import type {
  User,
  Product,
  CartItem,
  Order,
  OrderItem,
  Address,
  PaymentFormData,
  SearchResult,
  Facet,
  AppliedVoucher,
  AppliedPromotion,
  VisualSearchResult,
  KnowledgeEntry,
  KnowledgeSearchResult,
} from '../types';

// OCC base URL from environment (e.g., /occ/v2/electronics)
const OCC_BASE = import.meta.env.VITE_API_URL || '/occ/v2/electronics';

// ============================================
// Auth Module — OAuth2 Resource Owner Password
// ============================================

const TOKEN_KEY = 'occ_access_token';
const REFRESH_TOKEN_KEY = 'occ_refresh_token';
const USER_EMAIL_KEY = 'occ_user_email';
const CART_CODE_KEY = 'occ_cart_code';

export const auth = {
  login: async (email: string, password: string): Promise<void> => {
    const body = new URLSearchParams({
      grant_type: 'password',
      client_id: 'trusted_client',
      client_secret: 'secret',
      username: email,
      password: password,
    });

    const response = await fetch('/authorizationserver/oauth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error_description || 'Login failed');
    }

    const data = await response.json();
    localStorage.setItem(TOKEN_KEY, data.access_token);
    if (data.refresh_token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, data.refresh_token);
    }
    localStorage.setItem(USER_EMAIL_KEY, email);
    // Cart is persisted per-user on the backend (SAP Commerce default behavior).
    // Clear the stored cart code so ensureCart picks up this user's existing cart.
    localStorage.removeItem(CART_CODE_KEY);
    window.dispatchEvent(new Event('authChanged'));
  },

  logout: (): void => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_EMAIL_KEY);
    localStorage.removeItem(CART_CODE_KEY);
    window.dispatchEvent(new Event('authChanged'));
  },

  getToken: (): string | null => {
    return localStorage.getItem(TOKEN_KEY);
  },

  getUserEmail: (): string | null => {
    return localStorage.getItem(USER_EMAIL_KEY);
  },

  isLoggedIn: (): boolean => {
    return !!localStorage.getItem(TOKEN_KEY);
  },
};

// Authenticated fetch wrapper
// eslint-disable-next-line no-undef
const authFetch = async (url: string, options: RequestInit = {}): Promise<Response> => {
  const token = auth.getToken();
  if (!token) throw new Error('Not authenticated');

  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(url, { ...options, headers, cache: 'no-store' });

  // If 401, token may be expired
  if (response.status === 401) {
    auth.logout();
    throw new Error('Session expired. Please log in again.');
  }

  return response;
};

// ============================================
// OCC Response Mapping Helpers
// ============================================

/* eslint-disable @typescript-eslint/no-explicit-any */

const mapOccProduct = (occ: any): Product => ({
  id: occ.code,
  name: occ.name || occ.code,
  description: occ.description || occ.summary || '',
  price: occ.price?.value ?? 0,
  stockQuantity: occ.stock?.stockLevel
    ?? (occ.stock?.stockLevelStatus === 'inStock' ? 1000 : 0),
  imageUrl: occ.images?.find((img: any) => img.format === 'product')?.url
    || occ.images?.[0]?.url
    || undefined,
});

// OCC's /products/search, /carts, and /orders converters don't populate
// `images` — that lives only on /products/{code}. Fetch image URLs for a
// set of product codes in parallel, deduped, so repeats only hit OCC once.
const fetchImageUrls = async (codes: string[]): Promise<Map<string, string | undefined>> => {
  const unique = [...new Set(codes.filter(Boolean))];
  const entries = await Promise.all(unique.map(async (code) => {
    try {
      const r = await fetch(`${OCC_BASE}/products/${code}?fields=code,images(FULL)`);
      if (!r.ok) return [code, undefined] as const;
      const j = await r.json();
      const url = j.images?.find((i: { format?: string; url?: string }) => i.format === 'product')?.url
        || j.images?.[0]?.url;
      return [code, url] as const;
    } catch { return [code, undefined] as const; }
  }));
  return new Map(entries);
};

