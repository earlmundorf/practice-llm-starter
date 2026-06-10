package com.coremcp.services.impl;

import com.coremcp.services.AgentRateLimiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window in-memory rate limiter (one window per caller per minute).
 * Node-local by design: on a multi-node deployment each node grants the
 * configured budget independently, so the effective ceiling is limit × nodes —
 * acceptable for a cost guard, which needs an order-of-magnitude cap rather
 * than exact accounting. Set requestsPerMinute to 0 to disable.
 */
public class DefaultAgentRateLimiter implements AgentRateLimiter
{
	private static final int CLEANUP_THRESHOLD = 10_000;

	private int requestsPerMinute = 20;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	private static final class Window
	{
		private final long startMillis;
		private final AtomicInteger count = new AtomicInteger();

		private Window(final long startMillis)
		{
			this.startMillis = startMillis;
		}
	}

	@Override
	public boolean tryAcquire(final String key)
	{
		if (requestsPerMinute <= 0)
		{
			return true;
		}
		final long now = System.currentTimeMillis();
		if (windows.size() >= CLEANUP_THRESHOLD)
		{
			windows.values().removeIf(w -> now - w.startMillis >= 60_000L);
		}
		final Window window = windows.compute(key == null ? "anonymous" : key,
			(k, w) -> (w == null || now - w.startMillis >= 60_000L) ? new Window(now) : w);
		return window.count.incrementAndGet() <= requestsPerMinute;
	}

	public void setRequestsPerMinute(final int requestsPerMinute)
	{
		this.requestsPerMinute = requestsPerMinute;
	}
}
