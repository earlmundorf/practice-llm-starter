import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { auth, getStoredCartCode, storeCartCode, clearStoredCartCode } from '../services/api';
import { Checkout } from './Checkout';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

const DEFAULT_SUGGESTIONS = [
  'What products do you have?',
  'Show me laptops',
  'Add a keyboard to my cart',
  'Show me my cart',
];

const chatKey = (suffix: string): string => {
  const email = auth.getUserEmail() || 'anonymous';
  return `thinkshop_chat_${suffix}_${email}`;
};

const loadPersistedMessages = (): ChatMessage[] => {
  try {
    const stored = localStorage.getItem(chatKey('messages'));
    return stored ? JSON.parse(stored) : [];
  } catch { return []; }
};

const loadPersistedSuggestions = (): string[] => {
  try {
    const stored = localStorage.getItem(chatKey('suggestions'));
    return stored ? JSON.parse(stored) : DEFAULT_SUGGESTIONS;
  } catch { return DEFAULT_SUGGESTIONS; }
};

/** Map of agent-returned UI actions to frontend behavior */
const ACTION_MAP: Record<string, string> = {
  checkout: '/checkout',  // navigates to checkout page
  // cart: 'cart',        // TODO: opens cart modal (future)
};

/** Parse SUGGESTIONS:[...] from the last line of agent response */
const parseSuggestions = (text: string): { clean: string; suggestions: string[] } => {
  const match = text.match(/\n?SUGGESTIONS:\s*(\[.*\])\s*$/);
  if (!match) return { clean: text, suggestions: [] };
  try {
    const suggestions = JSON.parse(match[1]) as string[];
    return { clean: text.slice(0, match.index).trimEnd(), suggestions };
  } catch {
    return { clean: text, suggestions: [] };
  }
};