const enrichWithImages = async (products: Product[]): Promise<Product[]> => {
  const missing = products.filter(p => !p.imageUrl).map(p => p.id);
  if (missing.length === 0) return products;
  const urlByCode = await fetchImageUrls(missing);
  return products.map(p => p.imageUrl ? p : { ...p, imageUrl: urlByCode.get(p.id) });
};

// In-place fill of OrderItem.imageUrl across one or more orders. Shares
// a single dedup pass so repeated product codes across orders fetch once.
const enrichOrderItemImages = async (orders: Order[]): Promise<void> => {
  const missing = orders.flatMap(o => o.items).filter(i => !i.imageUrl).map(i => i.productId);
  if (missing.length === 0) return;
  const urlByCode = await fetchImageUrls(missing);
  orders.forEach(o => o.items.forEach(i => {
    if (!i.imageUrl) i.imageUrl = urlByCode.get(i.productId);
  }));
};

const mapOccCartEntry = (entry: any): CartItem => {
  const baseUnit = entry.basePrice?.value ?? 0;
  const quantity = entry.quantity || 0;
  const expectedFullTotal = baseUnit * quantity;
  const entryTotalPrice = entry.totalPrice?.value;

  let discountValue = 0;
  if (Array.isArray(entry.discountValues) && entry.discountValues.length > 0) {
    discountValue = entry.discountValues.reduce(
      (sum: number, d: any) => sum + (d?.value ?? d?.appliedValue ?? 0),
      0,
    );
  }
  // Fallback: derive from the difference between line total and base * qty.
  // Some OCC variants don't expose discountValues but do compute totalPrice post-discount.
  if (discountValue === 0 && entryTotalPrice != null && entryTotalPrice < expectedFullTotal) {
    discountValue = expectedFullTotal - entryTotalPrice;
  }

  return {
    productId: entry.product?.code || '',
    productName: entry.product?.name || '',
    quantity,
    price: baseUnit,
    entryNumber: entry.entryNumber,
    discountValue,
    // Minimal Product shell so CartModal/Checkout can read item.product.imageUrl
    // once enrichWithImages fills it in. OCC's cart converter doesn't include
    // images on entry.product, so imageUrl starts undefined and gets enriched.
    product: entry.product?.code ? {
      id: entry.product.code,
      name: entry.product.name || entry.product.code,
      description: '',
      price: baseUnit,
      stockQuantity: 0,
      imageUrl: undefined,
    } : undefined,
  };
};

const mapOccOrderEntry = (entry: any): OrderItem => ({
  productId: entry.product?.code || '',
  productName: entry.product?.name || '',
  description: entry.product?.description || entry.product?.summary || undefined,
  imageUrl: entry.product?.images?.find((img: any) => img.format === 'product')?.url
    || entry.product?.images?.[0]?.url
    || undefined,
  quantity: entry.quantity || 0,
  price: entry.basePrice?.value ?? entry.totalPrice?.value ?? 0,
});

const mapOccStatusDisplay = (statusDisplay: string | undefined): Order['status'] => {
  switch (statusDisplay?.toLowerCase()) {
    case 'completed': return 'COMPLETED';
    case 'cancelled': case 'canceled': return 'CANCELLED';
    case 'processing': case 'created': return 'CREATED';
    default: return 'CREATED';
  }
};

const mapOccOrder = (occ: any): Order => ({
  id: occ.code,
  userId: occ.user?.uid || '',
  items: (occ.entries || []).map(mapOccOrderEntry),
  totalAmount: occ.totalPrice?.value ?? occ.total?.value ?? 0,
  subTotal: occ.subTotal?.value,
  deliveryCost: occ.deliveryCost?.value,
  totalTax: occ.totalTax?.value,
  status: mapOccStatusDisplay(occ.statusDisplay || occ.status),
  createdAt: occ.created || occ.placed || new Date().toISOString(),
});

