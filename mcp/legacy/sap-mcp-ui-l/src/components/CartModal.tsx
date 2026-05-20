import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { cartUtils, api, auth } from '../services/api';
import type { CartItem, AppliedVoucher, AppliedPromotion } from '../types';

interface CartModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CartModal = ({ isOpen, onClose }: CartModalProps) => {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [appliedVouchers, setAppliedVouchers] = useState<AppliedVoucher[]>([]);
  const [appliedOrderPromotions, setAppliedOrderPromotions] = useState<AppliedPromotion[]>([]);
  const [appliedProductPromotions, setAppliedProductPromotions] = useState<AppliedPromotion[]>([]);
  const [potentialPromotions, setPotentialPromotions] = useState<AppliedPromotion[]>([]);
  const [totalDiscounts, setTotalDiscounts] = useState(0);
  const [serverTotal, setServerTotal] = useState<number | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (isOpen) {
      loadCart();
    }
  }, [isOpen]);

  useEffect(() => {
    const handleCartUpdate = () => {
      if (isOpen) {
        loadCart();
      }
    };

    window.addEventListener('cartUpdated', handleCartUpdate);
    return () => window.removeEventListener('cartUpdated', handleCartUpdate);
  }, [isOpen]);

  const loadCart = async () => {
    setLoading(true);
    try {
      const cartData = await cartUtils.getCart();
      setCart(cartData || []);

      if (auth.isLoggedIn() && cartData && cartData.length > 0) {
        try {
          const promoData = await api.getCartPromotions();
          setAppliedVouchers(promoData.appliedVouchers);
          setAppliedOrderPromotions(promoData.appliedOrderPromotions);
          setAppliedProductPromotions(promoData.appliedProductPromotions);
          setPotentialPromotions([
            ...promoData.potentialOrderPromotions,
            ...promoData.potentialProductPromotions,
          ]);
          setTotalDiscounts(promoData.totalDiscounts);
          setServerTotal(promoData.totalPrice);
        } catch {
          // Non-critical
        }
      } else {
        setAppliedVouchers([]);
        setAppliedOrderPromotions([]);
        setAppliedProductPromotions([]);
        setPotentialPromotions([]);
        setTotalDiscounts(0);
        setServerTotal(null);
      }
    } catch (error) {
      console.error('Failed to load cart:', error);
      setCart([]);
    } finally {
      setLoading(false);
    }
  };

  const updateQuantity = async (productId: string, newQuantity: number) => {
    if (newQuantity < 1) return;

    try {
      await cartUtils.updateCartItem(productId, newQuantity);
      await loadCart();
      window.dispatchEvent(new Event('cartUpdated'));
    } catch (error) {
      alert('Failed to update quantity: ' + (error as Error).message);
    }
  };

  const removeItem = async (productId: string) => {
    try {
      await cartUtils.removeFromCart(productId);
      await loadCart();
      window.dispatchEvent(new Event('cartUpdated'));
    } catch (error) {
      alert('Failed to remove item: ' + (error as Error).message);
    }
  };

  const clearCart = async () => {
    if (!window.confirm('Are you sure you want to clear your cart?')) return;

    try {
      await cartUtils.clearCart();
      await loadCart();
      window.dispatchEvent(new Event('cartUpdated'));
    } catch (error) {
      alert('Failed to clear cart: ' + (error as Error).message);
    }
  };

  const handleCheckout = () => {
    onClose();
    navigate('/checkout');
  };

  const total = cart.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  );
  const itemCount = cart.reduce((sum, item) => sum + item.quantity, 0);

  if (!isOpen) return null;

  return (
    <>
      {/* Invisible Overlay - Click to close but fully transparent */}
      <div
        className="fixed inset-0 z-40 transition-opacity duration-300"
        onClick={onClose}
      />

      {/* Slide-out Drawer */}
      <div
        className="fixed inset-y-0 right-0 w-full sm:w-[440px] bg-white dark:bg-gray-800 shadow-2xl z-50 flex flex-col transform transition-all duration-300 ease-in-out"
        style={{ boxShadow: '-4px 0 20px rgba(0, 0, 0, 0.15)' }}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
            🛒 Cart {itemCount > 0 && `(${itemCount})`}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 text-3xl font-bold w-10 h-10 flex items-center justify-center rounded-lg hover:bg-white/50 dark:hover:bg-gray-700/50 transition-colors"
            aria-label="Close cart"
          >
            ×
          </button>
        </div>

        {/* Cart Items */}
        <div className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            </div>
          ) : cart.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-gray-500 dark:text-gray-400 text-lg mb-4">
                Your cart is empty
              </p>
              <button
                onClick={onClose}
                className="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-semibold"
              >
                Continue Shopping
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {cart.map((item) => {
                const originalTotal = item.price * item.quantity;
                const lineDiscount = item.discountValue ?? 0;
                const discountedTotal = Math.max(0, originalTotal - lineDiscount);
                const isDiscounted = lineDiscount > 0;
                const linePromos = item.entryNumber != null
                  ? appliedProductPromotions.filter(
                      (p) => p.consumedEntries?.includes(item.entryNumber as number),
                    )
                  : [];
                return (
                  <div
                    key={item.productId}
                    className="bg-white dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600 p-4 hover:shadow-md transition-shadow"
                  >
                    <div className="flex justify-between items-start mb-3">
                      <div className="flex-1">
                        <h3 className="font-semibold text-gray-900 dark:text-white mb-1">
                          {item.productName}
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                          ${item.price.toFixed(2)} each
                        </p>
                      </div>
                      <button
                        onClick={() => removeItem(item.productId)}
                        className="text-gray-400 dark:text-gray-500 hover:text-red-600 dark:hover:text-red-400 transition-colors ml-2"
                        title="Remove item"
                      >
                        ✕
                      </button>
                    </div>

                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() =>
                            updateQuantity(item.productId, item.quantity - 1)
                          }
                          className="w-8 h-8 flex items-center justify-center bg-gray-100 dark:bg-gray-600 hover:bg-gray-200 dark:hover:bg-gray-500 rounded-md transition-colors font-semibold text-gray-700 dark:text-gray-200"
                        >
                          −
                        </button>
                        <span className="w-8 text-center font-semibold text-gray-900 dark:text-white">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() =>
                            updateQuantity(item.productId, item.quantity + 1)
                          }
                          className="w-8 h-8 flex items-center justify-center bg-gray-100 dark:bg-gray-600 hover:bg-gray-200 dark:hover:bg-gray-500 rounded-md transition-colors font-semibold text-gray-700 dark:text-gray-200"
                        >
                          +
                        </button>
                      </div>

                      <div className="text-right">
                        {isDiscounted && (
                          <p className="text-xs text-gray-500 dark:text-gray-400 line-through leading-none">
                            ${originalTotal.toFixed(2)}
                          </p>
                        )}
                        <p
                          className={`font-bold text-lg ${
                            isDiscounted
                              ? 'text-green-600 dark:text-green-400'
                              : 'text-gray-900 dark:text-white'
                          }`}
                        >
                          ${discountedTotal.toFixed(2)}
                        </p>
                      </div>
                    </div>

                    {isDiscounted && linePromos.length > 0 && (
                      <div className="mt-2 text-xs text-green-700 dark:text-green-300">
                        {linePromos.map((p) => p.description).join(' · ')}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        {cart.length > 0 && (
          <div className="border-t border-gray-200 dark:border-gray-700 p-6 space-y-4 bg-gray-50 dark:bg-gray-900">
            {/* Applied Order-Level Promotions (product-level promos appear inline on each line) */}
            {appliedOrderPromotions.length > 0 && (
              <div className="space-y-1">
                {appliedOrderPromotions.map((promo, idx) => (
                  <div
                    key={idx}
                    className="flex items-start gap-2 text-xs text-green-700 dark:text-green-300 bg-green-50 dark:bg-green-900/40 rounded px-2 py-1.5"
                  >
                    <svg className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    <span>{promo.description}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Potential Promotions */}
            {potentialPromotions.length > 0 && (
              <div className="space-y-1">
                {potentialPromotions.map((promo, idx) => (
                  <div
                    key={idx}
                    className="flex items-start gap-2 text-xs text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/40 rounded px-2 py-1.5"
                  >
                    <svg className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                    </svg>
                    <span>{promo.description}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Applied Coupons */}
            {appliedVouchers.length > 0 && (
              <div className="space-y-1">
                {appliedVouchers.map((v) => (
                  <div
                    key={v.code}
                    className="flex items-center justify-between text-xs bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded px-2 py-1.5"
                  >
                    <span className="font-mono font-semibold text-green-800 dark:text-green-300">
                      {v.code}
                      {v.name && <span className="ml-1 font-normal text-green-600 dark:text-green-400">— {v.name}</span>}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <div className="flex justify-between items-center">
              <span className="text-lg text-gray-600 dark:text-gray-400">
                Subtotal
              </span>
              <span className="text-2xl font-bold text-gray-900 dark:text-white">
                ${total.toFixed(2)}
              </span>
            </div>

            {totalDiscounts > 0 && (
              <div className="flex justify-between items-center">
                <span className="text-sm text-green-600 dark:text-green-400">Discounts</span>
                <span className="text-sm font-semibold text-green-600 dark:text-green-400">
                  -${totalDiscounts.toFixed(2)}
                </span>
              </div>
            )}

            <button
              onClick={handleCheckout}
              className="w-full px-6 py-4 bg-blue-600 dark:bg-blue-500 text-white rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-all duration-200 font-bold text-lg shadow-lg"
            >
              Checkout → ${(serverTotal !== null ? serverTotal : total).toFixed(2)}
            </button>

            <button
              onClick={onClose}
              className="w-full px-6 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 transition-colors font-medium"
            >
              Continue Shopping
            </button>

            <button
              onClick={clearCart}
              className="w-full px-6 py-2 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 transition-colors text-sm"
            >
              Clear Cart
            </button>
          </div>
        )}
      </div>
    </>
  );
};
