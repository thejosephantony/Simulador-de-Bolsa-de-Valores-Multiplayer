// Teste de carga do fluxo de negociação (R12/R13 do plano).
// Cada usuário virtual (VU) repete: consulta ativos, consulta livro, envia ordem, consulta carteira.
//   VUs pares  -> compradores: contas novas (só dinheiro), compram 1 ação a +1% do último preço.
//   VUs ímpares -> vendedores: conta semeada agent_market_maker, vendem 1 ação a -1% do último preço.
// Os preços se cruzam, então o Market gera trades reais. Vendedores compartilham uma conta de propósito:
// isso exercita a disputa por bloqueio da mesma conta no ledger.
// Os agentes são pausados no setup e retomados no teardown para não interferir na medição.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const AUTH = __ENV.AUTH_URL || 'http://localhost:8081';
const MARKET = __ENV.MARKET_URL || 'http://localhost:8082';
const PORTFOLIO = __ENV.PORTFOLIO_URL || 'http://localhost:8083';
const AGENTS = __ENV.AGENTS_URL || '';
const AGENT_TOKEN = __ENV.AGENT_ADMIN_TOKEN || 'change-this-agent-admin-token';
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || '45s';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

export const options = {
  setupTimeout: '180s',
  scenarios: {
    trading: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  thresholds: {
    // 422 (saldo/posição insuficiente) é resposta de negócio válida, não falha do sistema.
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /orders}': ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 422));

const ordersAccepted = new Counter('orders_accepted');
const ordersRejected = new Counter('orders_rejected_422');
const ordersWithTrade = new Counter('orders_with_trade');
const sharesTraded = new Counter('shares_traded');
const orderLatency = new Trend('order_latency', true);

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function login(identifier, password) {
  const res = http.post(`${AUTH}/api/v1/auth/login`, JSON.stringify({ identifier, password }),
    { headers: JSON_HEADERS, tags: { name: 'POST /auth/login' } });
  if (res.status !== 200) throw new Error(`login ${identifier} falhou: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

function register(username) {
  const res = http.post(`${AUTH}/api/v1/auth/register`, JSON.stringify({
    username, email: `${username}@example.com`, password: 'LoadTest@2026!',
  }), { headers: JSON_HEADERS, tags: { name: 'POST /auth/register' } });
  if (res.status !== 201 && res.status !== 200) throw new Error(`registro ${username}: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

function setAgents(action) {
  if (!AGENTS) return;
  for (const name of ['market-maker', 'momentum']) {
    http.post(`${AGENTS}/api/v1/agents/${name}/${action}`, null,
      { headers: { 'X-Admin-Token': AGENT_TOKEN }, tags: { name: 'agents' } });
  }
}

export function setup() {
  setAgents('pause');
  const buyers = [];
  const buyerCount = Math.ceil(VUS / 2);
  for (let i = 0; i < buyerCount; i++) {
    buyers.push(register(`k6_${RUN_ID}_${i}`));
  }
  const seller = login('agent_market_maker', 'Agent@2026!');
  // O agente market maker (agora pausado) pode ter deixado compras abertas nesta mesma conta. Elas
  // cruzariam com as vendas do teste e o Market recusaria por autonegociação (HTTP 409). Cancela antes de medir.
  const sellerAuth = { headers: { Authorization: `Bearer ${seller}` }, tags: { name: 'setup cancel' } };
  for (const o of http.get(`${MARKET}/api/v1/orders`, sellerAuth).json()) {
    if (o.status === 'OPEN' || o.status === 'PARTIALLY_FILLED') {
      http.del(`${MARKET}/api/v1/orders/${o.id}`, null, sellerAuth);
    }
  }
  const assets = http.get(`${MARKET}/api/v1/assets`).json().map((a) => a.id);
  return { buyers, seller, assets };
}

export default function (data) {
  const vu = exec.vu.idInTest; // 1..VUS
  const isBuyer = vu % 2 === 0 || VUS === 1;
  const token = isBuyer ? data.buyers[(vu >> 1) % data.buyers.length] : data.seller;
  const auth = { Authorization: `Bearer ${token}` };
  const assetId = data.assets[Math.floor(Math.random() * data.assets.length)];

  const assets = http.get(`${MARKET}/api/v1/assets`, { tags: { name: 'GET /assets' } });
  check(assets, { 'assets 200': (r) => r.status === 200 });
  const asset = assets.json().find((a) => a.id === assetId);

  const book = http.get(`${MARKET}/api/v1/assets/${assetId}/order-book`, { tags: { name: 'GET /order-book' } });
  check(book, { 'book 200': (r) => r.status === 200 });

  const factor = isBuyer ? 1.01 : 0.99;
  const price = Math.max(0.01, Math.round(asset.currentPrice * factor * 100) / 100);
  const res = http.post(`${MARKET}/api/v1/orders`, JSON.stringify({
    assetId, side: isBuyer ? 'BUY' : 'SELL', price, quantity: 1,
  }), {
    headers: Object.assign({ 'Idempotency-Key': `${RUN_ID}-${vu}-${exec.scenario.iterationInTest}` }, JSON_HEADERS, auth),
    tags: { name: 'POST /orders' },
  });
  orderLatency.add(res.timings.duration);
  if (res.status === 201) {
    ordersAccepted.add(1);
    const filled = res.json('filledQuantity') || 0;
    if (filled > 0) {
      ordersWithTrade.add(1);
      sharesTraded.add(filled);
    }
  } else if (res.status === 422) {
    ordersRejected.add(1);
  }
  check(res, { 'order 201/422': (r) => r.status === 201 || r.status === 422 });

  const pf = http.get(`${PORTFOLIO}/api/v1/portfolio/me`, { headers: auth, tags: { name: 'GET /portfolio' } });
  check(pf, { 'portfolio 200': (r) => r.status === 200 });

  sleep(0.5);
}

export function teardown() {
  setAgents('resume');
}
