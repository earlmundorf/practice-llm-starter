import { useState, useEffect } from 'react';
import { api } from '../services/api';
import { EntityModal } from './EntityModal';
import type { Product } from '../types';

interface Props {
  code: string;
  onClose: () => void;
  onAddToCart?: (code: string) => void;
}

export const ProductModal = ({ code, onClose, onAddToCart }: Props) => {
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.getProduct(code)
      .then((data) => { if (!cancelled) setProduct(data); })
      .catch(() => { if (!cancelled) setError('Failed to load product details'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [code]);

  return (
    <EntityModal title={product?.name ?? code} onClose={onClose}>
      {loading && (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
        </div>
      )}
      {error && <p className="text-red-600 dark:text-red-400 text-center py-8">{error}</p>}
      {product && (
        <div className="flex flex-col md:flex-row gap-6">
          {product.imageUrl && (
            <img
              src={product.imageUrl}
              alt={product.name}
              className="w-full md:w-56 h-56 object-contain rounded-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
            />
          )}
          <div className="flex-1">
            <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">Code: {product.id}</p>
            <p className="text-2xl font-bold text-green-600 dark:text-green-400 mb-3">
              ${product.price.toFixed(2)}
            </p>
            {product.description && (
              <p className="text-sm text-gray-700 dark:text-gray-300 mb-4 whitespace-pre-line">
                {product.description}
              </p>
            )}
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              {product.stockQuantity > 0
                ? `${product.stockQuantity} in stock`
                : 'Out of stock'}
            </p>
            {onAddToCart && product.stockQuantity > 0 && (
              <button
                type="button"
                onClick={() => onAddToCart(product.id)}
                className="bg-blue-600 dark:bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold"
              >
                Add to cart
              </button>
            )}
          </div>
        </div>
      )}
    </EntityModal>
  );
};
