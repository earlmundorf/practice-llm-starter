import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import type { Cart } from '../types';
import { api } from '../services/api';
import { useAuth } from './AuthContext';

interface CartState {
  cart: Cart | null;
  itemCount: number;
  loading: boolean;
  refresh: () => Promise<void>;
}

const CartContext = createContext<CartState | undefined>(undefined);

export const useCart = (): CartState => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

export const CartProvider = ({ children }: { children: ReactNode }) => {
  const { isLoggedIn } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isLoggedIn) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      const freshCart = await api.getCart();
      setCart(freshCart);
    } catch {
      setCart(null);
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn]);

  // Refresh cart when auth changes or cartUpdated fires
  useEffect(() => {
    refresh();

    const handleCartUpdate = () => refresh();
    window.addEventListener('cartUpdated', handleCartUpdate);
    return () => window.removeEventListener('cartUpdated', handleCartUpdate);
  }, [refresh]);

  const itemCount = cart?.totalUnitCount ?? 0;

  return (
    <CartContext.Provider value={{ cart, itemCount, loading, refresh }}>
      {children}
    </CartContext.Provider>
  );
};
