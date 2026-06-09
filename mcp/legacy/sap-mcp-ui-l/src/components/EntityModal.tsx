import { useEffect } from 'react';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export const EntityModal = ({ title, onClose, children }: Props) => {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl border border-gray-200 dark:border-gray-700 w-full max-w-3xl max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-xl font-bold text-gray-900 dark:text-white">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white text-2xl leading-none cursor-pointer"
            aria-label="Back to chat"
          >
            ×
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6">{children}</div>
        <div className="px-6 py-3 border-t border-gray-200 dark:border-gray-700 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="bg-blue-600 dark:bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold"
          >
            Back to chat
          </button>
        </div>
      </div>
    </div>
  );
};
