// ==========================================================================
// OCC Response Types
//
// These interfaces match the SAP Commerce OCC REST response shapes.
// See docs/endpoint-mapping.md for the full API contract.
// ==========================================================================

// --- Shared ---

export interface Price {
  value: number;
  currencyIso: string;
  formattedValue: string;
}

export interface Pagination {
  currentPage: number;
  pageSize: number;
  totalResults: number;
  totalPages: number;
}

export interface Sort {
  code: string;
  name: string;
  selected: boolean;
}

export interface Image {
  format: string;
  url: string;
  altText?: string;
}

// --- Products ---

export interface Stock {
  stockLevel?: number;
  stockLevelStatus: 'inStock' | 'lowStock' | 'outOfStock';
}

export interface Category {
  code: string;
  name: string;
}

export interface Product {
  code: string;
  name: string;
  summary?: string;
  description?: string;
  price?: Price;
  stock?: Stock;
  images?: Image[];
  categories?: Category[];
  averageRating?: number;
}

export interface FacetValue {
  name: string;
  count: number;
  query: { query: { value: string } };
  selected: boolean;
}

export interface Facet {
  name: string;
  values: FacetValue[];
  multiSelect: boolean;
}

export interface ProductSearchResult {
  products: Product[];
  pagination: Pagination;
  sorts: Sort[];
  facets: Facet[];
}

// --- Cart ---

export interface CartEntry {
  entryNumber: number;
  product: Product;
  quantity: number;
  basePrice: Price;
  totalPrice: Price;
}

export interface Cart {
  code: string;
  guid: string;
  entries: CartEntry[];
  totalItems: number;
  totalUnitCount: number;
  totalPrice: Price;
  subTotal?: Price;
  deliveryCost?: Price;
  deliveryAddress?: Address;
  deliveryMode?: DeliveryMode;
  paymentInfo?: PaymentDetails;
}

export interface CartModification {
  statusCode: string;
  quantityAdded: number;
  quantity: number;
  entry: CartEntry;
}

// --- Checkout ---

export interface Address {
  id?: string;
  firstName: string;
  lastName: string;
  line1: string;
  line2?: string;
  town: string;
  postalCode: string;
  country: { isocode: string; name?: string };
  region?: { isocode: string; name?: string };
  email?: string;
  phone?: string;
}

export interface DeliveryMode {
  code: string;
  name: string;
  deliveryCost: Price;
  description?: string;
}

export interface PaymentDetails {
  id?: string;
  accountHolderName?: string;
  cardNumber?: string;
  cardType: { code: string; name?: string };
  expiryMonth: string;
  expiryYear: string;
  billingAddress?: Address;
}

// --- Orders ---

export interface OrderEntry {
  entryNumber: number;
  product: Product;
  quantity: number;
  basePrice: Price;
  totalPrice: Price;
}

export interface Order {
  code: string;
  status: string;
  statusDisplay: string;
  created: string;
  totalPrice: Price;
  totalPriceWithTax?: Price;
  subTotal?: Price;
  deliveryCost?: Price;
  entries: OrderEntry[];
  deliveryAddress?: Address;
  deliveryMode?: DeliveryMode;
  paymentInfo?: PaymentDetails;
}

export interface OrderHistoryResult {
  orders: Order[];
  pagination: Pagination;
}

// --- User ---

export interface User {
  uid: string;
  firstName: string;
  lastName: string;
  name: string;
  titleCode?: string;
  currency?: { isocode: string };
  language?: { isocode: string };
  defaultAddress?: Address;
}

export interface AuthToken {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
}

// --- UI State ---

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info';
  message: string;
}
