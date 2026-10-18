import { useState } from 'react';
import { marketApi } from '../api/endpoints';
import { ApiError } from '../api/http';
import type { Order } from '../api/types';
import { formatDateTime, formatMoney, formatQuantity } from '../utils/format';

interface Props {
  orders: Order[];
  onChanged: () => void;
}

const STATUS_LABEL: Record<Order['status'], string> = {
  OPEN: 'Aberta',
  CANCELLED: 'Cancelada',
};

export function MyOrders({ orders, onChanged }: Props) {
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function cancel(order: Order) {
    setCancelling(order.id);
    setError(null);
    try {
      await marketApi.cancelOrder(order.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao cancelar ordem.');
    } finally {
      setCancelling(null);
    }
  }

  if (orders.length === 0) {
    return <p className="muted empty">Você ainda não enviou ordens.</p>;
  }

  const sorted = [...orders].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <>
      {error && <div className="alert error">{error}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Data/Horário</th>
              <th>Ativo</th>
              <th>Tipo</th>
              <th className="right">Preço</th>
              <th className="right">Qtd.</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {sorted.map((order) => (
              <tr key={order.id}>
                <td className="num muted">{formatDateTime(order.createdAt)}</td>
                <td><strong>{order.ticker}</strong></td>
                <td className={order.side === 'BUY' ? 'up' : 'down'}>
                  {order.side === 'BUY' ? 'Compra' : 'Venda'}
                </td>
                <td className="num">{formatMoney(order.price)}</td>
                <td className="num">{formatQuantity(order.quantity)}</td>
                <td>
                  <span className={`badge ${order.status.toLowerCase()}`}>{STATUS_LABEL[order.status]}</span>
                </td>
                <td className="right">
                  {order.status === 'OPEN' && (
                    <button
                      type="button"
                      className="btn ghost small"
                      disabled={cancelling === order.id}
                      onClick={() => cancel(order)}
                    >
                      {cancelling === order.id ? '…' : 'Cancelar'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