const mapOccUser = (occ: any): User => ({
  id: occ.uid,
  username: occ.uid,
  email: occ.uid,
  fullName: [occ.firstName, occ.lastName].filter(Boolean).join(' ') || occ.name || occ.uid,
});

const mapOccAddress = (occ: any): Address => ({
  id: occ.id || '',
  firstName: occ.firstName || '',
  lastName: occ.lastName || '',
  line1: occ.line1 || '',
  line2: occ.line2 || undefined,
  town: occ.town || '',
  postalCode: occ.postalCode || '',
  country: { isocode: occ.country?.isocode || 'US', name: occ.country?.name },
  defaultAddress: occ.defaultAddress || false,
});

const mapKnowledgeEntry = (raw: any): KnowledgeEntry => ({
  uid: raw.uid,
  category: raw.category,
  title: raw.title,
  summary: raw.summary,
  body: raw.body,
  tags: raw.tags ?? [],
  priority: raw.priority,
  imageUrl: raw.imageUrl,
});

/* eslint-enable @typescript-eslint/no-explicit-any */

// ============================================
// Cart Code Management
// ============================================

const getCartCode = (): string | null => localStorage.getItem(CART_CODE_KEY);
const setCartCode = (code: string): void => localStorage.setItem(CART_CODE_KEY, code);

