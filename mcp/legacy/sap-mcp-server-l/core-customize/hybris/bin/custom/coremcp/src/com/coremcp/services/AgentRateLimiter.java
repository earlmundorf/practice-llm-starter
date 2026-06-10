package com.coremcp.services;

/**
 * Per-caller rate limiting for the agent endpoints. Every /agent/* request triggers
 * at least one paid LLM round-trip, so even authenticated callers get a ceiling.
 */
public interface AgentRateLimiter
{
	/**
	 * @param key caller identity (typically the hybris user uid)
	 * @return true if the request is within the caller's budget, false if it should be rejected (429)
	 */
	boolean tryAcquire(String key);
}
