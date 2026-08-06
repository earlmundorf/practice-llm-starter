import { useState, useEffect } from 'react';
import { useSearchParams, useParams, Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import type { Order, OrderItem, Product } from '../types';

export const OrderConfirmation = () => {
  const [searchParams] = useSearchParams();
  const { orderId: orderIdFromPath } = useParams<{ orderId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const orderId = orderIdFromPath ?? searchParams.get('orderId');
  const isNew = searchParams.get('new') === '1';
  const fromChat = sessionStorage.getItem('thinkshop_from_chat') === 'true';
  const [order, setOrder] = useState<Order | null>(location.state?.order || null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedItem, setExpandedItem] = useState<number | null>(null);
  const [productDetails, setProductDetails] = useState<Record<string, Product | null>>({});
  const [loadingProduct, setLoadingProduct] = useState<string | null>(null);

  const toggleItem = async (idx: number) => {
    if (expandedItem === idx) {
      setExpandedItem(null);
      return;
    }
    setExpandedItem(idx);

    const item = order?.items[idx];
    if (item && !(item.productId in productDetails)) {
      setLoadingProduct(item.productId);
      try {
        const product = await api.getProduct(item.productId);
        setProductDetails((prev) => ({ ...prev, [item.productId]: product }));
      } catch {
        setProductDetails((prev) => ({ ...prev, [item.productId]: null }));
      } finally {
        setLoadingProduct(null);
      }
    }
  };

  useEffect(() => {
    if (!orderId) {
      setLoading(false);
      return;
    }

    // If order was passed via navigation state, skip the API call
    if (location.state?.order) {
      setLoading(false);
      return;
    }

    const loadOrder = async () => {
      try {
        const data = await api.getOrder(orderId);
        setOrder(data);
      } catch {
        setError('Failed to load order details');
      } finally {
        setLoading(false);
      }
    };

    loadOrder();
  }, [orderId, location.state]);

  if (!orderId) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 dark:text-red-400 font-medium">No order found</p>
          <Link to="/" className="text-blue-600 dark:text-blue-400 hover:underline mt-4 inline-block">
            Return to Home
          </Link>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading order details...</p>
        </div>
      </div>
    );
  }

  const getStatusColor = (status: string) => {
    switch (status?.toLowerCase()) {
      case 'created': case 'pending':
        return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300';
      case 'completed':
        return 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300';
      case 'cancelled':
        return 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300';
      default:
        return 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-8 border border-gray-200 dark:border-gray-700">
          {isNew ? (
            <div className="text-center mb-8">
              <div className="text-6xl mb-4">✅</div>
              <h2 className="text-3xl font-bold text-green-600 dark:text-green-400 mb-2">
                Order #{orderId} Confirmed!
              </h2>
              <p className="text-gray-600 dark:text-gray-400">
                Thank you for your purchase
              </p>
            </div>
          ) : (
            <div className="mb-8">
              <Link
                to="/orders"
                className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
              >
                &larr; Back to Orders
              </Link>
              <h2 className="text-3xl font-bold text-gray-900 dark:text-white mt-3">
                Order #{orderId}
              </h2>
            </div>
          )}

          {error && (
            <p className="text-center text-red-600 dark:text-red-400 font-medium">
              {error}
            </p>
          )}

          {order && (
            <div>
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-6 mb-6 border border-gray-200 dark:border-gray-600">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-gray-700 dark:text-gray-300">
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400">Status</p>
                    <span className={`inline-block mt-1 px-3 py-1 rounded-full text-xs font-semibold ${getStatusColor(order.status)}`}>
                      {order.status}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400">Order Date</p>
                    <p className="font-semibold text-gray-900 dark:text-white mt-1">
                      {new Date(order.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400">Total</p>
                    <p className="font-bold text-green-600 dark:text-green-400 text-xl mt-1">
                      ${order.totalAmount.toFixed(2)}
                    </p>
                  </div>
                </div>
              </div>

              <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                Order Items:
              </h3>
              <div className="overflow-x-auto mb-6">
                <table className="w-full">
                  <thead className="bg-gray-50 dark:bg-gray-700">
                    <tr>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">
                        Product
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">
                        Quantity
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">
                        Price
                      </th>
                      <th className="px-4 py-3 text-left text-sm font-semibold text-gray-700 dark:text-gray-300">
                        Total
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                    {order.items.map((item: OrderItem, idx: number) => (
                      <>
                        <tr
                          key={idx}
                          onClick={() => toggleItem(idx)}
                          className="hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                        >
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-3">
                              <svg
                                className={`w-3 h-3 text-gray-400 transition-transform flex-shrink-0 ${expandedItem === idx ? 'rotate-90' : ''}`}
                                fill="currentColor"
                                viewBox="0 0 20 20"
                              >
                                <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                              </svg>
                              {item.imageUrl ? (
                                <img
                                  src={item.imageUrl}
                                  alt={item.productName}
                                  loading="lazy"
                                  className="w-12 h-12 object-cover rounded-md flex-shrink-0"
                                />
                              ) : (
                                <div className="w-12 h-12 bg-gray-100 dark:bg-gray-700 rounded-md flex-shrink-0" />
                              )}
                              <span className="text-blue-600 dark:text-blue-400 hover:underline font-medium">
                                {item.productName}
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-gray-700 dark:text-gray-300">
                            {item.quantity}
                          </td>
                          <td className="px-4 py-3 text-gray-700 dark:text-gray-300">
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
                          <td className="px-4 py-3 text-gray-700 dark:text-gray-300 font-semibold">
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
                        {expandedItem === idx && (
                          <tr key={`${idx}-detail`} className="bg-gray-50 dark:bg-gray-700/50">
                            <td colSpan={4} className="px-4 py-4">
                              {loadingProduct === item.productId ? (
                                <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600"></div>
                                  Loading product details...
                                </div>
                              ) : productDetails[item.productId] === null ? (
                                <div className="flex-1">
                                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">
                                    Product Code: {item.productId}
                                  </p>
                                  <p className="text-sm text-gray-400 dark:text-gray-500 italic">
                                    This product is no longer available.
                                  </p>
                                </div>
                              ) : (
                                <div className="flex gap-4">
                                  {productDetails[item.productId]?.imageUrl && (
                                    <img
                                      src={productDetails[item.productId]!.imageUrl}
                                      alt={item.productName}
                                      className="w-20 h-20 object-contain rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600"
                                    />
                                  )}
                                  <div className="flex-1">
                                    <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">
                                      Product Code: {item.productId}
                                    </p>
                                    {productDetails[item.productId]?.description ? (
                                      <p className="text-sm text-gray-700 dark:text-gray-300 line-clamp-3">
                                        {productDetails[item.productId]!.description}
                                      </p>
                                    ) : (
                                      <p className="text-sm text-gray-400 dark:text-gray-500 italic">
                                        No description available.
                                      </p>
                                    )}
                                    <Link
                                      to={`/products/${item.productId}`}
                                      className="inline-block mt-2 text-sm text-blue-600 dark:text-blue-400 hover:underline font-medium"
                                    >
                                      View product
                                    </Link>
                                  </div>
                                </div>
                              )}
                            </td>
                          </tr>
                        )}
                      </>
                    ))}
                  </tbody>
                  <tfoot className="border-t-2 border-gray-300 dark:border-gray-600">
                    {order.subTotal != null && (
                      <tr>
                        <td colSpan={3} className="px-4 py-2 text-right text-gray-600 dark:text-gray-400">
                          Subtotal:
                        </td>
                        <td className="px-4 py-2 text-gray-900 dark:text-white">
                          ${order.subTotal.toFixed(2)}
                        </td>
                      </tr>
                    )}
                    {order.deliveryCost != null && order.deliveryCost > 0 && (
                      <tr>
                        <td colSpan={3} className="px-4 py-2 text-right text-gray-600 dark:text-gray-400">
                          Delivery:
                        </td>
                        <td className="px-4 py-2 text-gray-900 dark:text-white">
                          ${order.deliveryCost.toFixed(2)}
                        </td>
                      </tr>
                    )}
                    {order.totalTax != null && order.totalTax > 0 && (
                      <tr>
                        <td colSpan={3} className="px-4 py-2 text-right text-gray-600 dark:text-gray-400">
                          Tax:
                        </td>
                        <td className="px-4 py-2 text-gray-900 dark:text-white">
                          ${order.totalTax.toFixed(2)}
                        </td>
                      </tr>
                    )}
                    <tr className="border-t border-gray-300 dark:border-gray-600">
                      <td colSpan={3} className="px-4 py-3 text-right font-bold text-gray-900 dark:text-white">
                        Total:
                      </td>
                      <td className="px-4 py-3 font-bold text-green-600 dark:text-green-400 text-lg">
                        ${order.totalAmount.toFixed(2)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-3 justify-center mt-8">
            {fromChat && (
              <button
                onClick={() => {
                  sessionStorage.removeItem('thinkshop_from_chat');
                  sessionStorage.setItem('thinkshop_checkout_result', JSON.stringify({
                    type: 'placed',
                    orderId,
                    items: order?.items?.map(item => ({
                      name: item.productName,
                      quantity: item.quantity,
                      price: item.price,
                    })),
                    subtotal: order?.subTotal,
                    delivery: order?.deliveryCost,
                    total: order?.totalAmount,
                  }));
                  navigate('/chat');
                }}
                className="bg-blue-600 dark:bg-blue-500 text-white px-6 py-3 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold text-center"
              >
                Back to Chat
              </button>
            )}
            <Link
              to="/"
              className={`${fromChat ? 'border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700' : 'bg-blue-600 dark:bg-blue-500 text-white hover:bg-blue-700 dark:hover:bg-blue-600'} px-6 py-3 rounded-lg transition-colors font-semibold text-center`}
            >
              Continue Shopping
            </Link>
            <Link
              to="/orders"
              state={order ? { recentOrder: order } : undefined}
              className="border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-semibold text-center"
            >
              View All Orders
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
