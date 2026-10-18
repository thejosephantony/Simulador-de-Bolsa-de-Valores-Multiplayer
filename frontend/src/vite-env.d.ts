/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AUTH_API_URL?: string;
  readonly VITE_MARKET_API_URL?: string;
  readonly VITE_PORTFOLIO_API_URL?: string;
  readonly VITE_POLLING_INTERVAL_MS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
