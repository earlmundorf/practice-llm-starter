import { useState, useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../services/api';
import {
  KNOWLEDGE_CATEGORIES,
  type KnowledgeCategory,
  type KnowledgeEntry,
} from '../types';

export const HelpCenter = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchQuery = searchParams.get('q') || '';
  const category = (searchParams.get('category') || '') as KnowledgeCategory | '';

  const [searchInput, setSearchInput] = useState(searchQuery);
  const [state, setState] = useState<{
    entries: KnowledgeEntry[];
    loading: boolean;
    error: string | null;
    key: string;
  }>({ entries: [], loading: true, error: null, key: '' });

  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const fetchKey = `${searchQuery}|${category}`;

  useEffect(() => {
    let cancelled = false;
    api
      .searchKnowledge({
        q: searchQuery || undefined,
        category: category || undefined,
        pageSize: 50,
      })
      .then((result) => {
        if (cancelled) return;
        setState({ entries: result.results, loading: false, error: null, key: fetchKey });
      })
      .catch(() => {
        if (cancelled) return;
        setState({ entries: [], loading: false, error: "Couldn't load help entries.", key: fetchKey });
      });
    return () => {
      cancelled = true;
    };
  }, [searchQuery, category, fetchKey]);

  const showLoading = state.loading || state.key !== fetchKey;
  const { entries, error } = state;

  // Cleanup debounce on unmount.
  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const handleSearchChange = (value: string) => {
    setSearchInput(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      const params = new URLSearchParams(searchParams);
      if (value) {
        params.set('q', value);
      } else {
        params.delete('q');
      }
      setSearchParams(params, { replace: true });
    }, 300);
  };

  const handleCategoryClick = (c: KnowledgeCategory) => {
    const params = new URLSearchParams(searchParams);
    if (c === category) {
      params.delete('category');
    } else {
      params.set('category', c);
    }
    setSearchParams(params, { replace: true });
  };

  const clearFilters = () => {
    handleSearchChange('');
    const params = new URLSearchParams(searchParams);
    params.delete('category');
    setSearchParams(params, { replace: true });
  };

  const qs = searchParams.toString();
  const preservedQs = qs ? `?${qs}` : '';

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-6">
          Help Center
        </h1>

        <div className="mb-4">
          <input
            type="text"
            value={searchInput}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="Search help articles..."
            aria-label="Search help"
            className="w-full px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
          />
        </div>

        <div className="flex flex-wrap gap-2 mb-6" role="group" aria-label="Filter by category">
          {KNOWLEDGE_CATEGORIES.map((c) => {
            const active = c === category;
            return (
              <button
                key={c}
                onClick={() => handleCategoryClick(c)}
                aria-label={`Filter by category: ${c}`}
                aria-pressed={active}
                className={`px-3 py-1.5 rounded-full text-sm font-medium capitalize transition-colors border ${
                  active
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700'
                }`}
              >
                {c}
              </button>
            );
          })}
        </div>

        {showLoading ? (
          <div className="flex items-center justify-center py-20">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
              <p className="mt-4 text-gray-600 dark:text-gray-400">Loading help entries...</p>
            </div>
          </div>
        ) : error ? (
          <div
            role="alert"
            className="px-4 py-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
          >
            Couldn't load help entries.
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-12 text-center border border-gray-200 dark:border-gray-700">
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              No help entries found
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              {searchQuery || category
                ? 'Try a different search term or clear the filters.'
                : 'Check back soon for new help content!'}
            </p>
            {(searchQuery || category) && (
              <button
                onClick={clearFilters}
                className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                Clear Filters
              </button>
            )}
          </div>
        ) : (
          <ul className="space-y-3">
            {entries.map((e) => (
              <li key={e.uid}>
                <Link
                  to={`/help/${e.uid}${preservedQs}`}
                  className="block p-5 bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 hover:shadow-lg transition-shadow"
                >
                  <div className="flex items-start justify-between gap-4">
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                      {e.title}
                    </h3>
                    <span className="shrink-0 px-2 py-0.5 rounded-full text-xs font-medium capitalize bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
                      {e.category}
                    </span>
                  </div>
                  <p className="mt-2 text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
                    {e.summary}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};
