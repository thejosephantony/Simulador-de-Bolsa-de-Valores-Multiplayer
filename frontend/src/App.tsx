import { useAuth } from './auth/AuthContext';
import { HomeBrokerPage } from './pages/HomeBrokerPage';
import { LoginPage } from './pages/LoginPage';

export function App() {
  const { user } = useAuth();
  return user ? <HomeBrokerPage /> : <LoginPage />;
}
