import { useState, useEffect } from 'react';
import { api } from '../services/api';
import { EntityModal } from './EntityModal';
import type { Order } from '../types';

interface Props {
  onClose: () => void;
  onOpenOrder: (orderId: string) => void;
}

export const OrderHistoryModal = ({ onClose, onOpenOrder }: Props) => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.getUserOrders()
      .then((data) => { if (!cancelled) setOrders(data); })
      .catch(() => { if (!cancelled) setError('Failed to load orders'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  return (
    <EntityModal title="Your orders" onClose={onClose}>
      {loading && (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
        </div>
      )}
      {error && <p className="text-red-600 dark:text-red-400 text-center py-8">{error}</p>}
      {!loading && !error && orders.length === 0 && (
        <p className="text-center text-gray-500 dark:text-gray-400 py-8">No orders yet.</p>
      )}
      {orders.length > 0 && (
        <div className="divide-y divide-gray-200 dark:divide-gray-700">
          {orders.map((order) => (
            <button
              key={order.id}
              type="button"
              onClick={() => onOpenOrder(order.id)}
              className="w-full flex items-center justify-between py-3 px-2 -mx-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-left cursor-pointer"
            >
              <div>
                <p className="font-semibold text-gray-900 dark:text-white">Order #{order.id}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {new Date(order.createdAt).toLocaleDateString()} · {order.status}
                </p>
              </div>
              <p className="font-bold text-green-600 dark:text-green-400">
                ${order.totalAmount.toFixed(2)}
              </p>
            </button>
          ))}
        </div>
      )}
    </EntityModal>
  );
};
