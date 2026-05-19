import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { CartProvider } from './contexts/CartContext';
import { DarkModeProvider } from './contexts/DarkModeContext';
import { Layout } from './components/Layout';

export const App = () => {
  return (
    <BrowserRouter>
      <DarkModeProvider>
        <AuthProvider>
          <CartProvider>
            <Layout>
              <Routes>
                <Route path="/" element={<div>Home — SAP Commerce Storefront</div>} />
                <Route path="/products" element={<div>Products</div>} />
                <Route path="/products/:code" element={<div>Product Detail</div>} />
                <Route path="/login" element={<div>Login</div>} />
                <Route path="/checkout" element={<div>Checkout</div>} />
                <Route path="/order-confirmation/:orderCode" element={<div>Order Confirmation</div>} />
                <Route path="/orders" element={<div>Orders</div>} />
              </Routes>
            </Layout>
          </CartProvider>
        </AuthProvider>
      </DarkModeProvider>
    </BrowserRouter>
  );
};
