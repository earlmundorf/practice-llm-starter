import { Link } from 'react-router-dom';

export const Home = () => {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-slate-100 dark:from-gray-900 dark:via-slate-900 dark:to-gray-900">
      {/* Hero Section with Background Pattern */}
      <div className="relative overflow-hidden">
        {/* Animated background shapes - more subtle */}
        <div className="absolute top-20 left-10 w-72 h-72 bg-blue-300 dark:bg-blue-700 rounded-full mix-blend-multiply dark:mix-blend-screen filter blur-xl opacity-30 dark:opacity-20 animate-blob"></div>
        <div className="absolute top-40 right-10 w-72 h-72 bg-cyan-300 dark:bg-cyan-700 rounded-full mix-blend-multiply dark:mix-blend-screen filter blur-xl opacity-30 dark:opacity-20 animate-blob animation-delay-2000"></div>
        <div className="absolute -bottom-32 left-40 w-72 h-72 bg-slate-300 dark:bg-slate-700 rounded-full mix-blend-multiply dark:mix-blend-screen filter blur-xl opacity-30 dark:opacity-20 animate-blob animation-delay-4000"></div>

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
          <div className="text-center">
            <div className="text-8xl mb-8 animate-bounce-three drop-shadow-2xl">
              🛒
            </div>
            <h1 className="text-6xl md:text-7xl font-extrabold mb-2">
              <span className="bg-gradient-to-r from-blue-600 to-cyan-600 dark:from-blue-400 dark:to-cyan-400 bg-clip-text text-transparent">
                ThinkShop
              </span>
            </h1>
            <p className="text-lg md:text-xl text-gray-400 dark:text-gray-500 font-medium mb-4">
              for SAP Commerce
            </p>
            <p className="text-2xl md:text-3xl font-semibold text-gray-700 dark:text-gray-200 mb-4">
              Premium Electronics Store
            </p>
            <p className="text-lg md:text-xl text-gray-600 dark:text-gray-300 mb-12 max-w-2xl mx-auto">
              Shop smarter with our curated collection. Find products, manage
              your cart, and checkout effortlessly.
            </p>

            <div className="flex flex-col sm:flex-row gap-6 justify-center items-center">
              <Link
                to="/products"
                className="group relative bg-gradient-to-r from-blue-600 to-cyan-600 dark:from-blue-500 dark:to-cyan-500 text-white px-10 py-5 rounded-2xl font-bold text-xl shadow-2xl hover:shadow-blue-500/50 transition-all duration-300 transform hover:scale-105"
              >
                <span className="flex items-center gap-2">🛍️ Browse Products</span>
              </Link>
              <Link
                to="/orders"
                className="relative bg-white dark:bg-gray-800 text-gray-900 dark:text-white px-10 py-5 rounded-2xl font-bold text-xl shadow-2xl border-4 border-blue-300 dark:border-blue-500 hover:border-blue-500 dark:hover:border-blue-400 hover:shadow-blue-300/50 transition-all duration-300 transform hover:scale-105"
              >
                <span className="flex items-center gap-2">📋 My Orders</span>
              </Link>
              <Link
                to="/chat"
                className="relative bg-gradient-to-r from-purple-600 to-pink-600 dark:from-purple-500 dark:to-pink-500 text-white px-10 py-5 rounded-2xl font-bold text-xl shadow-2xl hover:shadow-purple-500/50 transition-all duration-300 transform hover:scale-105"
              >
                <span className="flex items-center gap-2">💬 AI Assistant</span>
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Features Section */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <h2 className="text-4xl md:text-5xl font-extrabold text-center mb-4 text-gray-900 dark:text-white">
          Why Shop With Us?
        </h2>
        <p className="text-center text-gray-600 dark:text-gray-400 mb-16 text-lg">
          Experience the future of online shopping
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Feature 1 */}
          <div className="group relative bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8 border-2 border-gray-200 dark:border-gray-700 hover:border-blue-400 dark:hover:border-blue-500 hover:shadow-2xl hover:shadow-blue-200/50 dark:hover:shadow-blue-900/50 transition-all duration-300 transform hover:-translate-y-2">
            <div className="absolute top-4 right-4 text-6xl opacity-10">🔒</div>
            <div className="relative">
              <div className="text-6xl mb-6 transform group-hover:scale-110 transition-transform duration-300">
                🔒
              </div>
              <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-4 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                Secure Commerce
              </h3>
              <p className="text-gray-700 dark:text-gray-300 leading-relaxed">
                Powered by SAP Commerce Cloud with enterprise-grade security,
                OAuth2 authentication, and trusted payment processing.
              </p>
            </div>
          </div>

          {/* Feature 2 */}
          <div className="group relative bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8 border-2 border-gray-200 dark:border-gray-700 hover:border-cyan-400 dark:hover:border-cyan-500 hover:shadow-2xl hover:shadow-cyan-200/50 dark:hover:shadow-cyan-900/50 transition-all duration-300 transform hover:-translate-y-2">
            <div className="absolute top-4 right-4 text-6xl opacity-10">⚡</div>
            <div className="relative">
              <div className="text-6xl mb-6 transform group-hover:scale-110 transition-transform duration-300">
                ⚡
              </div>
              <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-4 group-hover:text-cyan-600 dark:group-hover:text-cyan-400 transition-colors">
                Lightning Fast Checkout
              </h3>
              <p className="text-gray-700 dark:text-gray-300 leading-relaxed">
                Streamlined checkout in seconds with seamless cart sync across
                all your devices. Your shopping, simplified.
              </p>
            </div>
          </div>

          {/* Feature 3 */}
          <div className="group relative bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8 border-2 border-gray-200 dark:border-gray-700 hover:border-slate-400 dark:hover:border-slate-500 hover:shadow-2xl hover:shadow-slate-200/50 dark:hover:shadow-slate-900/50 transition-all duration-300 transform hover:-translate-y-2">
            <div className="absolute top-4 right-4 text-6xl opacity-10">📦</div>
            <div className="relative">
              <div className="text-6xl mb-6 transform group-hover:scale-110 transition-transform duration-300">
                📦
              </div>
              <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-4 group-hover:text-slate-600 dark:group-hover:text-slate-400 transition-colors">
                Premium Electronics
              </h3>
              <p className="text-gray-700 dark:text-gray-300 leading-relaxed">
                Curated collection of high-quality laptops, smartphones, and
                accessories from the world's most trusted brands.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Stats */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-center">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 border border-gray-200 dark:border-gray-700">
            <div className="text-4xl mb-2">💻</div>
            <div className="text-3xl font-bold text-gray-900 dark:text-white mb-1">
              10+
            </div>
            <div className="text-gray-600 dark:text-gray-300">Products</div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 border border-gray-200 dark:border-gray-700">
            <div className="text-4xl mb-2">⭐</div>
            <div className="text-3xl font-bold text-gray-900 dark:text-white mb-1">
              4.9
            </div>
            <div className="text-gray-600 dark:text-gray-300">Rating</div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 border border-gray-200 dark:border-gray-700">
            <div className="text-4xl mb-2">🚚</div>
            <div className="text-3xl font-bold text-gray-900 dark:text-white mb-1">
              Free
            </div>
            <div className="text-gray-600 dark:text-gray-300">Shipping</div>
          </div>
        </div>
      </div>
    </div>
  );
};
