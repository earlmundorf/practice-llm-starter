import { useState, useEffect } from 'react';
import { api } from '../services/api';
import { EntityModal } from './EntityModal';
import type { Order } from '../types';

interface Props {
  orderId: string;
  onClose: () => void;
}

export const OrderModal = ({ orderId, onClose }: Props) => {
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getOrder(orderId)
      .then((data) => { if (!cancelled) setOrder(data); })
      .catch(() => { if (!cancelled) setError('Failed to load order details'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [orderId]);

  return (
    <EntityModal title={`Order #${orderId}`} onClose={onClose}>
      {loading && (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
        </div>
      )}
      {error && <p className="text-red-600 dark:text-red-400 text-center py-8">{error}</p>}
      {order && (
        <div className="space-y-6">
          <div className="grid grid-cols-3 gap-4 text-sm">
            <div>
              <p className="text-gray-500 dark:text-gray-400">Status</p>
              <p className="font-semibold text-gray-900 dark:text-white mt-1">{order.status}</p>
            </div>
            <div>
              <p className="text-gray-500 dark:text-gray-400">Date</p>
              <p className="font-semibold text-gray-900 dark:text-white mt-1">
                {new Date(order.createdAt).toLocaleDateString()}
              </p>
            </div>
            <div>
              <p className="text-gray-500 dark:text-gray-400">Total</p>
              <p className="font-bold text-green-600 dark:text-green-400 text-lg mt-1">
                ${order.totalAmount.toFixed(2)}
              </p>
            </div>
          </div>

          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white mb-2">Items</h3>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-700 text-left">
                <tr>
                  <th className="px-3 py-2 font-semibold text-gray-700 dark:text-gray-300">Product</th>
                  <th className="px-3 py-2 font-semibold text-gray-700 dark:text-gray-300">Qty</th>
                  <th className="px-3 py-2 font-semibold text-gray-700 dark:text-gray-300">Price</th>
                  <th className="px-3 py-2 font-semibold text-gray-700 dark:text-gray-300">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {order.items.map((item, idx) => (
                  <tr key={idx}>
                    <td className="px-3 py-2 text-gray-900 dark:text-gray-200">{item.productName}</td>
                    <td className="px-3 py-2 text-gray-700 dark:text-gray-300">{item.quantity}</td>
                    <td className="px-3 py-2 text-gray-700 dark:text-gray-300">
                      {item.discountValue ? (
                        <span className="flex flex-col">
                          <span className="line-through text-gray-400 text-xs">${item.price.toFixed(2)}</span>
                          <span className="text-green-600 dark:text-green-400">
                            ${((item.price * item.quantity - item.discountValue) / item.quantity).toFixed(2)}
                          </span>
                        </span>
                      ) : (
                        <span>${item.price.toFixed(2)}</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-gray-900 dark:text-white font-medium">
                      {item.discountValue ? (
                        <span className="flex flex-col">
                          <span className="line-through text-gray-400 text-xs">${(item.price * item.quantity).toFixed(2)}</span>
                          <span className="text-green-600 dark:text-green-400">
                            ${(item.price * item.quantity - item.discountValue).toFixed(2)}
                          </span>
                        </span>
                      ) : (
                        <span>${(item.price * item.quantity).toFixed(2)}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="border-t border-gray-200 dark:border-gray-700 pt-4 space-y-1 text-sm">
            {order.subTotal != null && (
              <div className="flex justify-between text-gray-700 dark:text-gray-300">
                <span>Subtotal</span><span>${order.subTotal.toFixed(2)}</span>
              </div>
            )}
            {order.deliveryCost != null && order.deliveryCost > 0 && (
              <div className="flex justify-between text-gray-700 dark:text-gray-300">
                <span>Delivery</span><span>${order.deliveryCost.toFixed(2)}</span>
              </div>
            )}
            {order.totalTax != null && order.totalTax > 0 && (
              <div className="flex justify-between text-gray-700 dark:text-gray-300">
                <span>Tax</span><span>${order.totalTax.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between font-bold text-gray-900 dark:text-white pt-2 border-t border-gray-200 dark:border-gray-700">
              <span>Total</span><span className="text-green-600 dark:text-green-400">${order.totalAmount.toFixed(2)}</span>
            </div>
          </div>
        </div>
      )}
    </EntityModal>
  );
};
