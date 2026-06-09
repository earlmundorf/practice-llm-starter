import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { auth, api, cartUtils } from '../services/api';
import { Toast } from '../components/Toast';
import type { Product } from '../types';

interface ToastState {
  message: string;
  type: 'success' | 'error' | 'info';
}

export const ProductDetail = () => {
  const { productId } = useParams<{ productId: string }>();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    if (!productId) return;
    const load = async () => {
      try {
        const data = await api.getProduct(productId);
        setProduct(data);
      } catch {
        setError('Product not found');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [productId]);

  const handleAddToCart = async () => {
    if (!product) return;
    if (!auth.isLoggedIn()) {
      setToast({ message: 'Please log in first', type: 'error' });
      return;
    }
    if (quantity < 1 || quantity > product.stockQuantity) {
      setToast({ message: `Please enter a valid quantity (1-${product.stockQuantity})`, type: 'error' });
      return;
    }

    setAdding(true);
    try {
      const currentCart = await cartUtils.getCart();
      const existing = currentCart.find((item) => item.productId === product.id);
      if (existing && existing.quantity + quantity > product.stockQuantity) {
        setToast({ message: `Cannot add more. Maximum stock: ${product.stockQuantity}`, type: 'error' });
        setAdding(false);
        return;
      }

      await cartUtils.addToCart(product, quantity);
      setToast({ message: `${product.name} added to cart!`, type: 'success' });
      window.dispatchEvent(new CustomEvent('cartItemAdded'));
    } catch (err) {
      setToast({ message: (err as Error).message || 'Failed to add to cart', type: 'error' });
    } finally {
      setAdding(false);
    }
  };

  const handleQuantityChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setQuantity(parseInt(e.target.value) || 1);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading product...</p>
        </div>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="max-w-md w-full mx-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-8 text-center">
            <div className="text-5xl mb-4">📦</div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
              Product Not Found
            </h2>
            <p className="text-gray-600 dark:text-gray-400 mb-6">
              This product may no longer be available.
            </p>
            <Link
              to="/products"
              className="text-blue-600 dark:text-blue-400 hover:underline font-semibold"
            >
              Browse all products
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Link
          to="/products"
          className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
        >
          &larr; Back to Products
        </Link>

        <div className="mt-6 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div className="md:flex">
            {/* Product Image */}
            <div className="md:w-1/2 flex items-center justify-center">
              {product.imageUrl ? (
                <img
                  src={product.imageUrl}
                  alt={product.name}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="text-8xl">📦</div>
              )}
            </div>

            {/* Product Info */}
            <div className="md:w-1/2 p-8">
              <p className="text-sm text-gray-500 dark:text-gray-400 mb-1">
                {product.id}
              </p>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
                {product.name}
              </h1>

              <div className="flex items-center gap-3 mb-6">
                <span className="text-3xl font-bold text-blue-600 dark:text-blue-400">
                  ${product.price.toFixed(2)}
                </span>
                <span
                  className={`px-3 py-1 rounded-full text-sm font-semibold ${
                    product.stockQuantity > 0
                      ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300'
                      : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300'
                  }`}
                >
                  {product.stockQuantity > 0
                    ? `${product.stockQuantity} in stock`
                    : 'Out of stock'}
                </span>
              </div>

              {product.stockQuantity > 0 ? (
                <div className="space-y-4">
                  <div className="flex items-center gap-3">
                    <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
                      Quantity:
                    </label>
                    <input
                      type="number"
                      min="1"
                      max={product.stockQuantity}
                      value={quantity}
                      onChange={handleQuantityChange}
                      className="w-20 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    />
                  </div>
                  <button
                    onClick={handleAddToCart}
                    disabled={adding}
                    className="w-full bg-blue-600 dark:bg-blue-500 text-white px-6 py-4 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold text-lg disabled:opacity-50"
                  >
                    {adding ? 'Adding...' : 'Add to Cart'}
                  </button>
                </div>
              ) : (
                <button
                  disabled
                  className="w-full bg-gray-300 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-6 py-4 rounded-lg cursor-not-allowed font-semibold text-lg"
                >
                  Out of Stock
                </button>
              )}
            </div>
          </div>

          {/* Description */}
          {product.description && (
            <div className="border-t border-gray-200 dark:border-gray-700 p-8">
              <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
                Description
              </h2>
              <p className="text-gray-700 dark:text-gray-300 leading-relaxed">
                {product.description}
              </p>
            </div>
          )}
        </div>
      </div>

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
};
