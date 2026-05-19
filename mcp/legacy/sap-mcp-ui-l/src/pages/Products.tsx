import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { auth, api, cartUtils } from '../services/api';
import { ProductCard } from '../components/ProductCard';
import { FacetSidebar } from '../components/FacetSidebar';
import { ActiveFacetTags } from '../components/ActiveFacetTags';
import { Toast } from '../components/Toast';
import type { Product, Facet, SearchResult, VisualSearchResult } from '../types';

interface ToastState {
  message: string;
  type: 'success' | 'error' | 'info';
}

const SORT_OPTIONS = [
  { code: 'relevance', label: 'Relevance' },
  { code: 'name-asc', label: 'Name A-Z' },
  { code: 'name-desc', label: 'Name Z-A' },
  { code: 'price-asc', label: 'Price Low-High' },
  { code: 'price-desc', label: 'Price High-Low' },
];

const PAGE_SIZE = 12;

const parseFacetParams = (searchParams: URLSearchParams): Record<string, string[]> => {
  const filters: Record<string, string[]> = {};
  const raw = searchParams.get('facets') || '';
  if (!raw) return filters;
  for (const part of raw.split('|')) {
    const idx = part.indexOf(':');
    if (idx < 0) continue;
    const code = part.slice(0, idx);
    const value = part.slice(idx + 1);
    (filters[code] ??= []).push(value);
  }
  return filters;
};

const serializeFacetParams = (filters: Record<string, string[]>): string =>
  Object.entries(filters)
    .flatMap(([code, values]) => values.map((v) => `${code}:${v}`))
    .join('|');