export const Chat = () => {
  const navigate = useNavigate();
  const [messages, setMessages] = useState<ChatMessage[]>(loadPersistedMessages);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>(loadPersistedSuggestions);
  const [showCheckout, setShowCheckout] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Persist messages and suggestions per-user in localStorage
  useEffect(() => {
    localStorage.setItem(chatKey('messages'), JSON.stringify(messages));
  }, [messages]);

  useEffect(() => {
    localStorage.setItem(chatKey('suggestions'), JSON.stringify(suggestions));
  }, [suggestions]);

  // Reload chat for the new user on login/logout
  useEffect(() => {
    const handleAuth = () => {
      setMessages(loadPersistedMessages());
      setSuggestions(loadPersistedSuggestions());
    };
    window.addEventListener('authChanged', handleAuth);
    return () => window.removeEventListener('authChanged', handleAuth);
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  // Check for checkout results when returning from checkout/order-confirmation
  // Check for checkout results when returning from checkout/order-confirmation
  useEffect(() => {
    const result = sessionStorage.getItem('thinkshop_checkout_result');
    if (!result) return;
    sessionStorage.removeItem('thinkshop_checkout_result');

    try {
      const parsed = JSON.parse(result);
      if (parsed.type === 'placed') {
        clearStoredCartCode();
        // Create a fresh empty cart BEFORE dispatching cartUpdated
        // so ensureCart() finds it instead of picking up old carts
        (async () => {
          try {
            const token = auth.getToken();
            if (token) {
              const OCC_BASE = import.meta.env.VITE_API_URL || '/occ/v2/electronics';
              const res = await fetch(`${OCC_BASE}/users/current/carts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
              });
              if (res.ok) {
                const data = await res.json();
                if (data?.code) storeCartCode(data.code);
              }
            }
          } catch { /* ignore */ }
          window.dispatchEvent(new Event('cartUpdated'));
        })();
        let orderSummary = `Order #${parsed.orderId} placed successfully!\n\n`;
        if (parsed.items?.length) {
          orderSummary += parsed.items.map((item: { name: string; quantity: number; price: number }) =>
            `  ${item.name} x${item.quantity} — $${(item.price * item.quantity).toFixed(2)}`
          ).join('\n') + '\n\n';
        }
        if (parsed.subtotal != null) orderSummary += `Subtotal: $${parsed.subtotal.toFixed(2)}\n`;
        if (parsed.delivery) orderSummary += `Delivery: $${parsed.delivery.toFixed(2)}\n`;
        orderSummary += `Total: $${parsed.total?.toFixed(2) ?? '0.00'}\n\n`;
        orderSummary += 'Your cart has been cleared. Is there anything else I can help you with?';
        const msg: ChatMessage = {
          role: 'assistant',
          content: orderSummary,
        };
        setMessages(prev => [...prev, msg]);
        setSuggestions(['Show my orders', 'Continue shopping', 'What products do you have?']);
      } else if (parsed.type === 'cancelled') {
        const msg: ChatMessage = {
          role: 'assistant',
          content: 'No problem! Your cart is still saved. What would you like to do next?',
        };
        setMessages(prev => [...prev, msg]);
        setSuggestions(['Show my cart', 'Continue shopping', 'Proceed to checkout']);
      }
    } catch { /* ignore bad data */ }
  }, []);

  const handleSuggestionClick = (text: string) => {
    sendMessage(text);
  };

  const sendMessage = async (text?: string) => {
    const content = (text || input).trim();
    if (!content || loading) return;

    const userMsg: ChatMessage = { role: 'user', content };
    const updatedMessages = [...messages, userMsg];
    setMessages(updatedMessages);
    setInput('');
    setLoading(true);

    try {
      const token = auth.getToken();
      if (!token) throw new Error('Not authenticated');

      const OCC_BASE = import.meta.env.VITE_API_URL || '/occ/v2/electronics';
      const res = await fetch(`${OCC_BASE}/agent/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          messages: updatedMessages.map(m => ({ role: m.role, content: m.content })),
          cartCode: getStoredCartCode() || undefined,
        }),
      });

      if (res.status === 401) {
        auth.logout();
        throw new Error('Session expired. Please log in again.');
      }

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Request failed');
      }

      const data = await res.json();
      const { clean, suggestions: parsed } = parseSuggestions(data.reply);

      const assistantMsg: ChatMessage = { role: 'assistant', content: clean };
      setMessages([...updatedMessages, assistantMsg]);

      // Update suggestions — use agent suggestions or contextual defaults
      if (parsed.length > 0) {
        setSuggestions(parsed);
      } else {
        setSuggestions(getDefaultSuggestionsForContext(clean));
      }

      // Sync cart: store the cart code the agent used, then notify Header
      if (data.cartCode) {
        storeCartCode(data.cartCode);
      }
      window.dispatchEvent(new Event('cartUpdated'));
      window.dispatchEvent(new Event('cartItemAdded'));

      // Act on agent-driven UI actions
      if (data.action) {
        const mapped = ACTION_MAP[data.action];
        if (mapped) {
          if (mapped === '/checkout') {
            sessionStorage.setItem('thinkshop_from_chat', 'true');
          }
          navigate(mapped);
        }
      }
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : 'Something went wrong';
      setMessages([...updatedMessages, { role: 'assistant', content: `Error: ${errMsg}` }]);
    } finally {
      setLoading(false);
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  };

  if (!auth.isLoggedIn()) {
    return (
      <div className="h-full flex items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="max-w-md w-full mx-4">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-8 text-center">
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
              AI Shopping Assistant
            </h2>
            <p className="text-gray-600 dark:text-gray-400 mb-6">
              Please log in to start chatting
            </p>
          </div>
        </div>
      </div>
    );
  }

  const handleOrderPlaced = async (orderId: string) => {
    setShowCheckout(false);
    clearStoredCartCode();

    // Store recent order ID so Orders page can verify it's in the list
    sessionStorage.setItem('thinkshop_recent_order', orderId);

    // Create a fresh empty cart so the header count resets to 0
    try {
      const token = auth.getToken();
      if (token) {
        const OCC_BASE = import.meta.env.VITE_API_URL || '/occ/v2/electronics';
        const res = await fetch(`${OCC_BASE}/users/current/carts`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        });
        if (res.ok) {
          const data = await res.json();
          storeCartCode(data.code);
        }
      }
    } catch { /* ignore */ }

    window.dispatchEvent(new Event('cartUpdated'));
    const confirmMsg: ChatMessage = {
      role: 'assistant',
      content: `Order ${orderId} placed successfully! Your cart has been cleared. Is there anything else I can help you with?`,
    };
    setMessages(prev => [...prev, confirmMsg]);
    setSuggestions(['Show my orders', 'Continue shopping', 'What products do you have?']);
  };

  // Embedded checkout mode — replaces the chat area
  if (showCheckout) {
    return (
      <div className="h-full flex flex-col bg-gray-50 dark:bg-gray-900 transition-colors duration-300">
        <div className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-4 overflow-y-auto">
          <Checkout embedded onBack={() => setShowCheckout(false)} onOrderPlaced={handleOrderPlaced} />
        </div>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col bg-gray-50 dark:bg-gray-900 transition-colors duration-300">
      <div className="flex-shrink-0 max-w-4xl w-full mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-4">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            AI Shopping Assistant
          </h2>
          {messages.length > 0 && (
            <button
              onClick={() => {
                setMessages([]);
                setSuggestions(DEFAULT_SUGGESTIONS);
              }}
              disabled={loading}
              className="text-sm text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 transition-colors disabled:opacity-50"
            >
              Clear chat
            </button>
          )}
        </div>
        <p className="text-gray-600 dark:text-gray-400 mb-4">
          Ask about products, manage your cart, or place orders
        </p>
        <div className="flex flex-wrap gap-2">
          {suggestions.map((s, i) => (
            <button
              key={`${i}-${s}`}
              onClick={() => handleSuggestionClick(s)}
              disabled={loading}
              className="px-2.5 py-1 bg-white dark:bg-gray-800 border border-blue-500 dark:border-blue-400 text-blue-600 dark:text-blue-400 rounded-full hover:bg-blue-500 hover:text-white dark:hover:bg-blue-500 dark:hover:text-white transition-colors font-medium text-xs disabled:opacity-50"
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 lg:px-8 pb-6 flex flex-col min-h-0">
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 flex flex-col flex-1 min-h-0 transition-colors duration-300">
          <div className="flex-1 overflow-y-auto p-6 space-y-4">
            {messages.length === 0 && (
              <div className="text-center text-gray-400 dark:text-gray-500 py-12">
                <p className="text-lg mb-2">Start a conversation</p>
                <p className="text-sm">Try one of the suggestions above, or type your own message</p>
              </div>
            )}

            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[80%] px-4 py-3 rounded-lg whitespace-pre-wrap ${
                    msg.role === 'user'
                      ? 'bg-blue-600 dark:bg-blue-500 text-white'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200'
                  }`}
                >
                  {msg.content}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex justify-start">
                <div className="bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/30 dark:to-purple-900/30 px-4 py-3 rounded-lg text-gray-700 dark:text-gray-300 border border-blue-200 dark:border-blue-700 flex items-center gap-2">
                  <div className="animate-pulse">...</div>
                  <span className="font-medium">Thinking...</span>
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          <div className="flex-shrink-0 p-4 border-t border-gray-200 dark:border-gray-700">
            <div className="flex gap-2">
              <input
                ref={inputRef}
                type="text"
                placeholder="Ask about products, manage your cart, or place orders..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                disabled={loading}
                className="flex-1 px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 transition-colors duration-300"
              />
              <button
                onClick={() => sendMessage()}
                disabled={loading || !input.trim()}
                className="bg-blue-600 dark:bg-blue-500 text-white px-6 py-3 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Send
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

/** Fallback suggestions based on keywords in the last assistant message */
function getDefaultSuggestionsForContext(lastReply: string): string[] {
  const lower = lastReply.toLowerCase();

  if (lower.includes('added to') || lower.includes('added the')) {
    return ['Show my cart', 'Continue shopping', 'Proceed to checkout', 'What else do you have?'];
  }
  if (lower.includes('your cart') || lower.includes('cart contains') || lower.includes('cart now')) {
    return ['Proceed to checkout', 'Continue shopping', 'Clear my cart', 'Remove an item'];
  }
  if (lower.includes('order') && (lower.includes('placed') || lower.includes('confirmed'))) {
    return ['Show my orders', 'Continue shopping', 'What products do you have?'];
  }
  if (lower.includes('checkout') || lower.includes('delivery') || lower.includes('address')) {
    return ['Proceed to checkout', 'Show my cart', 'Continue shopping'];
  }
  if (lower.includes('product') || lower.includes('$') || lower.includes('price')) {
    return ['Add it to my cart', 'Show me more products', 'Show my cart', 'Tell me more'];
  }

  return DEFAULT_SUGGESTIONS;
}
