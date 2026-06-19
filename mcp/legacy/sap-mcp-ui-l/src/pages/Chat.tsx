import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, auth, getStoredCartCode, storeCartCode, clearStoredCartCode } from '../services/api';
import { Checkout } from './Checkout';
import { Markdown } from '../components/Markdown';
import { OrderModal } from '../components/OrderModal';
import { ProductModal } from '../components/ProductModal';
import { OrderHistoryModal } from '../components/OrderHistoryModal';

const MAX_IMAGES_PER_TURN = 4;

/** Downscale + JPEG-compress an image File to a base64 data URL (max ~1024px on long edge). */
const compressImageToDataUrl = async (file: File): Promise<string> => {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i);
      i.onerror = reject;
      i.src = url;
    });
    const MAX = 1024;
    const scale = Math.min(1, MAX / Math.max(img.width, img.height));
    const w = Math.round(img.width * scale);
    const h = Math.round(img.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas not supported');
    ctx.drawImage(img, 0, 0, w, h);
    return canvas.toDataURL('image/jpeg', 0.85);
  } finally {
    URL.revokeObjectURL(url);
  }
};

interface EntityRef {
  type: 'product' | 'order' | 'orderHistory';
  code?: string;
}

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  entityRefs?: EntityRef[];
  /** Set on assistant bubbles that are still receiving streamed text deltas. */
  streaming?: boolean;
  /** Transient "Looking up your orders…" line shown while a tool round is running. */
  toolStatus?: string;
}

/** Human-readable status labels for in-progress tool calls (R1: tool SSE events). */
const TOOL_STATUS_LABEL: Record<string, string> = {
  product_search: 'Searching products…',
  product_get: 'Loading product details…',
  order_history: 'Looking up your orders…',
  order_get: 'Loading order details…',
  cart_get: 'Checking your cart…',
  cart_add_product: 'Adding to cart…',
  cart_update_entry: 'Updating your cart…',
  cart_remove_entry: 'Removing item from cart…',
  cart_apply_voucher: 'Applying coupon…',
  cart_remove_voucher: 'Removing coupon…',
  customer_get: 'Looking you up…',
  customer_lookup: 'Finding customer…',
  promotions_get: 'Checking promotions…',
  checkout_set_delivery_address: 'Saving delivery address…',
  checkout_set_delivery_mode: 'Setting delivery option…',
  checkout_set_payment: 'Saving payment details…',
  order_place: 'Placing your order…',
};

type OpenModal =
  | { kind: 'order'; orderId: string }
  | { kind: 'product'; code: string }
  | { kind: 'orderHistory' }
  | null;

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

/**
 * Phrases that should always navigate the user to /checkout, regardless of
 * what the agent decides. The agent is unreliable about emitting ui_action,
 * and this intent is unambiguous and high-cost-when-missed.
 */
const CHECKOUT_INTENT_REGEX =
  /\b(proceed (to )?check\s*out|go to check\s*out|take me to check\s*out|let'?s check\s*out|i (want|need|'?d like) to check\s*out|ready to check\s*out|check\s*out now|^check\s*out$)\b/i;

const isCheckoutIntent = (text: string): boolean => CHECKOUT_INTENT_REGEX.test(text.trim());

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

/** Shape returned by /agent/chat (and the done event of the streaming endpoint). */
interface AgentResponse {
  reply: string;
  entityRefs?: EntityRef[];
  action?: string;
  cartCode?: string;
  messages?: unknown[];
  intent?: string;
}

/**
 * Send the chat request, preferring the streaming endpoint and gracefully falling back when:
 *  - the server returns plain JSON (feature flag disabled, or gateway stripped SSE),
 *  - the response Content-Type isn't `text/event-stream`, or
 *  - the stream errors mid-flight.
 *
 * Text deltas are pushed through `onDelta` as they arrive (during SSE only — the JSON-fallback
 * path skips deltas because the full reply is already in hand).
 */
