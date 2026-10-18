export interface User {
  id: string;
  username: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface Asset {
  id: string;
  ticker: string;
  name: string;
  initialPrice: number;
  currentPrice: number;
}

export interface BookLevel {
  price: number;
  quantity: number;
  orderCount: number;
}

export interface OrderBook {
  assetId: string;
  ticker: string;
  buys: BookLevel[];
  sells: BookLevel[];
  generatedAt: string;
}

export type OrderSide = 'BUY' | 'SELL';
export type OrderStatus = 'OPEN' | 'CANCELLED';

export interface Order {
  id: string;
  assetId: string;
  ticker: string;
  side: OrderSide;
  price: number;
  quantity: number;
  status: OrderStatus;
  createdAt: string;
  cancelledAt: string | null;
}

export interface CreateOrderRequest {
  assetId: string;
  side: OrderSide;
  price: number;
  quantity: number;
}

export interface Position {
  assetId: string;
  quantity: number;
  reservedQuantity: number;
  averagePrice: number;
}

export interface Portfolio {
  userId: string;
  balance: number;
  reservedBalance: number;
  availableBalance: number;
  positions: Position[];
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  message?: string;
  path?: string;
  fields?: Record<string, string>;
}
