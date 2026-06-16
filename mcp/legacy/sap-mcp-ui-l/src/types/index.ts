// ============================================
// User Types
// ============================================
export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
}

// ============================================
// Product Types
// ============================================
export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  imageUrl?: string;
}

// ============================================
// Cart Types
// ============================================
export interface CartItem {
  productId: string;
  productName: string;
  quantity: number;
  price: number;
  entryNumber?: number;
  product?: Product;
  discountValue?: number;
}

// ============================================
// Order Types
// ============================================
export type OrderStatus = 'CREATED' | 'COMPLETED' | 'CANCELLED';

export interface OrderItem {
  productId: string;
  productName: string;
  description?: string;
  imageUrl?: string;
  quantity: number;
  price: number;
}

export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number;
  subTotal?: number;
  deliveryCost?: number;
  totalTax?: number;
  status: OrderStatus;
  createdAt: string;
}

// ============================================
// API Types
// ============================================
export interface ApiError {
  error: string;
  message?: string;
  status?: number;
}

// ============================================
// Context Types
// ============================================
export interface DarkModeContextType {
  darkMode: boolean;
  toggleDarkMode: () => void;
}

// ============================================
// Component Prop Types
// ============================================
export interface ProductCardProps {
  product: Product;
  onAddToCart: (product: Product, quantity: number) => Promise<void>;
}

export interface ToastProps {
  message: string;
  type?: 'success' | 'error' | 'info';
  onClose: () => void;
}

export interface CartModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export interface UserPickerProps {
  onUserSelected: (user: User) => void;
  onCancel: (() => void) | null;
}

// ============================================
// Address Types
// ============================================
export interface Address {
  id: string;
  firstName: string;
  lastName: string;
  line1: string;
  line2?: string;
  town: string;
  postalCode: string;
  country: { isocode: string; name?: string };
  defaultAddress?: boolean;
}

// ============================================
// Payment Types
// ============================================
export interface PaymentFormData {
  cardNumber: string;
  cardType: string;
  expiryMonth: string;
  expiryYear: string;
  accountHolderName: string;
}

// ============================================
// Promotion / Voucher Types
// ============================================
export interface AppliedVoucher {
  code: string;
  name?: string;
  appliedValue?: number;
  freeShipping?: boolean;
}

export interface AppliedPromotion {
  description: string;
  promotionCode?: string;
  promotionType?: string;
  consumedEntries?: number[];
}

// ============================================
// Search Types
// ============================================
export interface FacetValue {
  code: string;
  name: string;
  count: number;
  selected: boolean;
  query: string;
}

export interface Facet {
  code: string;
  name: string;
  visible: boolean;
  multiSelect: boolean;
  values: FacetValue[];
}

export interface SearchResult {
  products: Product[];
  pagination: { currentPage: number; pageSize: number; totalResults: number; totalPages: number };
  sorts: { code: string; name: string; selected: boolean }[];
  facets: Facet[];
}

// ============================================
// Visual Search Types
// ============================================
export interface VisualSearchMatch {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  product: any; // Full OCC-shaped product data — mapped via mapOccProduct
  matchType: 'bestMatch' | 'similar' | 'explore';
  confidence: number;
}

export interface VisualSearchAiDetail {
  productName?: string;
  brand?: string;
  category?: string;
  color?: string;
  searchTerms?: string[];
  reasoning?: string;
  confidence?: string;
}

export interface MappedVisualSearchMatch {
  product: Product;
  matchType: 'bestMatch' | 'similar' | 'explore';
  confidence: number;
}

export interface VisualSearchResult {
  visionAnalysis: string;
  aiDetail?: VisualSearchAiDetail;
  products: VisualSearchMatch[];
  mappedProducts: MappedVisualSearchMatch[];
}

// ============================================
// Knowledge Base Types
// ============================================
// Public KB content served over /info/* (no auth). Consumed by the Help
// center and footer/about pages. `category` is a plain string to stay
// tolerant of new backend categories (e.g. loyalty content).
export interface KnowledgeEntry {
  uid: string;
  category: string;
  title: string;
  summary: string;
  body: string;
  tags: string[];
  priority: number;
  imageUrl?: string;
}

export interface KnowledgeSearchResult {
  results: KnowledgeEntry[];
  count: number;
}
