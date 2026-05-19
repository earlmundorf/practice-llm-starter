import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import type { User } from '../types';
import { auth } from '../services/api';

interface AuthState {
  user: User | null;
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export const useAuth = (): AuthState => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => auth.isLoggedIn());

  const loadUser = useCallback(async () => {
    if (auth.isLoggedIn()) {
      setLoading(true);
      try {
        const currentUser = await auth.getUser();
        setUser(currentUser);
      } catch {
        auth.logout();
        setUser(null);
      } finally {
        setLoading(false);
      }
    } else {
      setUser(null);
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUser();

    const handleAuthChange = () => loadUser();
    window.addEventListener('authChanged', handleAuthChange);
    window.addEventListener('authExpired', handleAuthChange);
    return () => {
      window.removeEventListener('authChanged', handleAuthChange);
      window.removeEventListener('authExpired', handleAuthChange);
    };
  }, [loadUser]);

  const login = async (email: string, password: string) => {
    const loggedInUser = await auth.login(email, password);
    setUser(loggedInUser);
  };

  const logout = () => {
    auth.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isLoggedIn: !!user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};