const fetchAgentResponse = async (
  occBase: string,
  headers: Record<string, string>,
  payload: string,
  onDelta: (chunk: string) => void,
  onTool: (toolName: string) => void,
): Promise<AgentResponse> => {
  const fallback = async (): Promise<AgentResponse> => {
    const res = await fetch(`${occBase}/agent/chat`, { method: 'POST', headers, body: payload });
    if (res.status === 401) { auth.logout(); throw new Error('Session expired. Please log in again.'); }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error((err as { error?: string }).error || 'Request failed');
    }
    return (await res.json()) as AgentResponse;
  };

  let res: Response;
  try {
    res = await fetch(`${occBase}/agent/chat/stream`, { method: 'POST', headers, body: payload });
  } catch {
    // Network-level error reaching the streaming endpoint — try the legacy path.
    return fallback();
  }

  if (res.status === 401) { auth.logout(); throw new Error('Session expired. Please log in again.'); }

  // 404/405 typically means the streaming route isn't deployed in this env — fall back.
  if (res.status === 404 || res.status === 405) {
    return fallback();
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { error?: string }).error || 'Request failed');
  }

  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (!contentType.includes('text/event-stream') || !res.body) {
    // Server elected to return plain JSON (feature flag off, or gateway didn't pass SSE through).
    return (await res.json()) as AgentResponse;
  }

  // Consume the SSE stream.
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let done: AgentResponse | null = null;
  let streamError: string | null = null;

  try {
    while (true) {
      const { value, done: isDone } = await reader.read();
      if (isDone) break;
      buffer += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        let eventName = 'message';
        const dataLines: string[] = [];
        for (const line of rawEvent.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim();
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
        }
        const dataStr = dataLines.join('\n');
        if (eventName === 'text') {
          try { onDelta(JSON.parse(dataStr) as string); } catch { /* skip malformed delta */ }
        } else if (eventName === 'tool') {
          try {
            const evt = JSON.parse(dataStr) as { name?: string };
            if (evt.name) onTool(evt.name);
          } catch { /* skip malformed tool event */ }
        } else if (eventName === 'done') {
          try { done = JSON.parse(dataStr) as AgentResponse; } catch { streamError = 'malformed done event'; }
        } else if (eventName === 'error') {
          try {
            const errPayload = JSON.parse(dataStr) as { error?: string };
            streamError = errPayload.error || 'stream error';
          } catch { streamError = 'stream error'; }
        }
      }
    }
  } catch (e) {
    streamError = e instanceof Error ? e.message : 'stream interrupted';
  }

  if (streamError && !done) {
    // Mid-stream failure with no terminal event — retry once against the non-streaming endpoint.
    return fallback();
  }
  if (!done) {
    // Stream closed without a done event — also fall back.
    return fallback();
  }
  return done;
};