export const Products = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState<Product[]>([]);
  const [initialLoad, setInitialLoad] = useState(true);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [toast, setToast] = useState<ToastState | null>(null);
  const [facets, setFacets] = useState<Facet[]>([]);

  const [searchInput, setSearchInput] = useState(searchParams.get('q') || '');
  const searchQuery = searchParams.get('q') || '';
  const sort = searchParams.get('sort') || 'relevance';
  const currentPage = parseInt(searchParams.get('page') || '0', 10);
  const activeFacets = parseFacetParams(searchParams);
  const hasActiveFacets = Object.keys(activeFacets).length > 0;
  const [pagination, setPagination] = useState<SearchResult['pagination']>({
    currentPage: 0, pageSize: PAGE_SIZE, totalResults: 0, totalPages: 0,
  });

  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  // Visual search state
  const [visualResults, setVisualResults] = useState<VisualSearchResult | null>(null);
  const [visualLoading, setVisualLoading] = useState(false);
  const [visualPreview, setVisualPreview] = useState<string | null>(null);
  const visualInputRef = useRef<HTMLInputElement>(null);

  const fetchProducts = useCallback(async (query: string, sortCode: string, page: number, facetFilters: Record<string, string[]>) => {
    try {
      const result = await api.searchProducts(query, sortCode, page, PAGE_SIZE, facetFilters);
      setProducts(result.products);
      setPagination(result.pagination);
      setFacets(result.facets);
    } catch (error) {
      console.error('Error loading products:', error);
      setToast({ message: 'Failed to load products', type: 'error' });
    } finally {
      setInitialLoad(false);
    }
  }, []);

  useEffect(() => {
    fetchProducts(searchQuery, sort, currentPage, activeFacets);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchQuery, sort, currentPage, searchParams.get('facets'), fetchProducts]);

  // --- URL param handlers ---

  const handleSearchChange = (value: string) => {
    setSearchInput(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      const params = new URLSearchParams(searchParams);
      if (value) { params.set('q', value); } else { params.delete('q'); }
      params.delete('page');
      setSearchParams(params, { replace: true });
    }, 300);
  };

  const handleSortChange = (newSort: string) => {
    const params = new URLSearchParams(searchParams);
    params.set('sort', newSort);
    params.delete('page');
    setSearchParams(params, { replace: true });
  };

  const toggleFacet = (facetCode: string, valueCode: string) => {
    const filters = { ...activeFacets };
    const current = filters[facetCode] || [];
    if (current.includes(valueCode)) {
      filters[facetCode] = current.filter((v) => v !== valueCode);
      if (filters[facetCode].length === 0) delete filters[facetCode];
    } else {
      filters[facetCode] = [...current, valueCode];
    }
    const params = new URLSearchParams(searchParams);
    const serialized = serializeFacetParams(filters);
    if (serialized) { params.set('facets', serialized); } else { params.delete('facets'); }
    params.delete('page');
    setSearchParams(params, { replace: true });
  };

  const clearAllFacets = () => {
    const params = new URLSearchParams(searchParams);
    params.delete('facets');
    params.delete('page');
    setSearchParams(params, { replace: true });
  };

  const goToPage = (page: number) => {
    const params = new URLSearchParams(searchParams);
    if (page === 0) { params.delete('page'); } else { params.set('page', String(page)); }
    setSearchParams(params, { replace: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // --- Cart ---

  const addToCart = async (product: Product, quantity: number) => {
    if (quantity < 1 || quantity > product.stockQuantity) {
      setToast({ message: `Please enter a valid quantity (1-${product.stockQuantity})`, type: 'error' });
      return;
    }
    if (!auth.isLoggedIn()) {
      setToast({ message: 'Please log in first', type: 'error' });
      return;
    }
    try {
      const currentCart = await cartUtils.getCart();
      const existing = currentCart.find((item) => item.productId === product.id);
      if (existing && existing.quantity + quantity > product.stockQuantity) {
        setToast({ message: `Cannot add more. Maximum stock: ${product.stockQuantity}`, type: 'error' });
        return;
      }
      await cartUtils.addToCart(product, quantity);
      setToast({ message: `${product.name} added to cart!`, type: 'success' });
      window.dispatchEvent(new CustomEvent('cartItemAdded'));
    } catch (error) {
      setToast({ message: (error as Error).message || 'Failed to add to cart', type: 'error' });
    }
  };

  // --- Visual search ---

  const handleVisualFile = useCallback(async (file: File) => {
    const allowed = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
    if (!allowed.has(file.type)) {
      setToast({ message: 'Please use a JPEG, PNG, WebP, or GIF image.', type: 'error' });
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setToast({ message: 'Image is too large. Please use an image under 10MB.', type: 'error' });
      return;
    }
    if (!auth.isLoggedIn()) {
      setToast({ message: 'Please log in first', type: 'error' });
      return;
    }

    const reader = new FileReader();
    reader.onload = async () => {
      const dataUrl = reader.result as string;
      const base64 = dataUrl.split(',')[1];
      setVisualPreview(dataUrl);
      setVisualLoading(true);
      try {
        const result = await api.visualSearch(base64, file.type);
        setVisualResults(result);
      } catch (err) {
        setToast({ message: (err as Error).message || 'Visual search failed', type: 'error' });
        setVisualResults(null);
        setVisualPreview(null);
      } finally {
        setVisualLoading(false);
      }
    };
    reader.readAsDataURL(file);
  }, []);

  const clearVisualSearch = useCallback(() => {
    setVisualResults(null);
    setVisualPreview(null);
    setVisualLoading(false);
  }, []);

  // --- Pagination numbers ---

  const pageNumbers = (() => {
    const { totalPages } = pagination;
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
    const pages: (number | 'ellipsis')[] = [0];
    const start = Math.max(1, currentPage - 1);
    const end = Math.min(totalPages - 2, currentPage + 1);
    if (start > 1) pages.push('ellipsis' as unknown as number);
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 2) pages.push('ellipsis' as unknown as number);
    pages.push(totalPages - 1);
    return pages;
  })();

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Our Products</h2>
            <p className="text-gray-600 dark:text-gray-400 mt-1">Browse our selection of premium electronics</p>
          </div>
          <div className="flex items-center gap-2 bg-white dark:bg-gray-800 rounded-lg shadow-md p-1 border border-gray-200 dark:border-gray-700">
            {(['grid', 'list'] as const).map((mode) => (
              <button
                key={mode}
                onClick={() => setViewMode(mode)}
                className={`px-4 py-2 rounded-md transition-colors font-medium capitalize ${
                  viewMode === mode
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
                }`}
              >
                {mode}
              </button>
            ))}
          </div>
        </div>

        {/* Search & Sort */}
        <div className="flex flex-col sm:flex-row gap-3 mb-6">
          <div className="relative flex-1">
            <input
              type="text"
              value={searchInput}
              onChange={(e) => handleSearchChange(e.target.value)}
              onPaste={(e) => {
                const items = e.clipboardData?.items;
                if (!items) return;
                for (const item of items) {
                  if (item.type.startsWith('image/')) {
                    e.preventDefault();
                    const file = item.getAsFile();
                    if (file) handleVisualFile(file);
                    return;
                  }
                }
              }}
              placeholder="Search products... (or paste an image)"
              className="w-full pl-10 pr-16 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
            />
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
              {searchInput && (
                <button
                  onClick={() => handleSearchChange('')}
                  className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 p-0.5"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
              <button
                onClick={() => visualInputRef.current?.click()}
                title="Search by image"
                aria-label="Search by image"
                className="text-gray-400 hover:text-blue-500 dark:hover:text-blue-400 p-0.5 transition-colors"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </button>
              <input
                ref={visualInputRef}
                type="file"
                accept="image/*"
                capture="environment"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleVisualFile(file);
                  e.target.value = '';
                }}
              />
            </div>
          </div>
          <select
            value={sort}
            onChange={(e) => handleSortChange(e.target.value)}
            className="px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
          >
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.code} value={opt.code}>{opt.label}</option>
            ))}
          </select>
        </div>

        <ActiveFacetTags
          activeFacets={activeFacets}
          onToggle={toggleFacet}
          onClearAll={clearAllFacets}
        />

        {/* Visual search results overlay */}
        {(visualLoading || visualResults) && (
          <div className="mb-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                {visualPreview && (
                  <img src={visualPreview} alt="Search image" className="w-12 h-12 rounded-xl object-cover border border-gray-200 dark:border-gray-700 shadow-sm" />
                )}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                    {visualLoading ? 'Searching by image...' : 'Visual Search Results'}
                  </h3>
                  {!visualLoading && visualResults && (
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      Found {visualResults.products.length} catalog match{visualResults.products.length !== 1 ? 'es' : ''}
                    </p>
                  )}
                </div>
              </div>
              <button
                onClick={clearVisualSearch}
                className="text-sm text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
                Clear
              </button>
            </div>

            {visualLoading ? (
              <div className="flex items-center justify-center py-16">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600 mx-auto"></div>
                  <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">Analyzing image...</p>
                </div>
              </div>
            ) : visualResults && (
              <>
                {/* AI reasoning */}
                {visualResults.visionAnalysis && (
                  <div className="bg-sky-50 dark:bg-sky-900/20 border border-sky-200 dark:border-sky-800 rounded-lg px-4 py-3 mb-5">
                    <p className="text-sm text-sky-900 dark:text-sky-200 m-0">
                      <strong>AI Analysis:</strong> {visualResults.visionAnalysis}
                    </p>
                    {visualResults.aiDetail?.searchTerms && (
                      <div className="flex items-center gap-2 mt-2 flex-wrap">
                        <span className="text-xs text-sky-700 dark:text-sky-300 font-medium">Searched for:</span>
                        {visualResults.aiDetail.searchTerms.map((term) => (
                          <span key={term} className="text-xs bg-sky-100 dark:bg-sky-800/40 text-sky-700 dark:text-sky-300 px-2 py-0.5 rounded-full">{term}</span>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Match type indicators + product cards */}
                {visualResults.mappedProducts.length > 0 ? (
                  <div className={`grid grid-cols-1 ${viewMode === 'grid' ? 'md:grid-cols-2 lg:grid-cols-3' : ''} gap-6`}>
                    {visualResults.mappedProducts.map((match) => (
                      <div key={match.product.id} className="relative">
                        {/* Match badge overlay */}
                        <div className="absolute top-2 right-2 z-10 flex items-center gap-1.5">
                          <span className={`text-xs font-bold uppercase px-2 py-0.5 rounded-full shadow-sm ${
                            match.matchType === 'bestMatch'
                              ? 'bg-green-100 text-green-800 dark:bg-green-900/60 dark:text-green-300'
                              : match.matchType === 'similar'
                              ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/60 dark:text-blue-300'
                              : 'bg-purple-100 text-purple-800 dark:bg-purple-900/60 dark:text-purple-300'
                          }`}>
                            {match.matchType === 'bestMatch' ? 'Best Match' : match.matchType === 'similar' ? 'Similar' : 'You Might Like'}
                          </span>
                          <span className="text-xs text-gray-400 dark:text-gray-500 bg-white/80 dark:bg-gray-800/80 px-1.5 py-0.5 rounded-full">
                            {Math.round(match.confidence * 100)}%
                          </span>
                        </div>
                        <ProductCard product={match.product} onAddToCart={addToCart} />
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-10 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
                    <p className="text-base text-yellow-800 dark:text-yellow-200 m-0">No matching products found. Try a different angle or a clearer image.</p>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* Normal product results (hidden during visual search) */}
        {!visualLoading && !visualResults && !initialLoad && (
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
            Showing {products.length} of {pagination.totalResults} products
            {searchQuery && <> for &ldquo;{searchQuery}&rdquo;</>}
          </p>
        )}

        {!visualLoading && !visualResults && <div className="flex gap-6">
          <FacetSidebar
            facets={facets}
            activeFacets={activeFacets}
            onToggle={toggleFacet}
          />

          <div className="flex-1 min-w-0">
            {initialLoad ? (
              <div className="flex items-center justify-center py-20">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                  <p className="mt-4 text-gray-600 dark:text-gray-400">Loading products...</p>
                </div>
              </div>
            ) : products.length === 0 ? (
              <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-12 text-center border border-gray-200 dark:border-gray-700">
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">No Products Found</h3>
                <p className="text-gray-600 dark:text-gray-400">
                  {searchQuery || hasActiveFacets
                    ? 'Try a different search term or clear the filters.'
                    : 'Check back soon for new items!'}
                </p>
                {(searchQuery || hasActiveFacets) && (
                  <button
                    onClick={() => { handleSearchChange(''); clearAllFacets(); }}
                    className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                  >
                    Clear Filters
                  </button>
                )}
              </div>
            ) : viewMode === 'grid' ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {products.map((product) => (
                  <ProductCard key={product.id} product={product} onAddToCart={addToCart} />
                ))}
              </div>
            ) : (
              <div className="space-y-4">
                {products.map((product) => (
                  <div
                    key={product.id}
                    onClick={() => navigate(`/products/${product.id}`)}
                    className="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 p-6 hover:shadow-lg transition-shadow cursor-pointer"
                  >
                    <div className="flex items-center gap-6">
                      <div className="flex-1">
                        <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">{product.name}</h3>
                        <p className="text-gray-600 dark:text-gray-400 mb-3">{product.description}</p>
                        <div className="flex items-center gap-4 text-sm">
                          <span className={`font-semibold ${product.stockQuantity > 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                            {product.stockQuantity > 0 ? 'In Stock' : 'Out of Stock'}
                          </span>
                          <span className="text-2xl font-bold text-green-600 dark:text-green-400">${product.price.toFixed(2)}</span>
                        </div>
                      </div>
                      <div className="flex flex-col gap-3" onClick={(e: React.MouseEvent) => e.stopPropagation()}>
                        <button
                          onClick={() => addToCart(product, 1)}
                          disabled={product.stockQuantity === 0}
                          className="bg-blue-600 dark:bg-blue-500 text-white px-6 py-3 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          Add to Cart
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!initialLoad && pagination.totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <button
                  onClick={() => goToPage(currentPage - 1)}
                  disabled={currentPage === 0}
                  className="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  Prev
                </button>
                {pageNumbers.map((p, i) =>
                  p === ('ellipsis' as unknown as number) ? (
                    <span key={`e${i}`} className="px-2 text-gray-400">...</span>
                  ) : (
                    <button
                      key={p}
                      onClick={() => goToPage(p as number)}
                      className={`px-3 py-2 rounded-lg border transition-colors ${
                        p === currentPage
                          ? 'bg-blue-600 text-white border-blue-600'
                          : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                      }`}
                    >
                      {(p as number) + 1}
                    </button>
                  )
                )}
                <button
                  onClick={() => goToPage(currentPage + 1)}
                  disabled={currentPage >= pagination.totalPages - 1}
                  className="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </div>}
      </div>

      {toast && (
        <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />
      )}
    </div>
  );
};
