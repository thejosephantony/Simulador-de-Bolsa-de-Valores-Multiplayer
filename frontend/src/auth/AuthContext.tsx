import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/endpoints';
import { setAccessToken, setUnauthorizedHandler } from '../api/http';
import type { AuthResponse, User } from '../api/types';

// sessionStorage (e não localStorage) permite abrir duas abas com usuários
// diferentes no mesmo navegador — útil para demonstrar múltiplos clientes.
const STORAGE_KEY = 'home-broker.session';

interface Session {
  token: string;
  user: User;
  expiresAt: number;
}

interface AuthContextValue {
  user: User | null;
  login: (identifier: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}

function toSession(response: AuthResponse): Session {
  return {
    token: response.accessToken,
    user: response.user,
    expiresAt: Date.now() + response.expiresInSeconds * 1000,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const stored = loadSession();
    setAccessToken(stored?.token ?? null);
    return stored;
  });

  const applySession = useCallback((next: Session | null) => {
    setAccessToken(next?.token ?? null);
    try {
      if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      else sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // armazenamento indisponível: a sessão continua só em memória
    }
    setSession(next);
  }, []);

  const logout = useCallback(() => applySession(null), [applySession]);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Encerra a sessão automaticamente quando o JWT expira.
  useEffect(() => {
    if (!session) return;
    const timeout = window.setTimeout(logout, session.expiresAt - Date.now());
    return () => window.clearTimeout(timeout);
  }, [session, logout]);

  const login = useCallback(
    async (identifier: string, password: string) => {
      applySession(toSession(await authApi.login(identifier, password)));
    },
    [applySession],
  );

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      applySession(toSession(await authApi.register(username, email, password)));
    },
    [applySession],
  );

  const value = useMemo(
    () => ({ user: session?.user ?? null, login, register, logout }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return context;
}
