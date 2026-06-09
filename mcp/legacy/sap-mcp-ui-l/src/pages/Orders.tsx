import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { auth, api } from '../services/api';
import type { User, Order } from '../types';

export const Orders = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [orders, setOrders] = useState<Order[]>([]);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadUserAndOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key]);

  const loadUserAndOrders = async () => {
    setLoading(true);
    try {
      if (!auth.isLoggedIn()) {
        setError('Please log in first');
        setLoading(false);
        return;
      }

      const user = await api.getUser();
      setCurrentUser(user);

      const ordersData = await api.getUserOrders();
      setOrders(ordersData);
      setError(null);
    } catch (err) {
      setError((err as Error).message);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };


  const getStatusColor = (status: string | undefined) => {
    switch (status?.toLowerCase()) {
      case 'created':
      case 'pending':
        return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300 border-yellow-300 dark:border-yellow-700';
      case 'completed':
        return 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300 border-green-300 dark:border-green-700';
      case 'cancelled':
        return 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300 border-red-300 dark:border-red-700';
      default:
        return 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300 border-gray-300 dark:border-gray-600';
    }
  };

  const getStatusIcon = (status: string | undefined) => {
    switch (status?.toLowerCase()) {
      case 'created':
      case 'pending':
        return '⏳';
      case 'completed':
        return '✅';
      case 'cancelled':
        return '❌';
      default:
        return '📦';
    }
  };

  const viewOrderDetails = (orderId: string) => {
    navigate(`/order-confirmation?orderId=${orderId}`);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">
            Loading orders...
          </p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="max-w-md w-full mx-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-red-200 dark:border-red-700 p-8 text-center">
            <div className="text-5xl mb-4">❌</div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
              Error
            </h2>
            <p className="text-gray-600 dark:text-gray-400 mb-6">{error}</p>
            <button
              onClick={loadUserAndOrders}
              className="bg-blue-600 dark:bg-blue-500 text-white px-6 py-2 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold"
            >
              Try Again
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
              📋 Order History
            </h2>
            {currentUser && (
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Orders for{' '}
                <span className="font-semibold">{currentUser.fullName}</span>
              </p>
            )}
          </div>
          <button
            onClick={loadUserAndOrders}
            className="bg-blue-600 dark:bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold"
          >
            🔄 Refresh
          </button>
        </div>

        {/* Orders List */}
        {orders.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-12 text-center border border-gray-200 dark:border-gray-700">
            <div className="text-6xl mb-4">📦</div>
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              No Orders Yet
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              You haven't placed any orders. Start shopping!
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => (
              <div
                key={order.id}
                onClick={() => viewOrderDetails(order.id)}
                className="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 p-6 cursor-pointer hover:shadow-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-all"
              >
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-4 flex-1 min-w-0">
                    <div className="text-3xl">
                      {getStatusIcon(order.status)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-3 mb-1">
                        <h3 className="text-xl font-bold text-gray-900 dark:text-white">
                          Order #{order.id}
                        </h3>
                        <span
                          className={`px-3 py-1 rounded-full text-xs font-semibold border ${getStatusColor(order.status)}`}
                        >
                          {order.status}
                        </span>
                      </div>
                      <div className="flex items-center gap-6 text-sm text-gray-600 dark:text-gray-400">
                        <span>
                          📅 {new Date(order.createdAt).toLocaleDateString()}
                        </span>
                        <span className="font-semibold text-gray-900 dark:text-white">
                          ${order.totalAmount?.toFixed(2)}
                        </span>
                      </div>
                    </div>
                  </div>
                  {order.items?.length > 0 && (
                    <div className="hidden sm:flex items-center gap-2">
                      {order.items.slice(0, 4).map((it, i) => (
                        it.imageUrl ? (
                          <img
                            key={i}
                            src={it.imageUrl}
                            alt={it.productName}
                            loading="lazy"
                            title={it.productName}
                            className="w-10 h-10 object-cover rounded-md"
                          />
                        ) : (
                          <div
                            key={i}
                            className="w-10 h-10 bg-gray-100 dark:bg-gray-700 rounded-md border border-gray-200 dark:border-gray-600"
                          />
                        )
                      ))}
                      {order.items.length > 4 && (
                        <span className="text-xs text-gray-500 dark:text-gray-400 font-medium">
                          +{order.items.length - 4}
                        </span>
                      )}
                    </div>
                  )}
                  <div className="text-gray-400 dark:text-gray-500 text-xl">
                    ▶
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
