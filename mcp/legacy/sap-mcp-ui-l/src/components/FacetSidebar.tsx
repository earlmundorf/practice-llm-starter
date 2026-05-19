import { useState, useEffect } from 'react';
import type { Facet } from '../types';

const FACET_LABELS: Record<string, string> = {
  priceValue: 'Price',
  inStockFlag: 'Availability',
};

const VALUE_LABELS: Record<string, Record<string, string>> = {
  inStockFlag: { true: 'In Stock', false: 'Out of Stock' },
};

interface FacetSidebarProps {
  facets: Facet[];
  activeFacets: Record<string, string[]>;
  onToggle: (facetCode: string, valueCode: string) => void;
}

export const FacetSidebar = ({ facets, activeFacets, onToggle }: FacetSidebarProps) => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const totalActive = Object.values(activeFacets).flat().length;

  useEffect(() => {
    if (mobileOpen) document.body.style.overflow = 'hidden';
    else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [mobileOpen]);

  const visibleFacets = facets.filter((f) => FACET_LABELS[f.code]);

  const content = (
    <div className="space-y-1">
      <h3 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-widest mb-3 px-1">
        Filters
      </h3>
      {visibleFacets.map((facet) => (
        <FacetGroup
          key={facet.code}
          facet={facet}
          activeValues={activeFacets[facet.code] || []}
          onToggle={onToggle}
        />
      ))}
    </div>
  );

  return (
    <>
      {/* Mobile trigger */}
      <button
        onClick={() => setMobileOpen(true)}
        className="lg:hidden fixed bottom-6 right-6 z-40 flex items-center gap-2 px-4 py-3 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-full shadow-lg hover:scale-105 active:scale-95 transition-transform"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
        </svg>
        <span className="text-sm font-medium">Filters</span>
        {totalActive > 0 && (
          <span className="flex items-center justify-center w-5 h-5 text-xs font-bold rounded-full bg-blue-500 text-white dark:bg-blue-600">
            {totalActive}
          </span>
        )}
      </button>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="lg:hidden fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMobileOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-80 max-w-[85vw] bg-white dark:bg-gray-800 shadow-2xl overflow-y-auto">
            <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Filters</h2>
              <button
                onClick={() => setMobileOpen(false)}
                className="p-2 -mr-2 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="p-4">{content}</div>
          </div>
        </div>
      )}

      {/* Desktop sidebar */}
      <aside className="hidden lg:block w-60 flex-shrink-0">
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-4 sticky top-24">
          {content}
        </div>
      </aside>
    </>
  );
};

interface FacetGroupProps {
  facet: Facet;
  activeValues: string[];
  onToggle: (facetCode: string, valueCode: string) => void;
}

const FacetGroup = ({ facet, activeValues, onToggle }: FacetGroupProps) => {
  const [collapsed, setCollapsed] = useState(false);
  const label = FACET_LABELS[facet.code] || facet.name;
  const activeCount = activeValues.length;
  const valueLabels = VALUE_LABELS[facet.code];

  return (
    <div className="border-t border-gray-100 dark:border-gray-700/50 pt-3 pb-1">
      <button
        onClick={() => setCollapsed(!collapsed)}
        className="flex items-center justify-between w-full group"
      >
        <span className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-800 dark:text-gray-200 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">
            {label}
          </span>
          {activeCount > 0 && (
            <span className="flex items-center justify-center min-w-[1.25rem] h-5 px-1.5 text-xs font-semibold rounded-full bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300">
              {activeCount}
            </span>
          )}
        </span>
        <svg
          className={`w-4 h-4 text-gray-400 transition-transform duration-200 ${collapsed ? '-rotate-90' : ''}`}
          fill="none" stroke="currentColor" viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {!collapsed && (
        <div className="mt-2 space-y-0.5">
          {facet.values.map((fv) => {
            const isActive = activeValues.includes(fv.code);
            const displayName = valueLabels?.[fv.code] ?? fv.name;
            return (
              <label
                key={fv.code}
                className={`flex items-center gap-2.5 px-2 py-1.5 rounded-lg text-sm cursor-pointer transition-colors ${
                  isActive
                    ? 'bg-blue-50 dark:bg-blue-900/20'
                    : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'
                }`}
              >
                <span className={`flex items-center justify-center w-4 h-4 rounded border-2 transition-colors flex-shrink-0 ${
                  isActive
                    ? 'bg-blue-600 border-blue-600'
                    : 'border-gray-300 dark:border-gray-500'
                }`}>
                  {isActive && (
                    <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </span>
                <input
                  type="checkbox"
                  checked={isActive}
                  onChange={() => onToggle(facet.code, fv.code)}
                  className="sr-only"
                />
                <span className={`flex-1 truncate ${
                  isActive
                    ? 'text-gray-900 dark:text-white font-medium'
                    : 'text-gray-600 dark:text-gray-400'
                }`}>
                  {displayName}
                </span>
                <span className={`text-xs tabular-nums ${
                  isActive
                    ? 'text-blue-600 dark:text-blue-400 font-medium'
                    : 'text-gray-400 dark:text-gray-500'
                }`}>
                  {fv.count}
                </span>
              </label>
            );
          })}
        </div>
      )}
    </div>
  );
};
