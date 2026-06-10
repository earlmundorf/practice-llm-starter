package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hybris.bootstrap.annotations.UnitTest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class AbstractHttpLlmProviderTest
{
	private HttpClient httpClient;
	private TestProvider provider;
	private HttpRequest request;

	/** Minimal concrete provider exposing the base retry plumbing. */
	private static class TestProvider extends AbstractHttpLlmProvider
	{
		private final HttpClient client;

		TestProvider(final HttpClient client)
		{
			this.client = client;
		}

		@Override
		protected HttpClient createHttpClient()
		{
			return client;
		}

		@Override
		public String getProviderId()
		{
			return "test";
		}

		@Override
		public Map<String, Object> chatCompletion(final List<Map<String, Object>> messages,
			final List<Map<String, Object>> tools, final String modelOverride)
		{
			return Map.of();
		}
	}

	@Before
	public void setUp()
	{
		httpClient = mock(HttpClient.class);
		provider = new TestProvider(httpClient);
		provider.setRetryMaxAttempts(3);
		provider.setRetryBaseDelayMillis(0); // no sleeping in tests
		request = HttpRequest.newBuilder().uri(URI.create("http://localhost/test")).GET().build();
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> response(final int status)
	{
		final HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn("body-" + status);
		return response;
	}

	@Test
	public void retriesRetryableStatusThenReturnsSuccess() throws Exception
	{
		// Mocks must be created BEFORE when(...) — creating them inside thenReturn args
		// nests stubbing and trips Mockito's UnfinishedStubbingException.
		final HttpResponse<String> tooMany = response(429);
		final HttpResponse<String> unavailable = response(503);
		final HttpResponse<String> ok = response(200);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
			.thenReturn(tooMany, unavailable, ok);

		final HttpResponse<String> result = provider.sendWithRetry(request);

		assertEquals(200, result.statusCode());
		verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	public void returnsLastResponseWhenRetriesExhausted() throws Exception
	{
		final HttpResponse<String> first = response(429);
		final HttpResponse<String> second = response(429);
		final HttpResponse<String> third = response(429);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
			.thenReturn(first, second, third);

		final HttpResponse<String> result = provider.sendWithRetry(request);

		assertEquals(429, result.statusCode());
		verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	public void doesNotRetryNonRetryableStatus() throws Exception
	{
		final HttpResponse<String> badRequest = response(400);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
			.thenReturn(badRequest);

		final HttpResponse<String> result = provider.sendWithRetry(request);

		assertEquals(400, result.statusCode());
		verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	public void retriesIoExceptionThenSucceeds() throws Exception
	{
		final HttpResponse<String> ok = response(200);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
			.thenThrow(new IOException("connection reset"))
			.thenReturn(ok);

		final HttpResponse<String> result = provider.sendWithRetry(request);

		assertEquals(200, result.statusCode());
		verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	public void throwsAfterIoExceptionExhaustsAttempts() throws Exception
	{
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
			.thenThrow(new IOException("down"));

		try
		{
			provider.sendWithRetry(request);
			fail("expected IOException");
		}
		catch (final IOException expected)
		{
			assertEquals("down", expected.getMessage());
		}
		verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	public void requireOkThrowsWithStatusAndBody()
	{
		try
		{
			provider.requireOk(response(503));
			fail("expected RuntimeException");
		}
		catch (final RuntimeException expected)
		{
			assertTrue(expected.getMessage().contains("503"));
			assertTrue(expected.getMessage().contains("body-503"));
		}
	}

	@Test
	public void retryableStatusClassification()
	{
		assertTrue(AbstractHttpLlmProvider.isRetryableStatus(429));
		assertTrue(AbstractHttpLlmProvider.isRetryableStatus(500));
		assertTrue(AbstractHttpLlmProvider.isRetryableStatus(502));
		assertTrue(AbstractHttpLlmProvider.isRetryableStatus(503));
		assertFalse(AbstractHttpLlmProvider.isRetryableStatus(200));
		assertFalse(AbstractHttpLlmProvider.isRetryableStatus(400));
		assertFalse(AbstractHttpLlmProvider.isRetryableStatus(401));
		assertFalse(AbstractHttpLlmProvider.isRetryableStatus(404));
	}

	@Test
	public void backoffGrowsExponentially()
	{
		provider.setRetryBaseDelayMillis(100);
		// attempt 2: 100..200, attempt 3: 200..300, attempt 4: 400..500 (jitter adds 0..base)
		final long second = provider.computeBackoffMillis(2);
		final long third = provider.computeBackoffMillis(3);
		final long fourth = provider.computeBackoffMillis(4);
		assertTrue(second >= 100 && second <= 200);
		assertTrue(third >= 200 && third <= 300);
		assertTrue(fourth >= 400 && fourth <= 500);
	}
}
