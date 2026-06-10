package com.coremcp.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.LlmProvider;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for HTTP-backed LLM providers. Owns the shared {@link HttpClient},
 * request timeout configuration, and the transient-failure retry policy so that
 * resilience behavior is identical across providers.
 *
 * Retry policy: {@link #sendWithRetry(HttpRequest)} retries connection-level
 * {@link IOException}s and retryable HTTP statuses (429/500/502/503) with
 * exponential backoff plus jitter, up to {@code retryMaxAttempts} total attempts
 * (defaults injected from coremcp.llm.retry.* properties). Streaming responses
 * must NOT go through this method — once deltas may have been emitted to a
 * consumer, a retry would duplicate output. Streaming callers send once and fall
 * back to the (retried) non-streaming path on failure.
 */
public abstract class AbstractHttpLlmProvider implements LlmProvider
{
	private static final Logger LOG = LoggerFactory.getLogger(AbstractHttpLlmProvider.class);

	protected final ObjectMapper objectMapper = new ObjectMapper();

	private volatile HttpClient httpClient;

	private int retryMaxAttempts = 3;
	private long retryBaseDelayMillis = 500;

	protected HttpClient httpClient()
	{
		HttpClient client = httpClient;
		if (client == null)
		{
			synchronized (this)
			{
				if (httpClient == null)
				{
					httpClient = createHttpClient();
				}
				client = httpClient;
			}
		}
		return client;
	}

	/** Overridable for tests. */
	protected HttpClient createHttpClient()
	{
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	}

	/** Per-request timeout for buffered (non-streaming) calls. */
	protected Duration requestTimeout()
	{
		return Duration.ofSeconds(Config.getInt("coremcp.llm.timeout.seconds", 60));
	}

	/** Per-request timeout for streaming calls (covers connect + response headers). */
	protected Duration streamRequestTimeout()
	{
		return Duration.ofSeconds(Config.getInt("coremcp.llm.stream.timeout.seconds", 120));
	}

	/**
	 * Send a buffered request, retrying transient failures. Returns the final response
	 * (which may still be an error status — callers validate via {@link #requireOk}).
	 */
	protected HttpResponse<String> sendWithRetry(final HttpRequest request)
		throws IOException, InterruptedException
	{
		final int maxAttempts = Math.max(1, retryMaxAttempts);
		IOException lastIoFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++)
		{
			if (attempt > 1)
			{
				sleepBeforeRetry(attempt);
			}
			try
			{
				final HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
				if (attempt < maxAttempts && isRetryableStatus(response.statusCode()))
				{
					LOG.warn("{} returned retryable status {} (attempt {}/{}); retrying",
						getProviderId(), response.statusCode(), attempt, maxAttempts);
					continue;
				}
				return response;
			}
			catch (final IOException e)
			{
				lastIoFailure = e;
				if (attempt == maxAttempts)
				{
					break;
				}
				LOG.warn("{} request failed: {} (attempt {}/{}); retrying",
					getProviderId(), e.getMessage(), attempt, maxAttempts);
			}
		}
		throw lastIoFailure;
	}

	static boolean isRetryableStatus(final int status)
	{
		return status == 429 || status == 500 || status == 502 || status == 503;
	}

	// Package-private — exercised directly by AbstractHttpLlmProviderTest.
	long computeBackoffMillis(final int attempt)
	{
		final long base = Math.max(0, retryBaseDelayMillis);
		if (base == 0)
		{
			return 0;
		}
		// attempt 2 → base, attempt 3 → 2*base, attempt 4 → 4*base ... capped exponent.
		final long exponential = base * (1L << Math.min(Math.max(attempt - 2, 0), 10));
		return exponential + ThreadLocalRandom.current().nextLong(0, base + 1);
	}

	private void sleepBeforeRetry(final int attempt) throws InterruptedException
	{
		final long delay = computeBackoffMillis(attempt);
		if (delay > 0)
		{
			Thread.sleep(delay);
		}
	}

	/** Throw (with the response body in the message) unless the response is HTTP 200. */
	protected void requireOk(final HttpResponse<String> response)
	{
		if (response.statusCode() != 200)
		{
			LOG.error("{} API error ({}): {}", getProviderId(), response.statusCode(), response.body());
			throw new RuntimeException(getProviderId() + " API returned status " + response.statusCode() + ": "
				+ response.body());
		}
	}

	public void setRetryMaxAttempts(final int retryMaxAttempts)
	{
		this.retryMaxAttempts = retryMaxAttempts;
	}

	public void setRetryBaseDelayMillis(final long retryBaseDelayMillis)
	{
		this.retryBaseDelayMillis = retryBaseDelayMillis;
	}
}
