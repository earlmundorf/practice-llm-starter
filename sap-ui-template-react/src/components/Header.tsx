import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useCart } from '../contexts/CartContext';
import { useDarkMode } from '../contexts/DarkModeContext';

export const Header = () => {
  const { user, isLoggedIn, logout } = useAuth();
  const { itemCount } = useCart();
  const { isDark, toggle } = useDarkMode();

  return (
    <header className="sticky top-0 z-40 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
      <nav className="flex items-center justify-between gap-4 px-4 py-3 max-w-7xl mx-auto">
        <Link to="/" className="text-lg font-bold text-gray-900 dark:text-white">
          ThinkShop
        </Link>

        <div className="flex items-center gap-6">
          <Link
            to="/products"
            className="text-sm text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white"
          >
            Products
          </Link>

          {isLoggedIn && (
            <Link
              to="/orders"
              className="text-sm text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white"
            >
              Orders
            </Link>
          )}

          <button
            onClick={toggle}
            className="p-2 text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white"
            aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {isDark ? '☀️' : '🌙'}
          </button>

          {/* Cart badge — TODO: open CartModal drawer on click */}
          <Link
            to="/checkout"
            className="relative p-2 text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white"
            aria-label={`Cart with ${itemCount} items`}
          >
            🛒
            {itemCount > 0 && (
              <span className="absolute -top-1 -right-1 flex items-center justify-center w-5 h-5 text-xs font-bold text-white bg-blue-600 rounded-full">
                {itemCount}
              </span>
            )}
          </Link>

          {isLoggedIn ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-700 dark:text-gray-300">{user?.name}</span>
              <button
                onClick={logout}
                className="text-sm text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white"
              >
                Log Out
              </button>
            </div>
          ) : (
            <Link
              to="/login"
              className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
            >
              Log In
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
};
