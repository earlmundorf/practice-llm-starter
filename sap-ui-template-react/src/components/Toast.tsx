import { useState, useEffect, useCallback } from 'react';
import type { ToastMessage } from '../types';

// ==========================================================================
// Toast notification system
//
// Usage from any component:
//   window.dispatchEvent(new CustomEvent('toast', {
//     detail: { type: 'success', message: 'Added to cart' }
//   }));
//
// The ToastContainer listens for these events and renders them.
// Mount <ToastContainer /> once in the Layout component.
// ==========================================================================

const TOAST_DURATION = 4000;

interface ToastProps {
  toast: ToastMessage;
  onDismiss: (id: string) => void;
}

const TOAST_STYLES: Record<ToastMessage['type'], string> = {
  success: 'bg-green-600 dark:bg-green-700 text-white',
  error: 'bg-red-600 dark:bg-red-700 text-white',
  info: 'bg-blue-600 dark:bg-blue-700 text-white',
};

const ToastItem = ({ toast, onDismiss }: ToastProps) => {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), TOAST_DURATION);
    return () => clearTimeout(timer);
  }, [toast.id, onDismiss]);

  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg ${TOAST_STYLES[toast.type]}`}
    >
      <span className="text-sm font-medium">{toast.message}</span>
      <button
        onClick={() => onDismiss(toast.id)}
        className="ml-auto text-white/80 hover:text-white"
        aria-label="Dismiss"
      >
        &times;
      </button>
    </div>
  );
};

export const ToastContainer = () => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    const handleToast = (e: Event) => {
      const { type, message } = (e as CustomEvent).detail;
      const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      setToasts((prev) => [...prev, { id, type, message }]);
    };

    window.addEventListener('toast', handleToast);
    return () => window.removeEventListener('toast', handleToast);
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={dismiss} />
      ))}
    </div>
  );
};

// Helper to dispatch toast events from anywhere
export const showToast = (type: ToastMessage['type'], message: string) => {
  window.dispatchEvent(new CustomEvent('toast', { detail: { type, message } }));
};
