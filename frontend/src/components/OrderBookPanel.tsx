import type { BookLevel, OrderBook } from '../api/types';
import { formatMoney, formatQuantity } from '../utils/format';

interface Props {
  book: OrderBook | null;
  loading: boolean;
  onPickPrice: (price: number) => void;
}

const DEPTH = 8;

function Side({
  title,
  levels,
  className,
  onPickPrice,
}: {
  title: string;
  levels: BookLevel[];
  className: string;
  onPickPrice: (price: number) => void;
}) {
  const visible = levels.slice(0, DEPTH);
  const maxQty = Math.max(1, ...visible.map((l) => l.quantity));

  return (
    <div className={`book-side ${className}`}>
      <h3>{title}</h3>
      <table className="table compact">
        <thead>
          <tr>
            <th>Preço</th>
            <th className="right">Qtd.</th>
            <th className="right">Ordens</th>
          </tr>
        </thead>
        <tbody>
          {visible.length === 0 ? (
            <tr>
              <td colSpan={3} className="muted empty">Sem ofertas</td>
            </tr>
          ) : (
            visible.map((level) => (
              <tr
                key={level.price}
                className="book-row"
                onClick={() => onPickPrice(level.price)}
                title="Usar este preço na ordem"
                style={{ ['--depth' as string]: `${(level.quantity / maxQty) * 100}%` }}
              >
                <td className="num price">{formatMoney(level.price)}</td>
                <td className="num">{formatQuantity(level.quantity)}</td>
                <td className="num muted">{level.orderCount}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export function OrderBookPanel({ book, loading, onPickPrice }: Props) {
  if (!book) {
    return <p className="muted empty">{loading ? 'Carregando livro…' : 'Selecione um ativo.'}</p>;
  }

  return (
    <div className="book">
      <Side title="Compra" levels={book.buys} className="buy" onPickPrice={onPickPrice} />
      <Side title="Venda" levels={book.sells} className="sell" onPickPrice={onPickPrice} />
    </div>
  );
}
