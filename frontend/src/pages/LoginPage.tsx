import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/http';
import { useAuth } from '../auth/AuthContext';

type Mode = 'login' | 'register';

export function LoginPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [identifier, setIdentifier] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setFieldErrors({});
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(identifier.trim(), password);
      } else {
        await register(username.trim(), email.trim(), password);
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.status === 401 ? 'Usuário ou senha inválidos.' : err.message);
        setFieldErrors(err.fields);
      } else {
        setError('Erro inesperado. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <header className="auth-header">
          <span className="brand-mark">▲</span>
          <h1>Home Broker</h1>
          <p className="muted">Simulador de Bolsa de Valores Multiplayer</p>
        </header>

        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => switchMode('login')}
          >
            Entrar
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => switchMode('register')}
          >
            Criar conta
          </button>
        </div>

        <form onSubmit={handleSubmit} className="form">
          {mode === 'login' ? (
            <label className="field">
              <span>Usuário ou e-mail</span>
              <input
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                autoComplete="username"
                required
                autoFocus
              />
            </label>
          ) : (
            <>
              <label className="field">
                <span>Usuário</span>
                <input
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  minLength={3}
                  maxLength={50}
                  required
                  autoFocus
                />
                {fieldErrors.username && <small className="field-error">{fieldErrors.username}</small>}
              </label>
              <label className="field">
                <span>E-mail</span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                  required
                />
                {fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
              </label>
            </>
          )}

          <label className="field">
            <span>Senha</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={mode === 'register' ? 8 : undefined}
              required
            />
            {mode === 'register' && <small className="muted">Mínimo de 8 caracteres.</small>}
            {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
          </label>

          {error && <div className="alert error">{error}</div>}

          <button type="submit" className="btn primary" disabled={submitting}>
            {submitting ? 'Aguarde…' : mode === 'login' ? 'Entrar no mercado' : 'Criar conta e entrar'}
          </button>
        </form>
      </section>
    </main>
  );
}
