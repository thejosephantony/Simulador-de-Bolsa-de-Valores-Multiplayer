import { useCallback, useEffect, useMemo, useState } from 'react';
import { marketApi, portfolioApi } from '../api/endpoints';
import type { Asset } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AssetList } from '../components/AssetList';
import { MyOrders } from '../components/MyOrders';
import { OrderBookPanel } from '../components/OrderBookPanel';
import { OrderForm } from '../components/OrderForm';
import { PortfolioPanel } from '../components/PortfolioPanel';
import { POLLING_INTERVAL_MS, usePolling } from '../hooks/usePolling';
import { formatMoney, formatTime } from '../utils/format';

export function HomeBrokerPage() {
  const { user, logout } = useAuth();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [pickedPrice, setPickedPrice] = useState<{ value: number; nonce: number } | null>(null);

  const assets = usePolling(marketApi.listAssets);
  const portfolio = usePolling(portfolioApi.me);
  const orders = usePolling(marketApi.listMyOrders);

  const bookFetcher = useMemo(
    () => (selectedId ? () => marketApi.orderBook(selectedId) : null),
    [selectedId],
  );
  const book = usePolling(bookFetcher);

  const assetList = assets.data ?? [];
  const selected = assetList.find((a) => a.id === selectedId) ?? null;

  // Seleciona o primeiro ativo assim que a lista chega.
  useEffect(() => {
    if (!selectedId && assetList.length > 0) setSelectedId(assetList[0].id);
  }, [selectedId, assetList]);

  const handleSelect = useCallback((asset: Asset) => setSelectedId(asset.id), []);

  const refreshAfterOrder = useCallback(() => {
    void orders.refresh();
    void book.refresh();
    void portfolio.refresh();
  }, [orders.refresh, book.refresh, portfolio.refresh]);

  const connectionError = assets.error ?? orders.error ?? portfolio.error ?? book.error;
  const lastUpdated = book.lastUpdated ?? orders.lastUpdated;

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">▲</span>
          <span>Home Broker</span>
        </div>
        <div className={connectionError ? 'status offline' : 'status online'} title={connectionError ?? undefined}>
          <span className="dot" />
          {connectionError
            ? 'Sem conexão com o servidor'
            : `Atualizado ${lastUpdated ? formatTime(lastUpdated) : '…'} · a cada ${POLLING_INTERVAL_MS / 1000}s`}
        </div>
        <div className="user">
          {portfolio.data && <span className="num muted">{formatMoney(portfolio.data.availableBalance)}</span>}
          <strong>{user?.username}</strong>
          <button type="button" className="btn ghost small" onClick={logout}>
            Sair
          </button>
        </div>
      </header>

      <main className="layout">
        <section className="panel assets-panel">
          <h2>Ativos</h2>
          {assets.loading && !assets.data ? (
            <p className="muted empty">Carregando ativos…</p>
          ) : (
            <AssetList assets={assetList} selectedId={selectedId} onSelect={handleSelect} />
          )}
        </section>

        <section className="panel book-panel">
          <h2>
            Livro de ofertas {selected && <span className="muted">· {selected.ticker}</span>}
          </h2>
          <OrderBookPanel
            book={book.data}
            loading={book.loading}
            onPickPrice={(value) => setPickedPrice({ value, nonce: Date.now() })}
          />
        </section>

        <section className="panel order-panel">
          <h2>Nova ordem</h2>
          <OrderForm asset={selected} pickedPrice={pickedPrice} onCreated={refreshAfterOrder} />
        </section>

        <section className="panel portfolio-panel">
          <h2>Carteira</h2>
          <PortfolioPanel portfolio={portfolio.data} assets={assetList} />
        </section>

        <section className="panel orders-panel">
          <h2>Minhas ordens</h2>
          {orders.loading && !orders.data ? (
            <p className="muted empty">Carregando ordens…</p>
          ) : (
            <MyOrders orders={orders.data ?? []} onChanged={refreshAfterOrder} />
          )}
        </section>
      </main>
    </div>
  );
}
