import type { Asset, Portfolio } from '../api/types';
import { formatMoney, formatQuantity } from '../utils/format';

interface Props {
  portfolio: Portfolio | null;
  assets: Asset[];
}

export function PortfolioPanel({ portfolio, assets }: Props) {
  if (!portfolio) {
    return <p className="muted empty">Carregando carteira…</p>;
  }

  const tickers = new Map(assets.map((a) => [a.id, a.ticker]));

  return (
    <div className="portfolio">
      <dl className="balances">
        <div>
          <dt>Saldo disponível</dt>
          <dd className="num big">{formatMoney(portfolio.availableBalance)}</dd>
        </div>
        <div>
          <dt>Saldo total</dt>
          <dd className="num">{formatMoney(portfolio.balance)}</dd>
        </div>
        <div>
          <dt>Reservado</dt>
          <dd className="num">{formatMoney(portfolio.reservedBalance)}</dd>
        </div>
      </dl>

      <h3>Posições</h3>
      {portfolio.positions.length === 0 ? (
        <p className="muted empty">Nenhum ativo em carteira.</p>
      ) : (
        <table className="table compact">
          <thead>
            <tr>
              <th>Ativo</th>
              <th className="right">Qtd.</th>
              <th className="right">Preço médio</th>
            </tr>
          </thead>
          <tbody>
            {portfolio.positions.map((position) => (
              <tr key={position.assetId}>
                <td><strong>{tickers.get(position.assetId) ?? '—'}</strong></td>
                <td className="num right">{formatQuantity(position.quantity)}</td>
                <td className="num right">{formatMoney(position.averagePrice)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
