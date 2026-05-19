import React, { useState } from 'react';
import { auth, api } from '../services/api';
import type { User } from '../types';

interface UserPickerProps {
  onUserSelected: (user: User) => void;
  onCancel: (() => void) | null;
}

const DEMO_USERS = [
  { email: 'john.doe@thinkshop.com', password: '1234', name: 'John Doe' },
  { email: 'jane.smith@thinkshop.com', password: '1234', name: 'Jane Smith' },
  { email: 'bob.wilson@thinkshop.com', password: '1234', name: 'Bob Wilson' },
];

export const UserPicker = ({ onUserSelected, onCancel }: UserPickerProps) => {
  const [email, setEmail] = useState('john.doe@thinkshop.com');
  const [password, setPassword] = useState('1234');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    await doLogin(email, password);
  };

  const doLogin = async (loginEmail: string, loginPassword: string) => {
    setLoading(true);
    setError(null);

    try {
      await auth.login(loginEmail, loginPassword);
      const user = await api.getUser();
      onUserSelected(user);
    } catch (err) {
      setError((err as Error).message);
      setLoading(false);
    }
  };

  const handleQuickLogin = (demoUser: typeof DEMO_USERS[number]) => {
    setEmail(demoUser.email);
    setPassword(demoUser.password);
    doLogin(demoUser.email, demoUser.password);
  };

  const getInitials = (name: string): string => {
    const parts = name.split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return parts[0][0].toUpperCase();
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-2xl max-w-md w-full border-2 border-gray-200 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">
              Welcome to ThinkShop!
            </h2>
            <p className="text-gray-600 text-sm mt-1">
              Log in to continue
            </p>
          </div>
          {onCancel && (
            <button
              onClick={onCancel}
              className="text-gray-400 hover:text-gray-600 transition-colors text-2xl leading-none"
              aria-label="Close"
            >
              ×
            </button>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {/* Quick Login Buttons */}
          <div className="mb-6">
            <p className="text-sm font-medium text-gray-500 mb-3">Quick login as demo user:</p>
            <div className="space-y-2">
              {DEMO_USERS.map((demoUser) => (
                <button
                  key={demoUser.email}
                  onClick={() => handleQuickLogin(demoUser)}
                  disabled={loading}
                  className="w-full flex items-center gap-3 p-3 rounded-lg border border-gray-200 hover:border-blue-500 hover:bg-blue-50 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold flex-shrink-0">
                    {getInitials(demoUser.name)}
                  </div>
                  <div className="text-left flex-1 min-w-0">
                    <div className="font-semibold text-gray-900 truncate">
                      {demoUser.name}
                    </div>
                    <div className="text-sm text-gray-600 truncate">
                      {demoUser.email}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Divider */}
          <div className="relative mb-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-gray-200"></div>
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-2 bg-white text-gray-500">or log in manually</span>
            </div>
          </div>

          {/* Login Form */}
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Email
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Enter email"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Enter password"
              />
            </div>

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Logging in...' : 'Log In'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
