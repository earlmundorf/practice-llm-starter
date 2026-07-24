package com.ucpcommerce.services;

/**
 * Per-operation idempotency store for the checkout mutations (official
 * binding: EVERY mutating call may carry an {@code Idempotency-Key}; an
 * identical retry replays the first response verbatim, a same-key retry with
 * a DIFFERENT payload is a 409 conflict). Records are scoped to the
 * authenticated user and swept with the checkout-session cleanup cron.
 */
public interface UcpIdempotencyService
{
	enum Outcome
	{
		/** Key not seen before — execute and {@link #record}. */
		NEW,
		/** Key seen with the SAME request hash — replay the stored response. */
		REPLAY,
		/** Key seen with a DIFFERENT request hash — 409 conflict. */
		CONFLICT
	}

	/** Consultation result: the outcome plus the stored response for REPLAY. */
	final class Consultation
	{
		private final Outcome outcome;
		private final String responseJson;

		public Consultation(final Outcome outcome, final String responseJson)
		{
			this.outcome = outcome;
			this.responseJson = responseJson;
		}

		public Outcome getOutcome()
		{
			return outcome;
		}

		public String getResponseJson()
		{
			return responseJson;
		}
	}

	/**
	 * Look up the (current user, operation, key) record and compare hashes.
	 *
	 * @param operation      operation discriminator (e.g. {@code create},
	 *                       {@code update:ucp_chk_x}, {@code complete:ucp_chk_x})
	 * @param idempotencyKey the client's key (never null here)
	 * @param requestHash    canonical request payload hash
	 */
	Consultation consult(String operation, String idempotencyKey, String requestHash);

	/**
	 * Persist the accepted call. {@code responseJson} may be null when replay
	 * is served elsewhere (complete replays from the checkout session entry).
	 * Insert races with another node lose silently — first writer wins.
	 */
	void record(String operation, String idempotencyKey, String requestHash, String responseJson);
}
