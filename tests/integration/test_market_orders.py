"""HTTP + PostgreSQL checks against a running, disposable market-service environment."""

import json
import os
import subprocess
import time
import unittest
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from decimal import Decimal
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class MarketOrdersTest(unittest.TestCase):
    base_url = os.environ.get("MARKET_BASE_URL", "http://localhost:8082").rstrip("/")
    user_id = str(uuid.uuid4())
    inactive_asset_id = str(uuid.uuid4())
    asset_id = "11111111-1111-1111-1111-111111111111"

    @classmethod
    def sql(cls, statement, check=True):
        environment = dict(os.environ)
        for name, default in {
            "PGHOST": "localhost", "PGPORT": "5432", "PGDATABASE": "stock_market",
            "PGUSER": "stock_market", "PGPASSWORD": "stock_market",
        }.items():
            environment.setdefault(name, default)
        return subprocess.run(
            ["psql", "-X", "-v", "ON_ERROR_STOP=1", "-At", "-c", statement],
            env=environment, text=True, capture_output=True, check=check, timeout=10,
        )

    @classmethod
    def setUpClass(cls):
        deadline = time.monotonic() + 60
        while True:
            try:
                with urlopen(cls.base_url + "/actuator/health", timeout=2) as response:
                    if json.load(response)["status"] == "UP":
                        break
            except (URLError, TimeoutError):
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("market-service did not become healthy within 60 seconds")
            time.sleep(1)
        cls.sql(
            "INSERT INTO market.assets "
            "(id, ticker, name, initial_price, current_price, active) VALUES "
            f"('{cls.inactive_asset_id}', 'T{uuid.uuid4().hex[:11]}', "
            "'Integration test inactive asset', 1, 1, false)"
        )
        cls.addClassCleanup(cls.cleanup_fixtures)

    @classmethod
    def cleanup_fixtures(cls):
        cls.sql(f"DELETE FROM market.orders WHERE user_id = '{cls.user_id}'")
        cls.sql(f"DELETE FROM market.assets WHERE id = '{cls.inactive_asset_id}'")

    def order(self, **changes):
        return {
            "userId": self.user_id, "assetId": self.asset_id,
            "side": "BUY", "price": 25.125, "quantity": 100, **changes,
        }

    def post(self, payload):
        body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        request = Request(
            self.base_url + "/api/v1/orders", data=body,
            headers={"Content-Type": "application/json"}, method="POST",
        )
        try:
            response = urlopen(request, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            return (
                response.status,
                json.loads(response.read(), parse_float=Decimal),
                response.headers["Content-Type"],
            )

    def count_orders(self):
        return int(self.sql(
            f"SELECT count(*) FROM market.orders WHERE user_id = '{self.user_id}'"
        ).stdout)

    def assert_rejected(self, payload, expected_status):
        before = self.count_orders()
        status, body, content_type = self.post(payload)
        self.assertEqual(status, expected_status, body)
        self.assertIn("application/problem+json", content_type)
        self.assertEqual(body["status"], expected_status)
        self.assertTrue(body["title"])
        self.assertTrue(body["detail"])
        self.assertEqual(self.count_orders(), before)
        return body

    def persisted(self, order_id):
        # Validate the server ID before including it in a SQL literal.
        order_id = str(uuid.UUID(order_id))
        return json.loads(self.sql(
            f"SELECT row_to_json(o)::text FROM market.orders o WHERE id = '{order_id}'"
        ).stdout, parse_float=Decimal)

    def test_creates_buy_and_sell_with_persisted_server_fields(self):
        with urlopen(self.base_url + "/api/v1/assets", timeout=5) as response:
            catalog_before = json.load(response)
        sequences = []
        for side in ("BUY", "SELL"):
            status, body, content_type = self.post(self.order(side=side))
            self.assertEqual(status, 201, body)
            self.assertIn("application/json", content_type)
            self.assertEqual(set(body), {
                "id", "userId", "assetId", "side", "price", "quantity",
                "remainingQuantity", "status", "sequenceNumber", "createdAt",
            })
            self.assertEqual(body["userId"], self.user_id)
            self.assertEqual(body["assetId"], self.asset_id)
            self.assertEqual(body["side"], side)
            self.assertEqual(body["price"], Decimal("25.125"))
            self.assertEqual(body["quantity"], 100)
            self.assertEqual(body["remainingQuantity"], 100)
            self.assertEqual(body["status"], "PENDING")
            self.assertGreater(body["sequenceNumber"], 0)
            self.assertIsNotNone(datetime.fromisoformat(body["createdAt"]).tzinfo)
            saved = self.persisted(body["id"])
            for api_field, db_field in (
                ("userId", "user_id"), ("assetId", "asset_id"), ("side", "side"),
                ("price", "price"), ("quantity", "quantity"),
                ("remainingQuantity", "remaining_quantity"), ("status", "status"),
                ("sequenceNumber", "sequence_number"),
            ):
                self.assertEqual(body[api_field], saved[db_field])
            self.assertEqual(saved["version"], 0)
            sequences.append(body["sequenceNumber"])
        self.assertGreater(sequences[1], sequences[0])
        with urlopen(self.base_url + "/api/v1/assets", timeout=5) as response:
            self.assertEqual(json.load(response), catalog_before)

    def test_rejects_missing_and_null_fields(self):
        for field in self.order():
            for missing in (True, False):
                with self.subTest(field=field, missing=missing):
                    payload = self.order()
                    if missing:
                        del payload[field]
                    else:
                        payload[field] = None
                    error = self.assert_rejected(payload, 400)
                    self.assertIn(field, [item["field"] for item in error["errors"]])

    def test_rejects_invalid_parameters_and_malformed_json(self):
        cases = [
            {"price": 0}, {"price": -1}, {"price": 0.00001}, {"price": 25.12345},
            {"price": 1000000000000000}, {"quantity": 0}, {"quantity": -1},
            {"quantity": 1.5}, {"quantity": 1.0}, {"quantity": 9223372036854775808},
            {"side": "HOLD"}, {"side": "buy"}, {"side": 0}, {"side": "0"},
            {"userId": "not-a-uuid"}, {"assetId": "not-a-uuid"},
        ]
        for changes in cases:
            with self.subTest(changes=changes):
                self.assert_rejected(self.order(**changes), 400)
        for body in (b"{", b"", b"null", b"[]"):
            with self.subTest(body=body):
                self.assert_rejected(body, 400)

    def test_rejects_unknown_and_inactive_assets(self):
        for asset_id in (str(uuid.uuid4()), self.inactive_asset_id):
            with self.subTest(asset_id=asset_id):
                error = self.assert_rejected(self.order(assetId=asset_id), 422)
                self.assertEqual(error["errors"][0]["field"], "assetId")

    def test_rejects_client_supplied_server_fields(self):
        for field, value in {
            "id": str(uuid.uuid4()), "status": "FILLED", "sequenceNumber": 1,
            "remainingQuantity": 0, "createdAt": "2000-01-01T00:00:00Z", "version": 7,
        }.items():
            with self.subTest(field=field):
                self.assert_rejected(self.order(**{field: value}), 400)

    def test_accepts_supported_numeric_boundaries_without_rounding(self):
        for price in ("0.0001", "999999999999999.9999"):
            with self.subTest(price=price):
                payload = json.dumps(self.order(quantity=9223372036854775807))
                payload = payload.replace("25.125", price).encode()
                status, body, _ = self.post(payload)
                self.assertEqual(status, 201, body)
                self.assertEqual(body["price"], Decimal(price))
                self.assertEqual(body["quantity"], 9223372036854775807)
                self.assertEqual(self.persisted(body["id"])["price"], Decimal(price))

    def test_concurrent_orders_receive_unique_persisted_sequences(self):
        before = self.count_orders()
        with ThreadPoolExecutor(max_workers=8) as executor:
            responses = list(executor.map(
                lambda number: self.post(self.order(side="BUY" if number % 2 else "SELL")),
                range(16),
            ))
        for status, body, _ in responses:
            self.assertEqual(status, 201, body)
            self.assertEqual(body["status"], "PENDING")
            self.assertEqual(self.persisted(body["id"])["sequence_number"], body["sequenceNumber"])
        self.assertEqual(len({body["id"] for _, body, _ in responses}), 16)
        self.assertEqual(len({body["sequenceNumber"] for _, body, _ in responses}), 16)
        self.assertEqual(self.count_orders(), before + 16)

    def test_database_rejects_invalid_order_state(self):
        status, order, _ = self.post(self.order())
        self.assertEqual(status, 201, order)
        order_id = str(uuid.UUID(order["id"]))
        for assignment in (
            "quantity = 0", "price = 0", "remaining_quantity = -1",
            "remaining_quantity = quantity + 1", "side = 'HOLD'", "status = 'UNKNOWN'",
            f"asset_id = '{uuid.uuid4()}'",
        ):
            with self.subTest(assignment=assignment):
                result = self.sql(
                    f"UPDATE market.orders SET {assignment} WHERE id = '{order_id}'", check=False
                )
                self.assertNotEqual(result.returncode, 0)
        saved = self.persisted(order_id)
        self.assertEqual(saved["quantity"], 100)
        self.assertEqual(saved["remaining_quantity"], 100)
        self.assertEqual(saved["status"], "PENDING")


if __name__ == "__main__":
    unittest.main(verbosity=2)
