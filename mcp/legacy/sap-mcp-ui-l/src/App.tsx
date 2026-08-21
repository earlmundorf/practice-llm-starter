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
import { HelpCenter } from './pages/HelpCenter';
import { HelpDetail } from './pages/HelpDetail';
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
              <Route path="/help" element={<HelpCenter />} />
              <Route path="/help/:uid" element={<HelpDetail />} />
            </Routes>
          </div>
          <footer className="text-center pt-3 pb-20 md:py-3 px-4 text-xs text-gray-500 dark:text-gray-400">
            <span className="whitespace-nowrap">Produced by the Capgemini SAP CX team</span>{' '}
            <span aria-hidden="true">·</span>{' '}
            <span className="whitespace-nowrap">Powered by SAP Commerce</span>
          </footer>
        </div>
      </Router>
    </DarkModeProvider>
  );
};
