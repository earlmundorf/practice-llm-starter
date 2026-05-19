import type {
  AuthToken,
  Cart,
  CartModification,
  DeliveryMode,
  Order,
  OrderHistoryResult,
  PaymentDetails,
  Product,
  ProductSearchResult,
  User,
  Address,
} from '../types';

// ==========================================================================
// OCC API Client
//
// Single service layer for all SAP Commerce OCC REST calls.
// See docs/endpoint-mapping.md for the full API contract.
// ==========================================================================

const BASE_URL = import.meta.env.VITE_API_URL || '/occ/v2/electronics';
const AUTH_URL = '/authorizationserver/oauth/token';
const CLIENT_ID = 'trusted_client';
const CLIENT_SECRET = 'secret';

// --- Core fetch wrapper ---

const apiFetch = async <T>(path: string, options: RequestInit = {}): Promise<T> => {
  const token = localStorage.getItem('access_token');
  const defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { ...defaultHeaders, ...(options.headers as Record<string, string>) },
  });

  if (response.status === 401) {
    auth.logout();
    window.dispatchEvent(new Event('authExpired'));
    throw new Error('Session expired — please log in again');
  }

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`API error ${response.status}: ${errorBody}`);
  }

  // Some OCC endpoints return empty bodies (DELETE, etc.)
  const text = await response.text();
  return text ? JSON.parse(text) : ({} as T);
};

// --- Auth ---

export const auth = {
  login: async (username: string, password: string): Promise<User> => {
    const body = new URLSearchParams({
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username,
      password,
    });

    const response = await fetch(AUTH_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });

    if (!response.ok) {
      throw new Error('Invalid email or password');
    }

    const token: AuthToken = await response.json();
    localStorage.setItem('access_token', token.access_token);
    localStorage.setItem('token_expires_at', String(Date.now() + token.expires_in * 1000));
    window.dispatchEvent(new Event('authChanged'));

    return apiFetch<User>('/users/current?fields=FULL');
  },

  logout: () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('token_expires_at');
    cartCode = null;
    window.dispatchEvent(new Event('authChanged'));
  },

  isLoggedIn: (): boolean => {
    const token = localStorage.getItem('access_token');
    const expiresAt = localStorage.getItem('token_expires_at');
    if (!token || !expiresAt) return false;
    return Date.now() < Number(expiresAt);
  },

  getUser: (): Promise<User> => apiFetch<User>('/users/current?fields=FULL'),
};

// --- Cart ---

let cartCode: string | null = null;

const ensureCart = async (): Promise<string> => {
  if (!cartCode) {
    const cart = await apiFetch<Cart>('/users/current/carts', { method: 'POST' });
    cartCode = cart.code;
  }
  return cartCode;
};

export const resetCart = () => {
  cartCode = null;
};

// --- API Methods ---

export const api = {
  auth,

  // Products
  searchProducts: (params: {
    query?: string;
    sort?: string;
    page?: number;
    pageSize?: number;
  }): Promise<ProductSearchResult> => {
    const q = encodeURIComponent(params.query || '');
    const sort = params.sort || 'relevance';
    const page = params.page ?? 0;
    const pageSize = params.pageSize ?? 20;
    return apiFetch<ProductSearchResult>(
      `/products/search?query=${q}:${sort}&currentPage=${page}&pageSize=${pageSize}&fields=FULL`,
    );
  },

  getProduct: (code: string): Promise<Product> =>
    apiFetch<Product>(`/products/${encodeURIComponent(code)}?fields=FULL`),

  // Cart
  getCart: async (): Promise<Cart> => {
    const code = await ensureCart();
    return apiFetch<Cart>(`/users/current/carts/${code}?fields=FULL`);
  },

  addToCart: async (productCode: string, quantity: number): Promise<CartModification> => {
    const code = await ensureCart();
    const result = await apiFetch<CartModification>(
      `/users/current/carts/${code}/entries?fields=FULL`,
      {
        method: 'POST',
        body: JSON.stringify({ product: { code: productCode }, quantity }),
      },
    );
    window.dispatchEvent(new Event('cartUpdated'));
    return result;
  },

  updateCartEntry: async (entryNumber: number, quantity: number): Promise<CartModification> => {
    const code = await ensureCart();
    const result = await apiFetch<CartModification>(
      `/users/current/carts/${code}/entries/${entryNumber}?fields=FULL`,
      {
        method: 'PATCH',
        body: JSON.stringify({ quantity }),
      },
    );
    window.dispatchEvent(new Event('cartUpdated'));
    return result;
  },

  removeCartEntry: async (entryNumber: number): Promise<void> => {
    const code = await ensureCart();
    await apiFetch<Record<string, never>>(
      `/users/current/carts/${code}/entries/${entryNumber}`,
      { method: 'DELETE' },
    );
    window.dispatchEvent(new Event('cartUpdated'));
  },

  // Checkout
  setDeliveryAddress: async (address: Omit<Address, 'id'>): Promise<Address> => {
    const code = await ensureCart();
    return apiFetch<Address>(
      `/users/current/carts/${code}/addresses/delivery?fields=FULL`,
      {
        method: 'POST',
        body: JSON.stringify(address),
      },
    );
  },

  getDeliveryModes: async (): Promise<DeliveryMode[]> => {
    const code = await ensureCart();
    const result = await apiFetch<{ deliveryModes: DeliveryMode[] }>(
      `/users/current/carts/${code}/deliverymodes?fields=FULL`,
    );
    return result.deliveryModes;
  },

  setDeliveryMode: async (deliveryModeId: string): Promise<void> => {
    const code = await ensureCart();
    await apiFetch<Record<string, never>>(
      `/users/current/carts/${code}/deliverymode?deliveryModeId=${encodeURIComponent(deliveryModeId)}`,
      { method: 'PUT' },
    );
  },

  setPaymentDetails: async (payment: Omit<PaymentDetails, 'id'>): Promise<PaymentDetails> => {
    const code = await ensureCart();
    return apiFetch<PaymentDetails>(
      `/users/current/carts/${code}/paymentdetails?fields=FULL`,
      {
        method: 'POST',
        body: JSON.stringify(payment),
      },
    );
  },

  placeOrder: async (): Promise<Order> => {
    const code = await ensureCart();
    const order = await apiFetch<Order>(
      `/users/current/orders?cartId=${code}&fields=FULL`,
      { method: 'POST' },
    );
    cartCode = null; // Cart consumed by order
    window.dispatchEvent(new Event('cartUpdated'));
    return order;
  },

  // Orders
  getOrders: (params?: { page?: number; pageSize?: number }): Promise<OrderHistoryResult> => {
    const page = params?.page ?? 0;
    const pageSize = params?.pageSize ?? 20;
    return apiFetch<OrderHistoryResult>(
      `/users/current/orders?fields=FULL&pageSize=${pageSize}&currentPage=${page}`,
    );
  },

  getOrder: (orderCode: string): Promise<Order> =>
    apiFetch<Order>(`/users/current/orders/${encodeURIComponent(orderCode)}?fields=FULL`),
};
