import { useCallback, useEffect, useRef, useState } from 'react';

export const POLLING_INTERVAL_MS = Number(import.meta.env.VITE_POLLING_INTERVAL_MS ?? 2000);

interface PollingState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  lastUpdated: Date | null;
  refresh: () => Promise<void>;
}

/**
 * Busca dados periodicamente. É a "atualização básica" do Home Broker na Parte 1;
 * na etapa seguinte será substituída por WebSocket/STOMP (realtime-service).
 */
export function usePolling<T>(
  fetcher: (() => Promise<T>) | null,
  intervalMs: number = POLLING_INTERVAL_MS,
): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(fetcher !== null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const refresh = useCallback(async () => {
    const current = fetcherRef.current;
    if (!current) return;
    try {
      const result = await current();
      // Ignora respostas de um fetcher antigo (ex.: troca de ativo selecionado).
      if (fetcherRef.current !== current) return;
      setData(result);
      setError(null);
      setLastUpdated(new Date());
    } catch (err) {
      if (fetcherRef.current !== current) return;
      setError(err instanceof Error ? err.message : 'Erro desconhecido');
    } finally {
      if (fetcherRef.current === current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    setData(null);
    setError(null);
    if (!fetcher) {
      setLoading(false);
      return;
    }
    setLoading(true);
    void refresh();
    const id = window.setInterval(() => void refresh(), intervalMs);
    return () => window.clearInterval(id);
  }, [fetcher, intervalMs, refresh]);

  return { data, error, loading, lastUpdated, refresh };
}
