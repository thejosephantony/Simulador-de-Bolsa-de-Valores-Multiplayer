import type { Asset } from '../api/types';
import { formatMoney, formatVariation } from '../utils/format';

interface Props {
  assets: Asset[];
  selectedId: string | null;
  onSelect: (asset: Asset) => void;
}

export function AssetList({ assets, selectedId, onSelect }: Props) {
  if (assets.length === 0) {
    return <p className="muted empty">Nenhum ativo disponível.</p>;
  }

  return (
    <ul className="asset-list">
      {assets.map((asset) => {
        const delta = asset.currentPrice - asset.initialPrice;
        const trend = delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat';
        return (
          <li key={asset.id}>
            <button
              type="button"
              className={asset.id === selectedId ? 'asset-item selected' : 'asset-item'}
              onClick={() => onSelect(asset)}
            >
              <span className="asset-main">
                <strong>{asset.ticker}</strong>
                <small className="muted">{asset.name}</small>
              </span>
              <span className="asset-price">
                <span className="num">{formatMoney(asset.currentPrice)}</span>
                <small className={`num ${trend}`}>{formatVariation(asset.currentPrice, asset.initialPrice)}</small>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
