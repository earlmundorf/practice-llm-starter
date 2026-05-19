const FACET_LABELS: Record<string, string> = {
  priceValue: 'Price',
  inStockFlag: 'Availability',
};

const VALUE_LABELS: Record<string, Record<string, string>> = {
  inStockFlag: { true: 'In Stock', false: 'Out of Stock' },
};

interface ActiveFacetTagsProps {
  activeFacets: Record<string, string[]>;
  onToggle: (facetCode: string, valueCode: string) => void;
  onClearAll: () => void;
}

export const ActiveFacetTags = ({ activeFacets, onToggle, onClearAll }: ActiveFacetTagsProps) => {
  const entries = Object.entries(activeFacets);
  if (entries.length === 0) return null;

  const totalCount = entries.reduce((sum, [, values]) => sum + values.length, 0);

  return (
    <div className="flex flex-wrap items-center gap-2 mb-4">
      <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mr-1">
        Active filters
      </span>
      {entries.map(([code, values]) => {
        const groupLabel = FACET_LABELS[code] || code;
        const valueMap = VALUE_LABELS[code];
        return values.map((value) => (
          <button
            key={`${code}:${value}`}
            onClick={() => onToggle(code, value)}
            className="group inline-flex items-center gap-1.5 pl-3 pr-2 py-1 rounded-full text-sm bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900/50 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
          >
            <span className="text-blue-500 dark:text-blue-400 font-medium">{groupLabel}:</span>
            <span>{valueMap?.[value] ?? value}</span>
            <svg className="w-3.5 h-3.5 text-blue-400 group-hover:text-blue-600 dark:group-hover:text-blue-200 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        ));
      })}
      {totalCount > 1 && (
        <button
          onClick={onClearAll}
          className="text-xs font-medium text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 transition-colors ml-1"
        >
          Clear all
        </button>
      )}
    </div>
  );
};
