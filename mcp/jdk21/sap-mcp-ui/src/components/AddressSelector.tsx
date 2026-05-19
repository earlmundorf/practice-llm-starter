import type { Address } from '../types';

interface AddressSelectorProps {
  addresses: Address[];
  selectedId: string | null;
  onSelect: (address: Address) => void;
  onAddNew: () => void;
}

export const AddressSelector = ({ addresses, selectedId, onSelect, onAddNew }: AddressSelectorProps) => {
  return (
    <div className="space-y-3">
      {addresses.map((addr) => (
        <button
          key={addr.id}
          type="button"
          onClick={() => onSelect(addr)}
          className={`w-full text-left p-4 rounded-lg border-2 transition-all ${
            selectedId === addr.id
              ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
              : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500 bg-white dark:bg-gray-700'
          }`}
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="font-semibold text-gray-900 dark:text-white">
                {addr.firstName} {addr.lastName}
              </p>
              <p className="text-sm text-gray-600 dark:text-gray-400">{addr.line1}</p>
              {addr.line2 && (
                <p className="text-sm text-gray-600 dark:text-gray-400">{addr.line2}</p>
              )}
              <p className="text-sm text-gray-600 dark:text-gray-400">
                {addr.town}, {addr.postalCode} {addr.country.isocode}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {addr.defaultAddress && (
                <span className="text-xs bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded-full font-medium">
                  Default
                </span>
              )}
              {selectedId === addr.id && (
                <svg className="w-5 h-5 text-blue-500" fill="currentColor" viewBox="0 0 20 20">
                  <path
                    fillRule="evenodd"
                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                    clipRule="evenodd"
                  />
                </svg>
              )}
            </div>
          </div>
        </button>
      ))}

      <button
        type="button"
        onClick={onAddNew}
        className="w-full p-4 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 hover:border-blue-400 dark:hover:border-blue-500 text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition-all flex items-center justify-center gap-2"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
        </svg>
        <span className="font-medium">Add New Address</span>
      </button>
    </div>
  );
};
