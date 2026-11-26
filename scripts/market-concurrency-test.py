#!/usr/bin/env python3
"""Integration tests for the isolated Compose environment in CI only."""
import concurrent.futures as cf
import json
import os
import subprocess
import urllib.error
import urllib.request
import uuid
from decimal import Decimal as D
from threading import Barrier

AUTH = os.getenv('AUTH_URL', 'http://localhost:8081')
MARKET = os.getenv('MARKET_URL', 'http://localhost:8082')
PORTFOLIO = os.getenv('PORTFOLIO_URL', 'http://localhost:8083')


def request(base, path, method='GET', token=None, body=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as res:
            raw = res.read()
            return res.status, json.loads(raw, parse_float=D) if raw else None
    except urllib.error.HTTPError as ex:
        return ex.code, None


def ok(result, expected=200):
    status, data = result
    assert status == expected, (status, expected, data)
    return data


def sql(query):
    return subprocess.check_output([
        'docker', 'compose', 'exec', '-T', 'postgres', 'sh', '-c',
        'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"',
        'sh', query], text=True, timeout=60).strip()


def uid(value):
    return str(uuid.UUID(value))


def user():
    name = 'ct_' + uuid.uuid4().hex[:18]
    token = ok(request(AUTH, '/api/v1/auth/register', 'POST', body={
        'username': name, 'email': name + '@example.com', 'password': 'Concurrency123!'
    }), 201)['accessToken']
    account = portfolio(token)
    return token, uid(account['userId'])


def portfolio(token):
    return ok(request(PORTFOLIO, '/api/v1/portfolio/me', token=token))


def order(token, asset, side, price, qty, key=None):
    return request(MARKET, '/api/v1/orders', 'POST', token, {
        'assetId': asset, 'side': side, 'price': price, 'quantity': qty
    }, key)


def cancel(token, order_id):
    ok(request(MARKET, '/api/v1/orders/' + uid(order_id), 'DELETE', token))


def mine(token):
    return ok(request(MARKET, '/api/v1/orders', token=token))


def seed(user_id, asset, qty):
    sql(f"INSERT INTO portfolio.portfolio_positions (id,user_id,asset_id,quantity,reserved_quantity,average_price,version) VALUES (gen_random_uuid(),'{uid(user_id)}','{uid(asset)}',{int(qty)},0,20,0)")


def position(token, asset):
    return next(p for p in portfolio(token)['positions'] if p['assetId'] == asset)


def parallel(tasks):
    barrier = Barrier(len(tasks))
    def run(task):
        barrier.wait(timeout=15)
        return task()
    with cf.ThreadPoolExecutor(max_workers=len(tasks)) as pool:
        futures = [pool.submit(run, task) for task in tasks]
        return [f.result(timeout=60) for f in futures]


def test_double_spend(a, b):
    token, _ = user()
    starting = D(portfolio(token)['balance'])
    # CI's configured starting balance is 100000. Use two orders of 60% of it.
    assert starting == D('100000'), starting
    results = parallel([lambda: order(token, a, 'BUY', 1000, 60),
                        lambda: order(token, b, 'BUY', 1000, 60)])
    assert sorted(s for s, _ in results) == [201, 422], results
    assert len(mine(token)) == 1
    assert D(portfolio(token)['reservedBalance']) == D('60000')
    for status, item in results:
        if status == 201:
            cancel(token, item['id'])
    assert D(portfolio(token)['reservedBalance']) == 0
    print('PASS: concurrent BUY across assets cannot spend the same balance twice', flush=True)


def test_double_sell(a):
    token, user_id = user()
    seed(user_id, a, 10)
    results = parallel([lambda: order(token, a, 'SELL', 1000, 4) for _ in range(8)])
    assert sorted(s for s, _ in results) == [201, 201] + [422]*6, results
    assert len(mine(token)) == 2
    p = position(token, a)
    assert p['quantity'] == 10 and p['reservedQuantity'] == 8, p
    for status, item in results:
        if status == 201:
            cancel(token, item['id'])
    assert position(token, a)['reservedQuantity'] == 0
    print('PASS: concurrent SELL cannot reserve more shares than owned', flush=True)


def test_idempotency(a):
    token, user_id = user()
    key = 'ct-' + uuid.uuid4().hex
    results = parallel([lambda: order(token, a, 'BUY', 1, 7, key) for _ in range(8)])
    ids = {ok(r, 201)['id'] for r in results}
    assert len(ids) == 1 and len(mine(token)) == 1, results
    order_id = uid(ids.pop())
    assert D(portfolio(token)['reservedBalance']) == D('7')
    assert sql(f"SELECT count(*) FROM market.order_requests WHERE user_id='{user_id}' AND order_id='{order_id}'") == '1'
    assert sql(f"SELECT count(*) FROM market.outbox_events WHERE correlation_id='{order_id}' AND event_type='order.changed'") == '1'
    cancel(token, order_id)
    print('PASS: simultaneous requests with one key create one order and one initial event', flush=True)


def test_rollback(a):
    buyer, buyer_id = user()
    seller, seller_id = user()
    seed(seller_id, a, 10)
    sell = ok(order(seller, a, 'SELL', 20, 5), 201)
    assert sell['status'] == 'OPEN', sell
    sell_id = uid(sell['id'])
    price_before = D(sql(f"SELECT current_price FROM market.assets WHERE id='{a}'"))
    buyer_before = portfolio(buyer)
    seller_before = portfolio(seller)
    event_count_before = sql('SELECT count(*) FROM market.outbox_events')
    key = 'rollback-' + uuid.uuid4().hex
    # Fail during the taker's private order event, AFTER trade and ledger writes.
    # Trigger only exists in the disposable CI DB and only affects this test user.
    sql(f"""CREATE FUNCTION market.ct_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN IF NEW.user_id = '{buyer_id}'::uuid AND NEW.event_type = 'order.changed' THEN
        RAISE EXCEPTION 'intentional integration test rollback';
    END IF; RETURN NEW; END; $$;
    CREATE TRIGGER ct_fail_outbox BEFORE INSERT ON market.outbox_events
        FOR EACH ROW EXECUTE FUNCTION market.ct_fail_outbox();""")
    try:
        status, _ = order(buyer, a, 'BUY', 25, 5, key)
        assert status == 500, status
        assert mine(buyer) == []
        assert portfolio(buyer) == buyer_before
        assert portfolio(seller) == seller_before
        assert D(sql(f"SELECT current_price FROM market.assets WHERE id='{a}'")) == price_before
        assert sql(f"SELECT count(*) FROM market.trades WHERE sell_order_id='{sell_id}'") == '0'
        assert sql(f"SELECT count(*) FROM portfolio.trade_settlements WHERE sell_order_id='{sell_id}'") == '0'
        assert sql(f"SELECT count(*) FROM portfolio.order_reservations WHERE user_id='{buyer_id}'") == '0'
        assert sql(f"SELECT count(*) FROM market.order_requests WHERE user_id='{buyer_id}'") == '0'
        assert sql(f"SELECT count(*) FROM market.outbox_events WHERE user_id='{buyer_id}'") == '0'
        assert sql('SELECT count(*) FROM market.outbox_events') == event_count_before
        original = mine(seller)[0]
        assert original['status'] == 'OPEN' and original['remainingQuantity'] == 5, original
    finally:
        sql('DROP TRIGGER IF EXISTS ct_fail_outbox ON market.outbox_events; DROP FUNCTION IF EXISTS market.ct_fail_outbox();')
    # The same key must work after rollback, and execute exactly one trade.
    result = ok(order(buyer, a, 'BUY', 25, 5, key), 201)
    assert result['status'] == 'FILLED', result
    assert sql(f"SELECT count(*) FROM market.trades WHERE sell_order_id='{sell_id}'") == '1'
    assert sql(f"SELECT count(*) FROM portfolio.trade_settlements WHERE sell_order_id='{sell_id}'") == '1'
    assert D(portfolio(buyer)['balance']) == D(buyer_before['balance']) - 100
    assert D(portfolio(seller)['balance']) == D(seller_before['balance']) + 100
    assert position(buyer, a)['quantity'] == 5
    assert position(seller, a)['quantity'] == 5
    print('PASS: injected failure rolls back order, trade, ledger, key and outbox; retry succeeds', flush=True)


if __name__ == '__main__':
    if os.getenv('MARKET_ISOLATED_TEST_DB') != '1':
        raise SystemExit('Refusing to run: this script requires a disposable Compose test database.')
    assets = [uid(x['id']) for x in ok(request(MARKET, '/api/v1/assets'))]
    assert len(assets) >= 2
    # Assumes previous smoke tests cancelled their open orders and no other clients run.
    assert sql("SELECT count(*) FROM market.orders WHERE status IN ('OPEN','PARTIALLY_FILLED')") == '0'
    test_double_spend(*assets[:2])
    test_double_sell(assets[0])
    test_idempotency(assets[0])
    test_rollback(assets[0])
    print('Market concurrency and rollback tests passed.', flush=True)
