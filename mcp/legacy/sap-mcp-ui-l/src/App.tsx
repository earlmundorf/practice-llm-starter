import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { useState } from 'react';
import { Header } from './components/Header';
import { Home } from './pages/Home';
import { Products } from './pages/Products';
import { Users } from './pages/Users';
import { Orders } from './pages/Orders';
import { Checkout } from './pages/Checkout';
import { ProductDetail } from './pages/ProductDetail';
import { OrderConfirmation } from './pages/OrderConfirmation';
import { Chat } from './pages/Chat';
import { Architecture } from './pages/Architecture';
import { UserPicker } from './components/UserPicker';
import { auth } from './services/api';
import { DarkModeProvider } from './contexts/DarkModeContext';
import type { User } from './types';
import './App.css';

export const App = () => {
  const [showInitialUserPicker, setShowInitialUserPicker] = useState(
    () => !auth.isLoggedIn()
  );

  const handleInitialUserSelected = (_user: User) => {
    setShowInitialUserPicker(false);
  };

  const handleCancelUserPicker = () => {
    if (auth.isLoggedIn()) {
      setShowInitialUserPicker(false);
    }
  };

  return (
    <DarkModeProvider>
      <Router>
        {showInitialUserPicker && (
          <UserPicker
            onUserSelected={handleInitialUserSelected}
            onCancel={
              auth.isLoggedIn() ? handleCancelUserPicker : null
            }
          />
        )}

        <div className="flex flex-col h-screen bg-white dark:bg-gray-900 transition-colors duration-300">
          <Header />
          <div className="flex-1 overflow-auto pb-16 md:pb-0">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/products" element={<Products />} />
              <Route path="/products/:productId" element={<ProductDetail />} />
              <Route path="/users" element={<Users />} />
              <Route path="/orders" element={<Orders />} />
              <Route path="/orders/:orderId" element={<OrderConfirmation />} />
              <Route path="/checkout" element={<Checkout />} />
              <Route path="/order-confirmation" element={<OrderConfirmation />} />
              <Route path="/chat" element={<Chat />} />
              <Route path="/architecture" element={<Architecture />} />
            </Routes>
          </div>
          <footer className="hidden md:block text-center py-3 text-xs text-gray-400 dark:text-gray-600">
            Powered by SAP Commerce
          </footer>
        </div>
      </Router>
    </DarkModeProvider>
  );
};
