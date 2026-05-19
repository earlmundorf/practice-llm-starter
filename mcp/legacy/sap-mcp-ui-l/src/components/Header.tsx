import { Link, useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { auth, api, cartUtils } from '../services/api';
import { useDarkMode } from '../contexts/DarkModeContext';
import { UserPicker } from './UserPicker';
import { CartModal } from './CartModal';
import type { User } from '../types';

export const Header = () => {
  const { darkMode, toggleDarkMode } = useDarkMode();
  const navigate = useNavigate();
  const [cartCount, setCartCount] = useState(0);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [showUserPicker, setShowUserPicker] = useState(false);
  const [showCartModal, setShowCartModal] = useState(false);
  const [showUserDropdown, setShowUserDropdown] = useState(false);
  const [cartPulse, setCartPulse] = useState(false);

  useEffect(() => {
    const handleCartItemAdded = () => {
      setCartPulse(true);
      setTimeout(() => setCartPulse(false), 400);
    };

    const loadCurrentUser = async () => {
      if (auth.isLoggedIn()) {
        try {
          const user = await api.getUser();
          setCurrentUser(user);
        } catch (error) {
          console.error('Failed to load user:', error);
          setCurrentUser(null);
        }
      }
    };

    const updateCartCount = async () => {
      try {
        const cart = await cartUtils.getCart();
        setCartCount(cartUtils.getCartCount(cart));
      } catch (error) {
        console.error('Failed to update cart count:', error);
        setCartCount(0);
      }
    };

    loadCurrentUser();
    updateCartCount();
    const handleOpenCartModal = () => setShowCartModal(true);

    window.addEventListener('cartUpdated', updateCartCount);
    window.addEventListener('cartItemAdded', handleCartItemAdded);
    window.addEventListener('authChanged', loadCurrentUser);
    window.addEventListener('openCartModal', handleOpenCartModal);

    return () => {
      window.removeEventListener('cartUpdated', updateCartCount);
      window.removeEventListener('cartItemAdded', handleCartItemAdded);
      window.removeEventListener('authChanged', loadCurrentUser);
      window.removeEventListener('openCartModal', handleOpenCartModal);
    };
  }, []);

  const handleUserSelected = (user: User) => {
    setCurrentUser(user);
    setShowUserPicker(false);
    window.dispatchEvent(new Event('cartUpdated'));
  };

  const handleSwitchAccount = () => {
    setShowUserPicker(true);
    setShowUserDropdown(false);
  };

  const handleCancelUserPicker = () => {
    setShowUserPicker(false);
  };

  const handleLogout = () => {
    auth.logout();
    setCurrentUser(null);
    setShowUserDropdown(false);
    setCartCount(0);
    setShowUserPicker(true);
    window.dispatchEvent(new Event('cartUpdated'));
  };

  const getInitials = (user: User | null): string => {
    if (!user) return '?';
    if (user.fullName) {
      const names = user.fullName.split(' ');
      if (names.length >= 2) {
        return (names[0][0] + names[names.length - 1][0]).toUpperCase();
      }
      return names[0][0].toUpperCase();
    }
    return user.username ? user.username[0].toUpperCase() : '?';
  };

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        showUserDropdown &&
        !(event.target as Element).closest('.user-dropdown-container')
      ) {
        setShowUserDropdown(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showUserDropdown]);

  return (
    <>
      {showUserPicker && (
        <UserPicker
          onUserSelected={handleUserSelected}
          onCancel={currentUser ? handleCancelUserPicker : null}
        />
      )}
      <CartModal
        isOpen={showCartModal}
        onClose={() => setShowCartModal(false)}
      />

      {/* Desktop Header - Top */}
      <nav className="hidden md:flex flex-shrink-0 bg-white dark:bg-gradient-to-r dark:from-gray-800 dark:via-gray-900 dark:to-gray-800 border-b border-gray-200 dark:border-gray-700 shadow-lg transition-colors duration-300">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 w-full">
          <div className="flex justify-between items-center h-16">
            <Link to="/" className="flex items-center space-x-2">
              <span className="text-3xl">🛒</span>
              <span className="text-gray-900 dark:text-white text-2xl font-bold">
                ThinkShop
              </span>
              <span className="text-gray-400 dark:text-gray-500 text-xs font-medium ml-1 self-end mb-1">
                for SAP Commerce
              </span>
            </Link>

            <div className="flex items-center space-x-1 sm:space-x-4">
              <Link
                to="/"
                className="text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-3 py-2 rounded-lg transition-all duration-200 font-medium"
              >
                🏠 <span className="hidden sm:inline">Home</span>
              </Link>

              <Link
                to="/products"
                className="text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-3 py-2 rounded-lg transition-all duration-200 font-medium"
              >
                📦 <span className="hidden sm:inline">Products</span>
              </Link>

              <Link
                to="/orders"
                className="text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-3 py-2 rounded-lg transition-all duration-200 font-medium"
              >
                📋 <span className="hidden sm:inline">Orders</span>
              </Link>

              <Link
                to="/chat"
                className="text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-3 py-2 rounded-lg transition-all duration-200 font-medium"
              >
                💬 <span className="hidden sm:inline">Chat</span>
              </Link>

              <Link
                to="/architecture"
                className="text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-3 py-2 rounded-lg transition-all duration-200 font-medium"
              >
                🏗️ <span className="hidden sm:inline">Architecture</span>
              </Link>

              <button
                onClick={toggleDarkMode}
                className="bg-gray-100 hover:bg-gray-200 dark:bg-white/10 dark:hover:bg-white/20 text-gray-700 dark:text-white px-3 py-2 rounded-lg transition-all duration-200 font-medium"
                title={darkMode ? 'Switch to light mode' : 'Switch to dark mode'}
              >
                {darkMode ? '☀️' : '🌙'}
              </button>

              <button
                onClick={() => setShowCartModal(true)}
                className={`bg-gray-100 hover:bg-gray-200 dark:bg-white/10 dark:hover:bg-white/20 text-gray-700 dark:text-white px-3 py-2 rounded-lg transition-all duration-200 relative font-medium ${cartPulse ? 'animate-cart-pulse' : ''}`}
              >
                🛒 <span className="hidden sm:inline">Cart</span>
                {cartCount > 0 && (
                  <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs font-bold rounded-full h-5 w-5 flex items-center justify-center">
                    {cartCount}
                  </span>
                )}
              </button>

              {/* User Avatar - Far Right */}
              {currentUser ? (
                <div className="relative ml-2 user-dropdown-container">
                  <button
                    onClick={() => setShowUserDropdown(!showUserDropdown)}
                    className="flex items-center space-x-2 hover:bg-gray-100 dark:hover:bg-white/5 px-2 py-1 rounded-lg transition-all duration-200"
                  >
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white font-bold text-sm shadow-lg ring-2 ring-gray-200 dark:ring-white/30">
                      {getInitials(currentUser)}
                    </div>
                    <svg
                      className={`w-4 h-4 text-gray-700 dark:text-white transition-transform duration-200 ${showUserDropdown ? 'rotate-180' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M19 9l-7 7-7-7"
                      />
                    </svg>
                  </button>

                  {/* Dropdown Menu */}
                  {showUserDropdown && (
                    <div className="absolute right-0 mt-2 w-64 bg-white dark:bg-gray-800 rounded-lg shadow-2xl border border-gray-200 dark:border-gray-700 py-2 z-50 transition-colors duration-300">
                      <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <div className="flex items-center space-x-3">
                          <div className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white font-bold shadow-lg">
                            {getInitials(currentUser)}
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">
                              {currentUser.fullName}
                            </p>
                            <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                              {currentUser.email}
                            </p>
                          </div>
                        </div>
                      </div>

                      <div className="py-1">
                        <Link
                          to="/users"
                          onClick={() => setShowUserDropdown(false)}
                          className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center space-x-2"
                        >
                          <svg
                            className="w-4 h-4"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={2}
                              d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                            />
                          </svg>
                          <span>My Profile</span>
                        </Link>
                        <button
                          onClick={handleSwitchAccount}
                          className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center space-x-2"
                        >
                          <svg
                            className="w-4 h-4"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={2}
                              d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                            />
                          </svg>
                          <span>Switch Account</span>
                        </button>
                        <button
                          onClick={handleLogout}
                          className="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex items-center space-x-2"
                        >
                          <svg
                            className="w-4 h-4"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={2}
                              d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                            />
                          </svg>
                          <span>Logout</span>
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <button
                  onClick={handleSwitchAccount}
                  className="text-gray-700 dark:text-white bg-gray-100 hover:bg-gray-200 dark:bg-white/10 dark:hover:bg-white/20 px-3 py-2 rounded-lg transition-all duration-200 font-medium ml-2 flex items-center space-x-2"
                >
                  <svg
                    className="w-5 h-5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                    />
                  </svg>
                  <span>Log In</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </nav>

      {/* Mobile Bottom Navigation */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white dark:bg-gradient-to-r dark:from-gray-800 dark:via-gray-900 dark:to-gray-800 border-t border-gray-200 dark:border-gray-700 shadow-2xl z-40 transition-colors duration-300">
        <div className="flex justify-around items-center h-16 px-1">
          <Link
            to="/"
            className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
          >
            <span className="text-xl">🏠</span>
            <span className="text-xs font-medium">Home</span>
          </Link>

          <Link
            to="/products"
            className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
          >
            <span className="text-xl">📦</span>
            <span className="text-xs font-medium">Shop</span>
          </Link>

          <Link
            to="/chat"
            className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
          >
            <span className="text-xl">💬</span>
            <span className="text-xs font-medium">Chat</span>
          </Link>

          <button
            onClick={() => setShowCartModal(true)}
            className={`flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 relative flex-1 ${cartPulse ? 'animate-cart-pulse' : ''}`}
          >
            <span className="text-xl">🛒</span>
            <span className="text-xs font-medium">Cart</span>
            {cartCount > 0 && (
              <span className="absolute top-0 right-1 bg-red-500 text-white text-xs font-bold rounded-full h-5 w-5 flex items-center justify-center">
                {cartCount}
              </span>
            )}
          </button>

          <Link
            to="/orders"
            className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
          >
            <span className="text-xl">📋</span>
            <span className="text-xs font-medium">Orders</span>
          </Link>

          <button
            onClick={toggleDarkMode}
            className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
          >
            <span className="text-xl">{darkMode ? '☀️' : '🌙'}</span>
            <span className="text-xs font-medium">Theme</span>
          </button>

          {currentUser ? (
            <button
              onClick={() => navigate('/users')}
              className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1 relative"
            >
              <div className="w-6 h-6 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white font-bold text-xs shadow-lg ring-2 ring-gray-200 dark:ring-white/30">
                {getInitials(currentUser)}
              </div>
              <span className="text-xs font-medium">Profile</span>
            </button>
          ) : (
            <button
              onClick={handleSwitchAccount}
              className="flex flex-col items-center justify-center text-gray-700 dark:text-white hover:bg-gray-100 dark:hover:bg-white/10 px-2 py-1 rounded-lg transition-all duration-200 flex-1"
            >
              <span className="text-xl">👤</span>
              <span className="text-xs font-medium">Login</span>
            </button>
          )}
        </div>

        {/* Mobile User Dropdown - Above bottom nav */}
        {showUserDropdown && currentUser && (
          <div className="absolute bottom-16 right-2 w-64 bg-white dark:bg-gray-800 rounded-lg shadow-2xl border border-gray-200 dark:border-gray-700 py-2 mb-2 transition-colors duration-300">
            <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center space-x-3">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white font-bold text-lg">
                  {getInitials(currentUser)}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">
                    {currentUser.fullName}
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                    {currentUser.email}
                  </p>
                </div>
              </div>
            </div>

            <div className="py-1">
              <Link
                to="/users"
                onClick={() => setShowUserDropdown(false)}
                className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center space-x-2"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
                <span>My Profile</span>
              </Link>
              <button
                onClick={handleSwitchAccount}
                className="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center space-x-2"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                  />
                </svg>
                <span>Switch Account</span>
              </button>
              <button
                onClick={handleLogout}
                className="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex items-center space-x-2"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                  />
                </svg>
                <span>Logout</span>
              </button>
            </div>
          </div>
        )}
      </nav>
    </>
  );
};
