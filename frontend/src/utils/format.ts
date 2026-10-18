const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const integer = new Intl.NumberFormat('pt-BR');
const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' });

export const formatMoney = (value: number) => currency.format(value);
export const formatQuantity = (value: number) => integer.format(value);
export const formatTime = (value: Date | string) => time.format(new Date(value));
export const formatDateTime = (value: Date | string) => dateTime.format(new Date(value));

export function formatVariation(current: number, initial: number) {
  if (initial === 0) return '0,00%';
  const pct = ((current - initial) / initial) * 100;
  const sign = pct > 0 ? '+' : '';
  return `${sign}${pct.toFixed(2).replace('.', ',')}%`;
}