const ensureCart = async (): Promise<string> => {
  const existing = getCartCode();
  if (existing) {
    // Verify cart still exists
    try {
      const res = await authFetch(`${OCC_BASE}/users/current/carts/${existing}?fields=DEFAULT`);
      if (res.ok) return existing;
    } catch {
      // Cart doesn't exist, fall through to find/create
    }
  }

  // Create a fresh cart — don't reuse old server-side carts which may be stale
  const res = await authFetch(`${OCC_BASE}/users/current/carts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok) {
    throw new Error('Failed to create cart');
  }
  const data = await res.json();
  const code = data.code;
  setCartCode(code);
  return code;
};

export const clearStoredCartCode = (): void => localStorage.removeItem(CART_CODE_KEY);
export const getStoredCartCode = (): string | null => getCartCode();
export const storeCartCode = (code: string): void => setCartCode(code);

// ============================================
// API Methods
// ============================================

export const api = {
  // --- Products ---

  searchProducts: async (
    query = '',
    sort = 'relevance',
    page = 0,
    pageSize = 12,
    facetFilters: Record<string, string[]> = {},
  ): Promise<SearchResult> => {
    // Build OCC query string: "searchTerm:sort:facet1:val1:facet2:val2"
    let queryParam = query ? `${query}:${sort}` : `:${sort}`;
    for (const [facetCode, values] of Object.entries(facetFilters)) {
      for (const value of values) {
        queryParam += `:${facetCode}:${value}`;
      }
    }
    const params = new URLSearchParams({
      query: queryParam,
      fields: 'FULL',
      currentPage: String(page),
      pageSize: String(pageSize),
    });
    const res = await fetch(`${OCC_BASE}/products/search?${params}`, { cache: 'no-store' });
    if (!res.ok) throw new Error('Failed to search products');
    const data = await res.json();

    /* eslint-disable @typescript-eslint/no-explicit-any */
    const facets: Facet[] = (data.facets || [])
      .filter((f: any) => f.visible !== false)
      .map((f: any) => ({
        code: f.code || f.name,
        name: f.name || f.code,
        visible: f.visible ?? true,
        multiSelect: f.multiSelect ?? false,
        values: (f.values || []).map((v: any) => ({
          code: v.code || v.name,
          name: v.name || v.code,
          count: v.count ?? 0,
          selected: v.selected ?? false,
          query: v.query?.query?.value || '',
        })),
      }));
    /* eslint-enable @typescript-eslint/no-explicit-any */

    // OCC's /products/search converter doesn't populate images even with
    // fields=FULL — that lives only on /products/{code}. Enrich each search
    // hit in parallel with a tiny image-only fetch so listing cards render.
    const baseProducts: Product[] = (data.products || []).map(mapOccProduct);
    const enriched = await enrichWithImages(baseProducts);

    return {
      products: enriched,
      pagination: {
        currentPage: data.pagination?.currentPage ?? 0,
        pageSize: data.pagination?.pageSize ?? pageSize,
        totalResults: data.pagination?.totalResults ?? 0,
        totalPages: data.pagination?.totalPages ?? 0,
      },
      sorts: (data.sorts || []).map((s: any) => ({ // eslint-disable-line @typescript-eslint/no-explicit-any
        code: s.code,
        name: s.name || s.code,
        selected: s.selected || false,
      })),
      facets,
    };
  },

  getProducts: async (): Promise<Product[]> => {
    const result = await api.searchProducts();
    return result.products;
  },

  getProduct: async (code: string): Promise<Product> => {
    const res = await fetch(
      `${OCC_BASE}/products/${code}?fields=code,name,description,summary,price(FULL),stock(FULL),images(FULL)`,
      { cache: 'no-store' }
    );
    if (!res.ok) throw new Error('Product not found');
    const data = await res.json();
    return mapOccProduct(data);
  },

  // --- Users ---

  getUser: async (): Promise<User> => {
    const res = await authFetch(`${OCC_BASE}/users/current?fields=FULL`);
    if (!res.ok) throw new Error('Failed to fetch user');
    const data = await res.json();
    return mapOccUser(data);
  },

  createUser: async (userData: { username: string; email: string; fullName: string }): Promise<User> => {
    const nameParts = userData.fullName.split(' ');
    const firstName = nameParts[0] || '';
    const lastName = nameParts.slice(1).join(' ') || '';

    const res = await fetch(`${OCC_BASE}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        uid: userData.email,
        firstName,
        lastName,
        password: '1234',
        titleCode: 'mr',
      }),
    });

    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.errors?.[0]?.message || 'Failed to create user');
    }

    // Auto-login after registration
    await auth.login(userData.email, '1234');
    return api.getUser();
  },

  // --- Cart ---

  getCart: async (): Promise<{ items: CartItem[]; total: number; cartCode: string }> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}?fields=FULL`
    );
    if (!res.ok) throw new Error('Failed to fetch cart');
    const data = await res.json();
    const items = (data.entries || []).map(mapOccCartEntry) as CartItem[];
    // Enrich embedded products with images — cart OCC response omits them.
    const productsForEnrich = items
      .map(i => i.product)
      .filter((p): p is Product => Boolean(p));
    if (productsForEnrich.length > 0) {
      const enriched = await enrichWithImages(productsForEnrich);
      const byId = new Map(enriched.map(p => [p.id, p]));
      items.forEach(i => {
        if (i.product) i.product = byId.get(i.product.id) ?? i.product;
      });
    }
    return { items, total: data.totalPrice?.value ?? 0, cartCode };
  },

  addToCart: async (productId: string, quantity: number): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/entries`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ product: { code: productId }, quantity }),
      }
    );
    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.errors?.[0]?.message || 'Failed to add to cart');
    }
  },

  updateCartItem: async (entryNumber: number, quantity: number): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/entries/${entryNumber}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity }),
      }
    );
    if (!res.ok) throw new Error('Failed to update cart item');
  },

  removeFromCart: async (entryNumber: number): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/entries/${entryNumber}`,
      { method: 'DELETE' }
    );
    if (!res.ok) throw new Error('Failed to remove from cart');
  },

  clearCart: async (): Promise<void> => {
    const cartCode = getCartCode();
    if (cartCode) {
      await authFetch(`${OCC_BASE}/users/current/carts/${cartCode}`, {
        method: 'DELETE',
      }).catch(() => {});
    }
    localStorage.removeItem(CART_CODE_KEY);
  },

  // --- Orders ---

  getUserOrders: async (): Promise<Order[]> => {
    const res = await authFetch(
      `${OCC_BASE}/users/current/orders?fields=FULL&pageSize=20`
    );
    if (!res.ok) throw new Error('Failed to load orders');
    const data = await res.json();
    const orders: Order[] = (data.orders || []).map(mapOccOrder);
    await enrichOrderItemImages(orders);
    return orders;
  },

  getOrder: async (orderId: string): Promise<Order> => {
    const res = await authFetch(
      `${OCC_BASE}/users/current/orders/${orderId}?fields=FULL`
    );
    if (!res.ok) throw new Error('Order not found');
    const data = await res.json();
    const order = mapOccOrder(data);
    await enrichOrderItemImages([order]);
    return order;
  },

  // --- Addresses ---

  getAddresses: async (): Promise<Address[]> => {
    const res = await authFetch(`${OCC_BASE}/users/current/addresses?fields=FULL`);
    if (!res.ok) throw new Error('Failed to fetch addresses');
    const data = await res.json();
    return (data.addresses || []).map(mapOccAddress);
  },

  createAddress: async (address: Omit<Address, 'id'>): Promise<Address> => {
    const res = await authFetch(`${OCC_BASE}/users/current/addresses`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        firstName: address.firstName,
        lastName: address.lastName,
        line1: address.line1,
        line2: address.line2 || undefined,
        town: address.town,
        postalCode: address.postalCode,
        country: { isocode: address.country.isocode },
        defaultAddress: address.defaultAddress || false,
      }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.errors?.[0]?.message || 'Failed to create address');
    }
    // OCC may not return body on create — refetch addresses to get the new one
    const addresses = await api.getAddresses();
    return addresses[addresses.length - 1];
  },

  updateAddress: async (id: string, address: Partial<Address>): Promise<void> => {
    const res = await authFetch(`${OCC_BASE}/users/current/addresses/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        firstName: address.firstName,
        lastName: address.lastName,
        line1: address.line1,
        line2: address.line2 || undefined,
        town: address.town,
        postalCode: address.postalCode,
        country: address.country ? { isocode: address.country.isocode } : undefined,
        defaultAddress: address.defaultAddress,
      }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.errors?.[0]?.message || 'Failed to update address');
    }
  },

  deleteAddress: async (id: string): Promise<void> => {
    const res = await authFetch(`${OCC_BASE}/users/current/addresses/${id}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error('Failed to delete address');
  },

  // --- Delivery Modes ---

  getDeliveryModes: async (): Promise<{ code: string; name: string; cost: number }[]> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/deliverymodes`
    );
    if (!res.ok) return [];
    const data = await res.json();
    return (data.deliveryModes || []).map((m: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
      code: m.code,
      name: m.name || m.code,
      cost: m.deliveryCost?.value ?? 0,
    }));
  },

  setCartDeliveryAddress: async (address: Address): Promise<void> => {
    const cartCode = await ensureCart();

    // Always use a saved address ID to avoid creating duplicate addresses.
    // If no ID, save the address first, then reference it by ID.
    let addressId = address.id;
    if (!addressId) {
      const saved = await api.createAddress(address);
      addressId = saved.id;
    }

    // If the cart already has this address attached (matched by content), skip the PUT.
    // Avoids an unnecessary round-trip on every checkout mount AND sidesteps
    // JaloObjectNoLongerValidError when a stale PK reference can't be re-resolved by
    // the PUT-by-id path on long-running Hybris sessions.
    try {
      const cartRes = await authFetch(`${OCC_BASE}/users/current/carts/${cartCode}?fields=DEFAULT`);
      if (cartRes.ok) {
        const cart = await cartRes.json();
        const current = cart?.deliveryAddress;
        const sameAddress = current
          && current.line1 === address.line1
          && (current.line2 ?? '') === (address.line2 ?? '')
          && current.town === address.town
          && current.postalCode === address.postalCode
          && current.country?.isocode === address.country.isocode;
        if (sameAddress) return;
      }
    } catch { /* fall through to PUT */ }

    const putUrl = `${OCC_BASE}/users/current/carts/${cartCode}/addresses/delivery?addressId=${addressId}`;
    const res = await authFetch(putUrl, { method: 'PUT' });
    if (res.ok) return;

    const body = await res.text().catch(() => '');
    let errMsg = 'Failed to set delivery address';
    try {
      errMsg = JSON.parse(body)?.errors?.[0]?.message || errMsg;
    } catch { /* not json */ }
    throw new Error(errMsg);
  },

  setCartDeliveryMode: async (modeCode: string): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/deliverymode?deliveryModeId=${modeCode}`,
      { method: 'PUT' }
    );
    if (!res.ok) throw new Error('Failed to set delivery mode');
  },

  // --- Vouchers / Coupons ---

  applyVoucher: async (voucherCode: string): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/vouchers?voucherId=${encodeURIComponent(voucherCode)}`,
      { method: 'POST' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.errors?.[0]?.message || 'Invalid coupon code');
    }
  },

  removeVoucher: async (voucherCode: string): Promise<void> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/vouchers/${voucherCode}`,
      { method: 'DELETE' }
    );
    if (!res.ok) throw new Error('Failed to remove coupon');
  },

  getCartPromotions: async (): Promise<{
    appliedVouchers: AppliedVoucher[];
    appliedOrderPromotions: AppliedPromotion[];
    appliedProductPromotions: AppliedPromotion[];
    potentialOrderPromotions: AppliedPromotion[];
    potentialProductPromotions: AppliedPromotion[];
    totalDiscounts: number;
    totalPrice: number;
    subTotal: number;
    deliveryCost: number;
  }> => {
    const cartCode = await ensureCart();
    const res = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}?fields=FULL`
    );
    if (!res.ok) throw new Error('Failed to fetch cart details');
    const data = await res.json();

    /* eslint-disable @typescript-eslint/no-explicit-any */
    const appliedVouchers: AppliedVoucher[] = (data.appliedVouchers || []).map((v: any) => ({
      code: v.code || v.voucherCode,
      name: v.name,
      appliedValue: v.appliedValue?.value,
      freeShipping: v.freeShipping,
    }));

    const mapPromotions = (promos: any[]): AppliedPromotion[] =>
      (promos || []).map((p: any) => ({
        description: p.description || p.promotion?.description || p.promotion?.name || p.promotion?.code || '',
        promotionCode: p.promotion?.code,
        promotionType: p.promotion?.promotionType,
        consumedEntries: Array.isArray(p.consumedEntries)
          ? p.consumedEntries
              .map((c: any) => c?.orderEntryNumber)
              .filter((n: any) => typeof n === 'number')
          : undefined,
      }));

    // Rule-engine promos all share promotionType="Rule Based Promotion", so the only reliable
    // type signal is the discount math itself: product-level promos drop the consumed entry's
    // adjustedUnitPrice below the entry's basePrice; order- and shipping-level promos leave it
    // unchanged. Build an entryNumber → basePrice lookup and classify on that.
    const entryBasePrice = new Map<number, number>();
    (data.entries || []).forEach((e: any) => {
      if (typeof e.entryNumber === 'number') {
        entryBasePrice.set(e.entryNumber, e.basePrice?.value ?? 0);
      }
    });

    const isProductLevelPromo = (p: any): boolean => {
      if (!Array.isArray(p?.consumedEntries) || p.consumedEntries.length === 0) return false;
      return p.consumedEntries.some((c: any) => {
        const base = entryBasePrice.get(c?.orderEntryNumber);
        const adjusted = c?.adjustedUnitPrice;
        return typeof base === 'number'
          && typeof adjusted === 'number'
          && adjusted < base - 0.001;
      });
    };

    const rawAll: any[] = [
      ...(data.appliedOrderPromotions || []),
      ...(data.appliedProductPromotions || []),
    ];
    /* eslint-enable @typescript-eslint/no-explicit-any */

    return {
      appliedVouchers,
      appliedOrderPromotions: mapPromotions(rawAll.filter((p) => !isProductLevelPromo(p))),
      appliedProductPromotions: mapPromotions(rawAll.filter(isProductLevelPromo)),
      potentialOrderPromotions: mapPromotions(data.potentialOrderPromotions),
      potentialProductPromotions: mapPromotions(data.potentialProductPromotions),
      totalDiscounts: data.totalDiscounts?.value ?? 0,
      totalPrice: data.totalPrice?.value ?? 0,
      subTotal: data.subTotal?.value ?? 0,
      deliveryCost: data.deliveryCost?.value ?? 0,
    };
  },

  // --- Orders ---

  createOrder: async (address: Address, payment: PaymentFormData): Promise<Order> => {
    const cartCode = await ensureCart();

    // Address and delivery mode are already set on the cart by the checkout page.
    // Only set payment details and place the order here.

    // Step 1: Set payment details on the cart
    const payRes = await authFetch(
      `${OCC_BASE}/users/current/carts/${cartCode}/paymentdetails`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          accountHolderName: payment.accountHolderName,
          cardNumber: payment.cardNumber,
          cardType: { code: payment.cardType },
          expiryMonth: payment.expiryMonth,
          expiryYear: payment.expiryYear,
          billingAddress: {
            firstName: address.firstName,
            lastName: address.lastName,
            line1: address.line1,
            line2: address.line2,
            town: address.town,
            postalCode: address.postalCode,
            country: { isocode: address.country.isocode },
          },
        }),
      }
    );
    if (!payRes.ok) {
      const err = await payRes.json().catch(() => ({}));
      throw new Error(err.errors?.[0]?.message || 'Failed to set payment details');
    }

    // Step 2: Place order
    const res = await authFetch(
      `${OCC_BASE}/users/current/orders?cartId=${cartCode}&fields=FULL`,
      { method: 'POST' }
    );

    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.errors?.[0]?.message || 'Failed to place order');
    }

    const orderData = await res.json();
    localStorage.removeItem(CART_CODE_KEY);

    // Clean up any remaining carts so ensureCart doesn't adopt stale ones
    try {
      const cartsRes = await authFetch(`${OCC_BASE}/users/current/carts?fields=DEFAULT`);
      if (cartsRes.ok) {
        const cartsData = await cartsRes.json();
        for (const cart of (cartsData.carts || []) as { code: string }[]) {
          await authFetch(`${OCC_BASE}/users/current/carts/${cart.code}`, { method: 'DELETE' }).catch(() => {});
        }
      }
    } catch { /* ignore cleanup errors */ }

    return mapOccOrder(orderData);
  },

  visualSearch: async (base64Image: string, mimeType: string): Promise<VisualSearchResult> => {
    const res = await authFetch(`${OCC_BASE}/agent/visual-search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: base64Image, mimeType }),
    });

    if (res.status === 413) throw new Error('Image is too large. Please use an image under 10MB.');
    if (res.status === 429) throw new Error('Too many requests. Please wait a moment and try again.');
    if (res.status === 503) throw new Error('Image analysis is temporarily unavailable. Please try again later.');
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || `Visual search failed (${res.status})`);
    }

    const data = await res.json();
    // Map OCC-shaped products through the same mapper as normal search
    return {
      ...data,
      mappedProducts: (data.products || []).map((match: { product: unknown; matchType: string; confidence: number }) => ({
        product: mapOccProduct(match.product),
        matchType: match.matchType,
        confidence: match.confidence,
      })),
    };
  },

  getAgentCapabilities: async (): Promise<{ vision: boolean }> => {
    try {
      const res = await authFetch(`${OCC_BASE}/agent/capabilities`);
      if (!res.ok) return { vision: false };
      const data = await res.json();
      return { vision: Boolean(data?.vision) };
    } catch {
      return { vision: false };
    }
  },

  cancelOrder: async (orderId: string): Promise<void> => {
    // Get order details to find entries for cancellation
    const order = await api.getOrder(orderId);

    const cancellationEntries = order.items.map((item, index) => ({
      orderEntryNumber: index,
      quantity: item.quantity,
    }));

    const res = await authFetch(
      `${OCC_BASE}/users/current/orders/${orderId}/cancellation`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          cancellationRequestEntryInputs: cancellationEntries,
        }),
      }
    );

    if (!res.ok) {
      const error = await res.json().catch(() => ({}));
      throw new Error(error.errors?.[0]?.message || 'Failed to cancel order');
    }
  },

  // --- Knowledge Base (public /info/*; no auth) ---

  searchKnowledge: async (
    opts: { q?: string; category?: string; pageSize?: number } = {},
  ): Promise<KnowledgeSearchResult> => {
    const params = new URLSearchParams();
    if (opts.q) params.set('q', opts.q);
    if (opts.category) params.set('category', opts.category);
    if (opts.pageSize != null) params.set('pageSize', String(opts.pageSize));

    const res = await fetch(`${OCC_BASE}/info/search?${params}`, { cache: 'no-store' });
    // KB is non-critical chrome — degrade quietly rather than throwing.
    if (!res.ok) return { results: [], count: 0 };

    const data = await res.json();
    const results = (data.results ?? []).map(mapKnowledgeEntry);
    return { results, count: data.count ?? results.length };
  },
};

// ============================================
// Cart Utilities — Adapter for Components
// ============================================

export const cartUtils = {
  getCurrentUserId: (): string | null => {
    return auth.getUserEmail();
  },

  setCurrentUserId: (_userId: string): void => {
    // No-op: auth module manages user identity via OAuth token
  },

  getCart: async (): Promise<CartItem[]> => {
    if (!auth.isLoggedIn()) return [];

    try {
      const cart = await api.getCart();
      return cart.items || [];
    } catch (error) {
      console.error('Failed to load cart:', error);
      return [];
    }
  },

  addToCart: async (product: Product, quantity: number): Promise<CartItem[]> => {
    if (!auth.isLoggedIn()) {
      throw new Error('Please log in first');
    }

    try {
      await api.addToCart(product.id, quantity);
      window.dispatchEvent(new Event('cartUpdated'));
      return await cartUtils.getCart();
    } catch (error) {
      console.error('Failed to add to cart:', error);
      throw error;
    }
  },

  removeFromCart: async (productId: string): Promise<CartItem[]> => {
    if (!auth.isLoggedIn()) {
      throw new Error('Please log in first');
    }

    try {
      // Look up entryNumber by productId
      const items = await cartUtils.getCart();
      const item = items.find((i) => i.productId === productId);
      if (!item || item.entryNumber === undefined) {
        throw new Error('Item not found in cart');
      }

      await api.removeFromCart(item.entryNumber);
      window.dispatchEvent(new Event('cartUpdated'));
      return await cartUtils.getCart();
    } catch (error) {
      console.error('Failed to remove from cart:', error);
      throw error;
    }
  },

  updateCartItem: async (
    productId: string,
    quantity: number
  ): Promise<CartItem[]> => {
    if (!auth.isLoggedIn()) {
      throw new Error('Please log in first');
    }

    try {
      // Look up entryNumber by productId
      const items = await cartUtils.getCart();
      const item = items.find((i) => i.productId === productId);
      if (!item || item.entryNumber === undefined) {
        throw new Error('Item not found in cart');
      }

      await api.updateCartItem(item.entryNumber, quantity);
      window.dispatchEvent(new Event('cartUpdated'));
      return await cartUtils.getCart();
    } catch (error) {
      console.error('Failed to update cart:', error);
      throw error;
    }
  },

  clearCart: async (): Promise<void> => {
    if (!auth.isLoggedIn()) return;

    try {
      await api.clearCart();
      window.dispatchEvent(new Event('cartUpdated'));
    } catch (error) {
      console.error('Failed to clear cart:', error);
      throw error;
    }
  },

  getCartTotal: (cart: CartItem[]): number => {
    if (!cart || !Array.isArray(cart)) return 0;
    return cart.reduce((total, item) => total + item.price * item.quantity, 0);
  },

  getCartCount: (cart: CartItem[]): number => {
    if (!cart || !Array.isArray(cart)) return 0;
    return cart.reduce((total, item) => total + item.quantity, 0);
  },
};
