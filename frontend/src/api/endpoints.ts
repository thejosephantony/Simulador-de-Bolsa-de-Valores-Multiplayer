import { API_URLS, request } from './http';
import type {
  Asset,
  AuthResponse,
  CreateOrderRequest,
  Order,
  OrderBook,
  Portfolio,
  User,
} from './types';

export const authApi = {
  login: (identifier: string, password: string) =>
    request<AuthResponse>(API_URLS.auth, '/api/v1/auth/login', {
      method: 'POST',
      body: { identifier, password },
      auth: false,
    }),

  register: (username: string, email: string, password: string) =>
    request<AuthResponse>(API_URLS.auth, '/api/v1/auth/register', {
      method: 'POST',
      body: { username, email, password },
      auth: false,
    }),

  me: () => request<User>(API_URLS.auth, '/api/v1/users/me'),
};

export const marketApi = {
  listAssets: () => request<Asset[]>(API_URLS.market, '/api/v1/assets'),

  orderBook: (assetId: string) =>
    request<OrderBook>(API_URLS.market, `/api/v1/assets/${assetId}/order-book`),

  listMyOrders: () => request<Order[]>(API_URLS.market, '/api/v1/orders'),

  createOrder: (order: CreateOrderRequest) =>
    request<Order>(API_URLS.market, '/api/v1/orders', { method: 'POST', body: order }),

  cancelOrder: (orderId: string) =>
    request<Order>(API_URLS.market, `/api/v1/orders/${orderId}`, { method: 'DELETE' }),
};

export const portfolioApi = {
  me: () => request<Portfolio>(API_URLS.portfolio, '/api/v1/portfolio/me'),
};
