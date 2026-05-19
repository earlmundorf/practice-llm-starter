import { useState, useEffect } from 'react';
import { auth, api } from '../services/api';
import { AddressForm } from '../components/AddressForm';
import type { User, Address } from '../types';

export const Users = () => {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [addressLoading, setAddressLoading] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingAddress, setEditingAddress] = useState<Address | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkDeleting, setBulkDeleting] = useState(false);

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    if (!auth.isLoggedIn()) {
      setLoading(false);
      return;
    }

    try {
      const [user, addrs] = await Promise.all([
        api.getUser(),
        api.getAddresses(),
      ]);
      setCurrentUser(user);
      setAddresses(addrs);
    } catch (error) {
      console.error('Failed to load profile:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAddAddress = async (data: Omit<Address, 'id'>) => {
    setAddressLoading(true);
    try {
      await api.createAddress(data);
      const addrs = await api.getAddresses();
      setAddresses(addrs);
      setShowAddForm(false);
    } catch (error) {
      alert('Failed to add address: ' + (error as Error).message);
    } finally {
      setAddressLoading(false);
    }
  };

  const handleUpdateAddress = async (data: Omit<Address, 'id'> & { id?: string }) => {
    if (!data.id) return;
    setAddressLoading(true);
    try {
      await api.updateAddress(data.id, data);
      const addrs = await api.getAddresses();
      setAddresses(addrs);
      setEditingAddress(null);
    } catch (error) {
      alert('Failed to update address: ' + (error as Error).message);
    } finally {
      setAddressLoading(false);
    }
  };

  const handleDeleteAddress = async (id: string) => {
    if (!confirm('Delete this address?')) return;
    setDeletingId(id);
    try {
      await api.deleteAddress(id);
      setAddresses((prev) => prev.filter((a) => a.id !== id));
      setSelectedIds((prev) => { const next = new Set(prev); next.delete(id); return next; });
    } catch (error) {
      alert('Failed to delete address: ' + (error as Error).message);
    } finally {
      setDeletingId(null);
    }
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const nonDefaultAddresses = addresses.filter((a) => !a.defaultAddress);

  const toggleSelectAll = () => {
    if (selectedIds.size === nonDefaultAddresses.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(nonDefaultAddresses.map((a) => a.id)));
    }
  };

  const handleBulkDelete = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`Delete ${selectedIds.size} address${selectedIds.size > 1 ? 'es' : ''}?`)) return;
    setBulkDeleting(true);
    try {
      await Promise.all([...selectedIds].map((id) => api.deleteAddress(id)));
      setAddresses((prev) => prev.filter((a) => !selectedIds.has(a.id)));
      setSelectedIds(new Set());
    } catch (error) {
      alert('Some addresses failed to delete: ' + (error as Error).message);
      const addrs = await api.getAddresses();
      setAddresses(addrs);
      setSelectedIds(new Set());
    } finally {
      setBulkDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-400">Loading profile...</p>
        </div>
      </div>
    );
  }

  if (!currentUser) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="max-w-md w-full mx-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-8 text-center">
            <div className="text-5xl mb-4">👤</div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
              Not Logged In
            </h2>
            <p className="text-gray-600 dark:text-gray-400">
              Please log in to view your profile.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const getInitials = (user: User): string => {
    if (user.fullName) {
      const names = user.fullName.split(' ');
      if (names.length >= 2) {
        return (names[0][0] + names[names.length - 1][0]).toUpperCase();
      }
      return names[0][0].toUpperCase();
    }
    return user.username ? user.username[0].toUpperCase() : '?';
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h2 className="text-3xl font-bold text-gray-900 dark:text-white mb-8">
          👤 My Profile
        </h2>

        {/* Profile Info */}
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-8 border border-gray-200 dark:border-gray-700 mb-8">
          <div className="flex items-center gap-6 mb-8">
            <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-2xl shadow-lg">
              {getInitials(currentUser)}
            </div>
            <div>
              <h3 className="text-2xl font-bold text-gray-900 dark:text-white">
                {currentUser.fullName}
              </h3>
              <p className="text-gray-600 dark:text-gray-400">
                {currentUser.email}
              </p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4 border border-gray-200 dark:border-gray-600">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Full Name</p>
                  <p className="font-semibold text-gray-900 dark:text-white">{currentUser.fullName}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Email</p>
                  <p className="font-semibold text-gray-900 dark:text-white">{currentUser.email}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Username</p>
                  <p className="font-semibold text-gray-900 dark:text-white">{currentUser.username}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* My Addresses */}
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-8 border border-gray-200 dark:border-gray-700">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-2xl font-bold text-gray-900 dark:text-white">
              My Addresses
            </h3>
            {!showAddForm && !editingAddress && (
              <div className="flex items-center gap-2">
                {selectedIds.size > 0 && (
                  <button
                    onClick={handleBulkDelete}
                    disabled={bulkDeleting}
                    className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors font-medium flex items-center gap-2 disabled:opacity-50"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    {bulkDeleting ? 'Deleting...' : `Delete (${selectedIds.size})`}
                  </button>
                )}
                <button
                  onClick={() => setShowAddForm(true)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium flex items-center gap-2"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                  </svg>
                  Add Address
                </button>
              </div>
            )}
          </div>

          {/* Add Form */}
          {showAddForm && (
            <div className="mb-6 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600">
              <h4 className="font-semibold text-gray-900 dark:text-white mb-4">New Address</h4>
              <AddressForm
                onSubmit={handleAddAddress}
                onCancel={() => setShowAddForm(false)}
                loading={addressLoading}
              />
            </div>
          )}

          {/* Edit Form */}
          {editingAddress && (
            <div className="mb-6 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg border border-gray-200 dark:border-gray-600">
              <h4 className="font-semibold text-gray-900 dark:text-white mb-4">Edit Address</h4>
              <AddressForm
                initialData={editingAddress}
                onSubmit={handleUpdateAddress}
                onCancel={() => setEditingAddress(null)}
                loading={addressLoading}
              />
            </div>
          )}

          {/* Address List */}
          {addresses.length === 0 && !showAddForm ? (
            <div className="text-center py-8">
              <p className="text-gray-500 dark:text-gray-400 mb-2">No saved addresses yet.</p>
              <p className="text-sm text-gray-400 dark:text-gray-500">
                Add an address to use during checkout.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {nonDefaultAddresses.length > 1 && (
                <label className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 cursor-pointer pb-1">
                  <input
                    type="checkbox"
                    checked={selectedIds.size === nonDefaultAddresses.length && nonDefaultAddresses.length > 0}
                    onChange={toggleSelectAll}
                    className="h-4 w-4 text-blue-600 rounded border-gray-300 dark:border-gray-600"
                  />
                  Select all ({nonDefaultAddresses.length})
                </label>
              )}
              {addresses.map((addr) => (
                <div
                  key={addr.id}
                  className={`p-4 bg-gray-50 dark:bg-gray-700 rounded-lg border transition-colors ${
                    selectedIds.has(addr.id)
                      ? 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/10'
                      : 'border-gray-200 dark:border-gray-600'
                  }`}
                >
                  <div className="flex items-start gap-3">
                    {addr.defaultAddress ? (
                      <div className="w-4 mt-1" />
                    ) : (
                      <input
                        type="checkbox"
                        checked={selectedIds.has(addr.id)}
                        onChange={() => toggleSelect(addr.id)}
                        className="h-4 w-4 mt-1 text-blue-600 rounded border-gray-300 dark:border-gray-600 cursor-pointer"
                      />
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <p className="font-semibold text-gray-900 dark:text-white">
                          {addr.firstName} {addr.lastName}
                        </p>
                        {addr.defaultAddress && (
                          <span className="text-xs bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded-full font-medium">
                            Default
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-600 dark:text-gray-400">{addr.line1}</p>
                      {addr.line2 && (
                        <p className="text-sm text-gray-600 dark:text-gray-400">{addr.line2}</p>
                      )}
                      <p className="text-sm text-gray-600 dark:text-gray-400">
                        {addr.town}, {addr.postalCode} {addr.country.isocode}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => {
                          setEditingAddress(addr);
                          setShowAddForm(false);
                        }}
                        className="p-2 text-gray-500 hover:text-blue-600 dark:text-gray-400 dark:hover:text-blue-400 transition-colors"
                        title="Edit"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                        </svg>
                      </button>
                      {!addr.defaultAddress && (
                        <button
                          onClick={() => handleDeleteAddress(addr.id)}
                          disabled={deletingId === addr.id}
                          className="p-2 text-gray-500 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400 transition-colors disabled:opacity-50"
                          title="Delete"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