export const Chat = () => {
  const navigate = useNavigate();
  const [messages, setMessages] = useState<ChatMessage[]>(loadPersistedMessages);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>(loadPersistedSuggestions);
  const [showCheckout, setShowCheckout] = useState(false);
  const [openModal, setOpenModal] = useState<OpenModal>(null);
  const [visionEnabled, setVisionEnabled] = useState(false);
  const [pendingImages, setPendingImages] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Probe the backend once on mount to see whether the configured LLM provider supports images.
  useEffect(() => {
    if (!auth.isLoggedIn()) return;
    api.getAgentCapabilities().then((caps) => setVisionEnabled(caps.vision));
  }, []);

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
    const images = pendingImages;
    if ((!content && images.length === 0) || loading) return;

    // Hard-route checkout intent: don't depend on the LLM emitting ui_action.
    if (images.length === 0 && isCheckoutIntent(content)) {
      setInput('');
      sessionStorage.setItem('thinkshop_from_chat', 'true');
      navigate('/checkout');
      return;
    }

    // Local display text — note image attachment(s) so the user sees them in the transcript.
    const imageNote = images.length > 1
      ? `[${images.length} images attached]`
      : images.length === 1 ? '[image attached]' : '';
    const displayContent = imageNote
      ? (content ? `${content}\n${imageNote}` : imageNote)
      : content;
    const userMsg: ChatMessage = { role: 'user', content: displayContent };
    const updatedMessages = [...messages, userMsg];
    setMessages(updatedMessages);
    setInput('');
    setPendingImages([]);
    setLoading(true);

    try {
      const token = auth.getToken();
      if (!token) throw new Error('Not authenticated');

      // Build the wire payload. Multimodal content array only on the LATEST user message
      // when images are attached; prior turns stay text-only (server already strips images
      // from echoed history, so they never come back from the server).
      const wireMessages = updatedMessages.map((m, idx) => {
        const isLast = idx === updatedMessages.length - 1;
        if (isLast && images.length > 0 && m.role === 'user') {
          const parts: Array<Record<string, unknown>> = [];
          if (content) parts.push({ type: 'text', text: content });
          for (const dataUrl of images) {
            parts.push({ type: 'image_url', image_url: { url: dataUrl } });
          }
          return { role: m.role, content: parts };
        }
        return { role: m.role, content: m.content };
      });

      const OCC_BASE = import.meta.env.VITE_API_URL || '/occ/v2/electronics';
      const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      };
      const payload = JSON.stringify({
        messages: wireMessages,
        cartCode: getStoredCartCode() || undefined,
      });

      // Try the streaming endpoint first. On any failure (server flag off, gateway strips
      // SSE, network hiccup mid-stream) we transparently fall back to /agent/chat.
      const data = await fetchAgentResponse(
        OCC_BASE,
        headers,
        payload,
        (delta) => {
          // Append this delta to the in-progress assistant bubble. We seed the bubble on
          // first delta so we don't render an empty bubble before any text arrives. Once
          // text starts arriving we drop any tool-status line — it served its purpose.
          setMessages((prev) => {
            const last = prev[prev.length - 1];
            if (last && last.role === 'assistant' && last.streaming) {
              const updated: ChatMessage = {
                ...last,
                content: last.content + delta,
                toolStatus: undefined,
              };
              return [...prev.slice(0, -1), updated];
            }
            return [...prev, { role: 'assistant', content: delta, streaming: true }];
          });
        },
        (toolName) => {
          // Render a transient status line on the in-progress bubble (or seed one if there
          // isn't a streaming bubble yet). Cleared when text starts streaming.
          const status = TOOL_STATUS_LABEL[toolName] ?? 'Working on it…';
          setMessages((prev) => {
            const last = prev[prev.length - 1];
            if (last && last.role === 'assistant' && last.streaming) {
              return [...prev.slice(0, -1), { ...last, toolStatus: status }];
            }
            return [...prev, { role: 'assistant', content: '', streaming: true, toolStatus: status }];
          });
        },
      );

      const { clean, suggestions: parsed } = parseSuggestions(data.reply);

      const entityRefs = Array.isArray(data.entityRefs) ? (data.entityRefs as EntityRef[]) : undefined;
      // Replace any in-progress streaming bubble with the final cleaned reply (strips the
      // SUGGESTIONS:[...] tail). If we never streamed (fallback path), append normally.
      setMessages((prev) => {
        const last = prev[prev.length - 1];
        const finalMsg: ChatMessage = { role: 'assistant', content: clean, entityRefs };
        if (last && last.role === 'assistant' && (last as { streaming?: boolean }).streaming) {
          return [...prev.slice(0, -1), finalMsg];
        }
        return [...prev, finalMsg];
      });

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
                className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[80%] px-4 py-3 rounded-lg ${
                    msg.role === 'user'
                      ? 'bg-blue-600 dark:bg-blue-500 text-white whitespace-pre-wrap'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200'
                  }`}
                >
                  {msg.role === 'user' ? (
                    msg.content
                  ) : (
                    <>
                    {msg.content && (
                    <Markdown>{msg.content}</Markdown>
                    )}
                    {msg.streaming && msg.toolStatus && (
                      <div className={`flex items-center gap-2 text-sm italic text-gray-500 dark:text-gray-400 ${msg.content ? 'mt-2' : ''}`}>
                        <span className="inline-block w-3 h-3 rounded-full border-2 border-current border-t-transparent animate-spin" />
                        {msg.toolStatus}
                      </div>
                    )}
                    </>
                  )}
                </div>
                {msg.role === 'assistant' && msg.entityRefs && msg.entityRefs.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2 max-w-[80%]">
                    {msg.entityRefs.map((ref, i) => {
                      const onClick = () => {
                        if (ref.type === 'order' && ref.code) setOpenModal({ kind: 'order', orderId: ref.code });
                        else if (ref.type === 'product' && ref.code) setOpenModal({ kind: 'product', code: ref.code });
                        else if (ref.type === 'orderHistory') setOpenModal({ kind: 'orderHistory' });
                      };
                      const label =
                        ref.type === 'order' ? `🧾 Order #${ref.code}` :
                        ref.type === 'product' ? `📦 ${ref.code}` :
                        '📋 All orders';
                      return (
                        <button
                          key={i}
                          type="button"
                          onClick={onClick}
                          className="text-sm px-3 py-1 rounded-full bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-700 hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors cursor-pointer"
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                )}
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
            {pendingImages.length > 0 && (
              <div className="mb-2 flex items-center gap-2 flex-wrap">
                {pendingImages.map((dataUrl, idx) => (
                  <div key={idx} className="relative">
                    <img
                      src={dataUrl}
                      alt={`Attached ${idx + 1}`}
                      className="h-16 w-16 object-cover rounded border border-gray-300 dark:border-gray-600"
                    />
                    <button
                      onClick={() => setPendingImages((p) => p.filter((_, i) => i !== idx))}
                      title="Remove"
                      className="absolute -top-1.5 -right-1.5 h-5 w-5 rounded-full bg-gray-800 text-white text-xs flex items-center justify-center hover:bg-red-600"
                    >
                      ×
                    </button>
                  </div>
                ))}
                {pendingImages.length < MAX_IMAGES_PER_TURN && (
                  <span className="text-xs text-gray-500 dark:text-gray-400">
                    {MAX_IMAGES_PER_TURN - pendingImages.length} more allowed
                  </span>
                )}
              </div>
            )}
            <div className="flex gap-2">
              {visionEnabled && (
                <>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    multiple
                    // On mobile this opens the camera directly; desktop opens a multi-select picker.
                    capture="environment"
                    className="hidden"
                    onChange={async (e) => {
                      const files = Array.from(e.target.files || []);
                      e.target.value = '';
                      if (files.length === 0) return;
                      try {
                        const available = MAX_IMAGES_PER_TURN - pendingImages.length;
                        const accepted = files.slice(0, Math.max(0, available));
                        const dataUrls = await Promise.all(accepted.map(compressImageToDataUrl));
                        setPendingImages((prev) => [...prev, ...dataUrls]);
                      } catch (err) {
                        console.error('Image compression failed', err);
                      }
                    }}
                  />
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    disabled={loading || pendingImages.length >= MAX_IMAGES_PER_TURN}
                    title="Attach a photo"
                    className="px-3 py-3 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-600 disabled:opacity-50"
                  >
                    📷
                  </button>
                </>
              )}
              <input
                ref={inputRef}
                type="text"
                placeholder="Ask about products, manage your cart, or place orders..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                onPaste={(e) => {
                  if (!visionEnabled) return;
                  const items = e.clipboardData?.items;
                  if (!items) return;
                  const files: File[] = [];
                  for (const item of items) {
                    if (item.type.startsWith('image/')) {
                      const f = item.getAsFile();
                      if (f) files.push(f);
                    }
                  }
                  if (files.length === 0) return;
                  e.preventDefault();
                  const available = MAX_IMAGES_PER_TURN - pendingImages.length;
                  const accepted = files.slice(0, Math.max(0, available));
                  Promise.all(accepted.map(compressImageToDataUrl))
                    .then((urls) => setPendingImages((prev) => [...prev, ...urls]))
                    .catch(console.error);
                }}
                disabled={loading}
                className="flex-1 px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 transition-colors duration-300"
              />
              <button
                onClick={() => sendMessage()}
                disabled={loading || (!input.trim() && pendingImages.length === 0)}
                className="bg-blue-600 dark:bg-blue-500 text-white px-6 py-3 rounded-lg hover:bg-blue-700 dark:hover:bg-blue-600 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Send
              </button>
            </div>
          </div>
        </div>
      </div>

      {openModal?.kind === 'order' && (
        <OrderModal orderId={openModal.orderId} onClose={() => setOpenModal(null)} />
      )}
      {openModal?.kind === 'product' && (
        <ProductModal
          code={openModal.code}
          onClose={() => setOpenModal(null)}
          onAddToCart={async (code) => {
            try {
              await api.addToCart(code, 1);
              window.dispatchEvent(new Event('cartUpdated'));
              window.dispatchEvent(new Event('cartItemAdded'));
            } finally {
              setOpenModal(null);
            }
          }}
        />
      )}
      {openModal?.kind === 'orderHistory' && (
        <OrderHistoryModal
          onClose={() => setOpenModal(null)}
          onOpenOrder={(orderId) => setOpenModal({ kind: 'order', orderId })}
        />
      )}
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
