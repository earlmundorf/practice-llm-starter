import React, { useState } from 'react';
import type { Address } from '../types';

interface AddressFormProps {
  initialData?: Address;
  onSubmit: (data: Omit<Address, 'id'> & { id?: string }) => void;
  onCancel: () => void;
  loading?: boolean;
}

export const AddressForm = ({ initialData, onSubmit, onCancel, loading }: AddressFormProps) => {
  const [firstName, setFirstName] = useState(initialData?.firstName || '');
  const [lastName, setLastName] = useState(initialData?.lastName || '');
  const [line1, setLine1] = useState(initialData?.line1 || '');
  const [line2, setLine2] = useState(initialData?.line2 || '');
  const [town, setTown] = useState(initialData?.town || '');
  const [postalCode, setPostalCode] = useState(initialData?.postalCode || '');
  const [country] = useState(initialData?.country?.isocode || 'US');
  const [defaultAddress, setDefaultAddress] = useState(initialData?.defaultAddress || false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      ...(initialData?.id ? { id: initialData.id } : {}),
      firstName,
      lastName,
      line1,
      line2: line2 || undefined,
      town,
      postalCode,
      country: { isocode: country },
      defaultAddress,
    });
  };

  const inputClass =
    'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent';
  const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className={labelClass}>First Name *</label>
          <input
            type="text"
            required
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>Last Name *</label>
          <input
            type="text"
            required
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            className={inputClass}
          />
        </div>
      </div>

      <div>
        <label className={labelClass}>Address Line 1 *</label>
        <input
          type="text"
          required
          value={line1}
          onChange={(e) => setLine1(e.target.value)}
          className={inputClass}
          placeholder="Street address"
        />
      </div>

      <div>
        <label className={labelClass}>Address Line 2</label>
        <input
          type="text"
          value={line2}
          onChange={(e) => setLine2(e.target.value)}
          className={inputClass}
          placeholder="Apt, suite, unit, etc. (optional)"
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label className={labelClass}>City *</label>
          <input
            type="text"
            required
            value={town}
            onChange={(e) => setTown(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>Postal Code *</label>
          <input
            type="text"
            required
            value={postalCode}
            onChange={(e) => setPostalCode(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>Country</label>
          <input
            type="text"
            value={country}
            disabled
            className={`${inputClass} bg-gray-100 dark:bg-gray-600 cursor-not-allowed`}
          />
        </div>
      </div>

      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="defaultAddress"
          checked={defaultAddress}
          onChange={(e) => setDefaultAddress(e.target.checked)}
          className="h-4 w-4 text-blue-600 rounded border-gray-300 dark:border-gray-600"
        />
        <label htmlFor="defaultAddress" className="text-sm text-gray-700 dark:text-gray-300">
          Set as default address
        </label>
      </div>

      <div className="flex gap-3 pt-2">
        <button
          type="submit"
          disabled={loading}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 font-medium"
        >
          {loading ? 'Saving...' : initialData?.id ? 'Update Address' : 'Add Address'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors font-medium"
        >
          Cancel
        </button>
      </div>
    </form>
  );
};
