import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth, api, cartUtils } from '../services/api';
import { AddressSelector } from '../components/AddressSelector';
import { AddressForm } from '../components/AddressForm';
import type { CartItem, User, Address, PaymentFormData, AppliedVoucher, AppliedPromotion } from '../types';

interface DeliveryMode {
  code: string;
  name: string;
  cost: number;
}

const DEFAULT_PAYMENT: PaymentFormData = {
  cardNumber: '4111111111111111',
  cardType: 'visa',
  expiryMonth: '12',
  expiryYear: '2028',
  accountHolderName: 'ThinkShop Customer',
};

interface CheckoutProps {
  embedded?: boolean;
  onBack?: () => void;
  onOrderPlaced?: (orderId: string) => void;
}

export const Checkout = ({ embedded, onBack, onOrderPlaced }: CheckoutProps = {}) => {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const navigate = useNavigate();
  const fromChat = !embedded && sessionStorage.getItem('thinkshop_from_chat') === 'true';

  // Address state
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddress, setSelectedAddress] = useState<Address | null>(null);
  const [showNewAddressForm, setShowNewAddressForm] = useState(false);
  const [addressLoading, setAddressLoading] = useState(false);

  // Delivery mode state
  const [deliveryModes, setDeliveryModes] = useState<DeliveryMode[]>([]);
  const [selectedMode, setSelectedMode] = useState<string | null>(null);
  const [modesLoading, setModesLoading] = useState(false);

  // Payment state
  const [payment, setPayment] = useState<PaymentFormData>(DEFAULT_PAYMENT);

  // Coupon / Promotion state
  const [couponCode, setCouponCode] = useState('');
  const [couponError, setCouponError] = useState('');
  const [couponLoading, setCouponLoading] = useState(false);
  const [appliedVouchers, setAppliedVouchers] = useState<AppliedVoucher[]>([]);
  const [appliedOrderPromotions, setAppliedOrderPromotions] = useState<AppliedPromotion[]>([]);
  const [appliedProductPromotions, setAppliedProductPromotions] = useState<AppliedPromotion[]>([]);
  const [potentialPromotions, setPotentialPromotions] = useState<AppliedPromotion[]>([]);
  const [totalDiscounts, setTotalDiscounts] = useState(0);
  const [serverTotal, setServerTotal] = useState<number | null>(null);
  const [serverDeliveryCost, setServerDeliveryCost] = useState<number | null>(null);

  const loadPromotionData = useCallback(async () => {
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
      setServerDeliveryCost(promoData.deliveryCost);
    } catch {
      // Non-critical — promotions display is optional
    }
  }, []);

  const loadCheckoutData = useCallback(async () => {
    try {
      if (auth.isLoggedIn()) {
        const [user, addrs, cartData] = await Promise.all([
          api.getUser(),
          api.getAddresses(),
          cartUtils.getCart(),
        ]);
        setCurrentUser(user);
        setAddresses(addrs);
        setCart(cartData || []);

        // Pre-fill payment holder name from user
        setPayment((prev) => ({
          ...prev,
          accountHolderName: user.fullName || prev.accountHolderName,
        }));

        // Auto-select default or first address
        if (addrs.length > 0) {
          const defaultAddr = addrs.find((a) => a.defaultAddress) || addrs[0];
          setSelectedAddress(defaultAddr);
        }

        // Load promotion/voucher data
        await loadPromotionData();
      } else {
        const cartData = await cartUtils.getCart();
        setCart(cartData || []);
      }
    } catch (error) {
      console.error('Failed to load checkout data:', error);
      setCart([]);
    } finally {
      setLoading(false);
    }
  }, [loadPromotionData]);

  useEffect(() => {
    loadCheckoutData();

    const handleCartUpdate = () => loadCheckoutData();
    window.addEventListener('cartUpdated', handleCartUpdate);
    return () => window.removeEventListener('cartUpdated', handleCartUpdate);
  }, [loadCheckoutData]);

  // Fetch delivery modes when address is selected
  useEffect(() => {
    if (!selectedAddress) {
      setDeliveryModes([]);
      setSelectedMode(null);
      return;
    }

    const fetchModes = async () => {
      setModesLoading(true);
      try {
        await api.setCartDeliveryAddress(selectedAddress);
        const modes = await api.getDeliveryModes();
        setDeliveryModes(modes);
        if (modes.length > 0) {
          setSelectedMode(modes[0].code);
          await api.setCartDeliveryMode(modes[0].code);
          await loadPromotionData();
        }
      } catch (error) {
        console.error('Failed to load delivery modes:', error);
      } finally {
        setModesLoading(false);
      }
    };

    fetchModes();
  }, [selectedAddress, loadPromotionData]);

  const handleSelectAddress = (addr: Address) => {
    setSelectedAddress(addr);
    setShowNewAddressForm(false);
  };

  const handleNewAddress = async (data: Omit<Address, 'id'> & { id?: string }) => {
    setAddressLoading(true);
    try {
      const newAddr = await api.createAddress(data);
      const addrs = await api.getAddresses();
      setAddresses(addrs);
      setSelectedAddress(newAddr);
      setShowNewAddressForm(false);
    } catch (error) {
      alert('Failed to save address: ' + (error as Error).message);
    } finally {
      setAddressLoading(false);
    }
  };

  const handleModeChange = async (code: string) => {
    setSelectedMode(code);
    try {
      await api.setCartDeliveryMode(code);
      await loadPromotionData();
    } catch (error) {
      console.error('Failed to set delivery mode:', error);
    }
  };

  const handleApplyCoupon = async () => {
    if (!couponCode.trim()) return;
    setCouponLoading(true);
    setCouponError('');
    try {
      await api.applyVoucher(couponCode.trim().toUpperCase());
      setCouponCode('');
      await loadPromotionData();
    } catch (error) {
      setCouponError((error as Error).message);
    } finally {
      setCouponLoading(false);
    }
  };

  const handleRemoveVoucher = async (code: string) => {
    try {
      await api.removeVoucher(code);
      await loadPromotionData();
    } catch (error) {
      console.error('Failed to remove voucher:', error);
    }
  };

  const subtotal = cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const deliveryCost = selectedMode ? (deliveryModes.find((m) => m.code === selectedMode)?.cost ?? 0) : 0;
  const total = serverTotal !== null ? serverTotal : subtotal + deliveryCost - totalDiscounts;

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    if (cart.length === 0) {
      alert('Your cart is empty');
      return;
    }
    if (!selectedAddress) {
      alert('Please select a delivery address');
      return;
    }
    if (!payment.cardNumber || !payment.accountHolderName) {
      alert('Please fill in payment details');
      return;
    }

    setPlacing(true);
    try {
      const order = await api.createOrder(selectedAddress, payment);
      if (embedded && onOrderPlaced) {
        // Let handleOrderPlaced manage cart cleanup and events
        onOrderPlaced(order.id);
      } else if (fromChat) {
        sessionStorage.removeItem('thinkshop_from_chat');
        sessionStorage.setItem('thinkshop_checkout_result', JSON.stringify({
          type: 'placed',
          orderId: order.id,
          items: cart.map(item => ({
            name: item.productName,
            quantity: item.quantity,
            price: item.price,
          })),
          subtotal: subtotal,
          delivery: deliveryCost,
          total: total,
        }));
        // Don't dispatch cartUpdated here — Chat will handle it after creating a fresh cart
        navigate('/chat');
      } else {
        window.dispatchEvent(new Event('cartUpdated'));
        navigate(`/order-confirmation?orderId=${order.id}&new=1`, { state: { order } });
      }
    } catch (error) {
      alert('Error: ' + (error as Error).message);
      setPlacing(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading checkout...</p>
        </div>
      </div>
    );
  }

  const inputClass =
    'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent';

  return (
    <div className={embedded ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-900'}>
      <div className={embedded ? '' : 'max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8'}>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            {embedded && onBack && (
              <button
                onClick={onBack}
                className="text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
                title="Back to chat"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>
            )}
            <h2 className={`font-bold text-gray-900 dark:text-white ${embedded ? 'text-xl' : 'text-3xl'}`}>Checkout</h2>
          </div>
          {currentUser && !embedded && (
            <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg px-4 py-2">
              <p className="text-sm text-blue-600 dark:text-blue-400">Shopping as:</p>
              <p className="font-semibold text-blue-900 dark:text-blue-300">{currentUser.fullName}</p>
              <p className="text-xs text-blue-600 dark:text-blue-400">{currentUser.email}</p>
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left Column: Address, Delivery, Payment */}
          <div className="space-y-6">
            {currentUser ? (
              <form onSubmit={handleSubmit} className="space-y-6">
                {/* Delivery Address */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700">
                  <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                    Delivery Address
                  </h3>
                  {showNewAddressForm ? (
                    <AddressForm
                      onSubmit={handleNewAddress}
                      onCancel={() => setShowNewAddressForm(false)}
                      loading={addressLoading}
                    />
                  ) : (
                    <AddressSelector
                      addresses={addresses}
                      selectedId={selectedAddress?.id || null}
                      onSelect={handleSelectAddress}
                      onAddNew={() => setShowNewAddressForm(true)}
                    />
                  )}
                </div>

                {/* Delivery Mode */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700">
                  <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                    Delivery Mode
                  </h3>
                  {!selectedAddress ? (
                    <p className="text-gray-500 dark:text-gray-400 text-sm">
                      Select an address to see delivery options.
                    </p>
                  ) : modesLoading ? (
                    <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400">
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600"></div>
                      Loading delivery options...
                    </div>
                  ) : deliveryModes.length === 0 ? (
                    <p className="text-gray-500 dark:text-gray-400 text-sm">
                      No delivery options available.
                    </p>
                  ) : (
                    <div className="space-y-2">
                      {deliveryModes.map((mode) => (
                        <label
                          key={mode.code}
                          className={`flex items-center justify-between p-3 rounded-lg border-2 cursor-pointer transition-all ${
                            selectedMode === mode.code
                              ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                              : 'border-gray-200 dark:border-gray-600 hover:border-gray-300'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <input
                              type="radio"
                              name="deliveryMode"
                              value={mode.code}
                              checked={selectedMode === mode.code}
                              onChange={() => handleModeChange(mode.code)}
                              className="h-4 w-4 text-blue-600"
                            />
                            <span className="font-medium text-gray-900 dark:text-white">
                              {mode.name}
                            </span>
                          </div>
                          <span className="font-semibold text-gray-900 dark:text-white">
                            {serverDeliveryCost !== null && serverDeliveryCost === 0 && mode.cost > 0 ? (
                              <>
                                <span className="line-through text-gray-400 dark:text-gray-500 mr-1">${mode.cost.toFixed(2)}</span>
                                <span className="text-green-600 dark:text-green-400">FREE</span>
                              </>
                            ) : (
                              `$${mode.cost.toFixed(2)}`
                            )}
                          </span>
                        </label>
                      ))}
                      {serverDeliveryCost !== null && serverDeliveryCost === 0 && deliveryModes.some(m => m.cost > 0) && (
                        <p className="text-xs text-green-600 dark:text-green-400 mt-1 px-1">
                          Free shipping applied by promotion
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Payment */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700">
                  <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                    Payment
                  </h3>
                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Cardholder Name
                      </label>
                      <input
                        type="text"
                        required
                        value={payment.accountHolderName}
                        onChange={(e) => setPayment({ ...payment, accountHolderName: e.target.value })}
                        className={inputClass}
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Card Number
                      </label>
                      <input
                        type="text"
                        required
                        value={payment.cardNumber}
                        onChange={(e) => setPayment({ ...payment, cardNumber: e.target.value })}
                        className={inputClass}
                        maxLength={16}
                      />
                    </div>
                    <div className="grid grid-cols-3 gap-4">
                      <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                          Expiry Month
                        </label>
                        <select
                          value={payment.expiryMonth}
                          onChange={(e) => setPayment({ ...payment, expiryMonth: e.target.value })}
                          className={inputClass}
                        >
                          {Array.from({ length: 12 }, (_, i) => {
                            const m = String(i + 1).padStart(2, '0');
                            return <option key={m} value={m}>{m}</option>;
                          })}
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                          Expiry Year
                        </label>
                        <select
                          value={payment.expiryYear}
                          onChange={(e) => setPayment({ ...payment, expiryYear: e.target.value })}
                          className={inputClass}
                        >
                          {Array.from({ length: 10 }, (_, i) => {
                            const y = String(2025 + i);
                            return <option key={y} value={y}>{y}</option>;
                          })}
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                          Card Type
                        </label>
                        <select
                          value={payment.cardType}
                          onChange={(e) => setPayment({ ...payment, cardType: e.target.value })}
                          className={inputClass}
                        >
                          <option value="visa">Visa</option>
                          <option value="master">Mastercard</option>
                          <option value="amex">Amex</option>
                        </select>
                      </div>
                    </div>
                    <p className="text-xs text-gray-400 dark:text-gray-500">
                      Mock payment provider — pre-filled for demo purposes.
                    </p>
                  </div>
                </div>

                {/* Coupon Code */}
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700">
                  <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                    Coupon Code
                  </h3>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="Enter coupon code"
                      value={couponCode}
                      onChange={(e) => { setCouponCode(e.target.value); setCouponError(''); }}
                      onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleApplyCoupon())}
                      className={inputClass}
                    />
                    <button
                      type="button"
                      onClick={handleApplyCoupon}
                      disabled={couponLoading || !couponCode.trim()}
                      className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      {couponLoading ? 'Applying...' : 'Apply'}
                    </button>
                  </div>
                  {couponError && (
                    <p className="mt-2 text-sm text-red-600 dark:text-red-400">{couponError}</p>
                  )}
                  {appliedVouchers.length > 0 && (
                    <div className="mt-3 space-y-2">
                      {appliedVouchers.map((v) => (
                        <div
                          key={v.code}
                          className="flex items-center justify-between bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-lg px-3 py-2"
                        >
                          <div>
                            <span className="font-mono font-semibold text-green-800 dark:text-green-300">
                              {v.code}
                            </span>
                            {v.name && (
                              <span className="ml-2 text-sm text-green-600 dark:text-green-400">
                                — {v.name}
                              </span>
                            )}
                          </div>
                          <button
                            type="button"
                            onClick={() => handleRemoveVoucher(v.code)}
                            className="text-red-500 hover:text-red-700 text-sm font-medium"
                          >
                            Remove
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Place Order Button */}
                <div className="flex flex-col gap-3">
                  <button
                    type="submit"
                    disabled={cart.length === 0 || placing || !selectedAddress}
                    className="w-full bg-blue-600 dark:bg-blue-500 text-white px-6 py-4 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold text-lg disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {placing ? 'Placing Order...' : `Place Order ($${total.toFixed(2)})`}
                  </button>
                  {embedded && onBack ? (
                    <button
                      type="button"
                      onClick={onBack}
                      className="w-full text-center border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-semibold"
                    >
                      Back to Chat
                    </button>
                  ) : fromChat ? (
                    <button
                      type="button"
                      onClick={() => {
                        sessionStorage.removeItem('thinkshop_from_chat');
                        sessionStorage.setItem('thinkshop_checkout_result', JSON.stringify({ type: 'cancelled' }));
                        navigate('/chat');
                      }}
                      className="w-full text-center border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-semibold"
                    >
                      Back to Chat
                    </button>
                  ) : (
                    <Link
                      to="/"
                      className="w-full text-center border-2 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-semibold"
                    >
                      Continue Shopping
                    </Link>
                  )}
                </div>
              </form>
            ) : (
              <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700 text-center py-8">
                <p className="text-gray-600 dark:text-gray-400 mb-4">
                  Please log in to continue
                </p>
                <p className="text-sm text-gray-500 dark:text-gray-500">
                  Click "Log In" in the header
                </p>
              </div>
            )}
          </div>

          {/* Right Column: Order Summary */}
          <div className="lg:sticky lg:top-8 lg:self-start">
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-200 dark:border-gray-700">
              <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-6">
                Order Summary
              </h3>
              {cart.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-gray-500 dark:text-gray-400 mb-4">Your cart is empty.</p>
                  <Link
                    to="/"
                    className="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-semibold"
                  >
                    Go shopping
                  </Link>
                </div>
              ) : (
                <>
                  <div className="space-y-3 mb-6">
                    {cart.map((item, idx) => {
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
                          key={idx}
                          className="p-4 bg-gray-50 dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600"
                        >
                          <div className="flex justify-between items-start mb-2 gap-3">
                            {item.product?.imageUrl ? (
                              <img
                                src={item.product.imageUrl}
                                alt={item.productName}
                                loading="lazy"
                                className="w-14 h-14 object-cover rounded-md flex-shrink-0"
                              />
                            ) : (
                              <div className="w-14 h-14 bg-gray-100 dark:bg-gray-600 rounded-md flex-shrink-0" />
                            )}
                            <div className="flex-1 min-w-0">
                              <h4 className="font-semibold text-gray-900 dark:text-white">
                                {item.productName}
                              </h4>
                              <p className="text-sm text-gray-600 dark:text-gray-400">
                                Product: {item.productId}
                              </p>
                            </div>
                            <div className="text-right">
                              {isDiscounted && (
                                <span className="block text-xs text-gray-500 dark:text-gray-400 line-through">
                                  ${originalTotal.toFixed(2)}
                                </span>
                              )}
                              <span
                                className={`font-bold ${
                                  isDiscounted
                                    ? 'text-green-600 dark:text-green-400'
                                    : 'text-gray-900 dark:text-white'
                                }`}
                              >
                                ${discountedTotal.toFixed(2)}
                              </span>
                            </div>
                          </div>
                          <div className="flex justify-between items-center text-sm">
                            <span className="text-gray-600 dark:text-gray-400">
                              ${item.price.toFixed(2)} x {item.quantity}
                            </span>
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
                  {/* Applied Order-Level Promotions (product-level promos appear inline on each line) */}
                  {appliedOrderPromotions.length > 0 && (
                    <div className="mb-4 space-y-1">
                      {appliedOrderPromotions.map((promo, idx) => (
                        <div
                          key={idx}
                          className="flex items-start gap-2 text-sm text-green-700 dark:text-green-300 bg-green-50 dark:bg-green-900/40 rounded-lg px-3 py-2"
                        >
                          <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                          </svg>
                          <span>{promo.description}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  {/* Potential Promotions */}
                  {potentialPromotions.length > 0 && (
                    <div className="mb-4 space-y-1">
                      {potentialPromotions.map((promo, idx) => (
                        <div
                          key={idx}
                          className="flex items-start gap-2 text-sm text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/40 rounded-lg px-3 py-2"
                        >
                          <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                          </svg>
                          <span>{promo.description}</span>
                        </div>
                      ))}
                    </div>
                  )}

                  <div className="pt-4 border-t-2 border-gray-900 dark:border-gray-600">
                    <div className="flex justify-between items-center mb-2">
                      <span className="text-sm text-gray-600 dark:text-gray-400">
                        Subtotal ({cart.reduce((sum, item) => sum + item.quantity, 0)} items):
                      </span>
                      <span className="text-sm font-semibold text-gray-900 dark:text-white">
                        ${subtotal.toFixed(2)}
                      </span>
                    </div>
                    {selectedMode && deliveryModes.length > 0 && (
                      <div className="flex justify-between items-center mb-2">
                        <span className="text-sm text-gray-600 dark:text-gray-400">Delivery:</span>
                        <span className="text-sm font-semibold text-gray-900 dark:text-white">
                          ${(serverDeliveryCost !== null ? serverDeliveryCost : (deliveryModes.find((m) => m.code === selectedMode)?.cost ?? 0)).toFixed(2)}
                        </span>
                      </div>
                    )}
                    {totalDiscounts > 0 && (
                      <div className="flex justify-between items-center mb-2">
                        <span className="text-sm text-green-600 dark:text-green-400">Discounts:</span>
                        <span className="text-sm font-semibold text-green-600 dark:text-green-400">
                          -${totalDiscounts.toFixed(2)}
                        </span>
                      </div>
                    )}
                    <div className="flex justify-between items-center pt-2 border-t border-gray-200 dark:border-gray-600">
                      <span className="text-2xl font-bold text-gray-900 dark:text-white">Total:</span>
                      <span className="text-2xl font-bold text-green-600 dark:text-green-400">
                        ${total.toFixed(2)}
                      </span>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
