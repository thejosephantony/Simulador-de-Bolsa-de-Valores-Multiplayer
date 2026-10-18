import { useEffect, useState, type FormEvent } from 'react';
import { marketApi } from '../api/endpoints';
import { ApiError } from '../api/http';
import type { Asset, Order, OrderSide } from '../api/types';
import { formatMoney } from '../utils/format';

interface Props {
  asset: Asset | null;
  pickedPrice: { value: number; nonce: number } | null;
  onCreated: (order: Order) => void;
}

export function OrderForm({ asset, pickedPrice, onCreated }: Props) {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('100');
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  // Ao trocar de ativo, sugere o preço atual dele.
  useEffect(() => {
    if (asset) setPrice(asset.currentPrice.toFixed(2));
    setFeedback(null);
  }, [asset?.id]);

  // Clique em um nível do livro preenche o preço.
  useEffect(() => {
    if (pickedPrice) setPrice(pickedPrice.value.toFixed(2));
  }, [pickedPrice]);

  const priceNumber = Number(price.replace(',', '.'));
  const quantityNumber = Number(quantity);
  const valid = asset !== null && priceNumber > 0 && Number.isInteger(quantityNumber) && quantityNumber > 0;
  const total = valid ? priceNumber * quantityNumber : 0;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!asset || !valid) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const order = await marketApi.createOrder({
        assetId: asset.id,
        side,
        price: priceNumber,
        quantity: quantityNumber,
      });
      setFeedback({
        kind: 'success',
        text: `Ordem de ${side === 'BUY' ? 'compra' : 'venda'} enviada: ${order.quantity} ${order.ticker} a ${formatMoney(order.price)}.`,
      });
      onCreated(order);
    } catch (err) {
      setFeedback({ kind: 'error', text: err instanceof ApiError ? err.message : 'Falha ao enviar ordem.' });
    } finally {
      setSubmitting(false);
    }
  }

  if (!asset) {
    return <p className="muted empty">Selecione um ativo para negociar.</p>;
  }

  return (
    <form className="form order-form" onSubmit={handleSubmit}>
      <div className="side-toggle" role="radiogroup" aria-label="Tipo de ordem">
        <button
          type="button"
          role="radio"
          aria-checked={side === 'BUY'}
          className={side === 'BUY' ? 'side-btn buy active' : 'side-btn buy'}
          onClick={() => setSide('BUY')}
        >
          Comprar
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={side === 'SELL'}
          className={side === 'SELL' ? 'side-btn sell active' : 'side-btn sell'}
          onClick={() => setSide('SELL')}
        >
          Vender
        </button>
      </div>

      <div className="form-row">
        <label className="field">
          <span>Preço (R$)</span>
          <input
            inputMode="decimal"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            className="num"
            required
          />
        </label>
        <label className="field">
          <span>Quantidade</span>
          <input
            type="number"
            min={1}
            step={1}
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="num"
            required
          />
        </label>
      </div>

      <div className="order-total">
        <span className="muted">Total estimado</span>
        <strong className="num">{formatMoney(total)}</strong>
      </div>

      {feedback && <div className={`alert ${feedback.kind}`}>{feedback.text}</div>}

      <button type="submit" className={`btn ${side === 'BUY' ? 'buy' : 'sell'}`} disabled={!valid || submitting}>
        {submitting ? 'Enviando…' : `${side === 'BUY' ? 'Comprar' : 'Vender'} ${asset.ticker}`}
      </button>
    </form>
  );
}
