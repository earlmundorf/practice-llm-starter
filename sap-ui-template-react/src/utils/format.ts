import type { Price } from '../types';

/**
 * Format a price for display. Prefers OCC's formattedValue, falls back to manual formatting.
 */
export const formatPrice = (price: Price | undefined): string => {
  if (!price) return '—';
  if (price.formattedValue) return price.formattedValue;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: price.currencyIso || 'USD',
  }).format(price.value);
};

/**
 * Format an ISO date string for display.
 */
export const formatDate = (isoDate: string | undefined): string => {
  if (!isoDate) return '—';
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(isoDate));
};

/**
 * Format an ISO date string with time.
 */
export const formatDateTime = (isoDate: string | undefined): string => {
  if (!isoDate) return '—';
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(isoDate));
};

/**
 * Map OCC order status codes to display labels and colors.
 */
export const ORDER_STATUS: Record<string, { label: string; color: string }> = {
  CREATED: { label: 'Created', color: 'text-blue-600 dark:text-blue-400' },
  CHECKED_VALID: { label: 'Validated', color: 'text-blue-600 dark:text-blue-400' },
  PAYMENT_AUTHORIZED: { label: 'Payment Authorized', color: 'text-yellow-600 dark:text-yellow-400' },
  PAYMENT_CAPTURED: { label: 'Payment Captured', color: 'text-yellow-600 dark:text-yellow-400' },
  READY: { label: 'Ready', color: 'text-indigo-600 dark:text-indigo-400' },
  COMPLETED: { label: 'Completed', color: 'text-green-600 dark:text-green-400' },
  CANCELLED: { label: 'Cancelled', color: 'text-red-600 dark:text-red-400' },
};

export const formatOrderStatus = (status: string): { label: string; color: string } => {
  return ORDER_STATUS[status] || { label: status, color: 'text-gray-600 dark:text-gray-400' };
};
