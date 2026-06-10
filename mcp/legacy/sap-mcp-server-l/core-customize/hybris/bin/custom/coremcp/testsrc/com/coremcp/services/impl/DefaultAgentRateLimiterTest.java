package com.coremcp.services.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class DefaultAgentRateLimiterTest
{
	private DefaultAgentRateLimiter rateLimiter;

	@Before
	public void setUp()
	{
		rateLimiter = new DefaultAgentRateLimiter();
		rateLimiter.setRequestsPerMinute(3);
	}

	@Test
	public void allowsRequestsUpToLimit()
	{
		assertTrue(rateLimiter.tryAcquire("user1"));
		assertTrue(rateLimiter.tryAcquire("user1"));
		assertTrue(rateLimiter.tryAcquire("user1"));
	}

	@Test
	public void rejectsRequestsOverLimit()
	{
		for (int i = 0; i < 3; i++)
		{
			assertTrue(rateLimiter.tryAcquire("user1"));
		}
		assertFalse(rateLimiter.tryAcquire("user1"));
		assertFalse(rateLimiter.tryAcquire("user1"));
	}

	@Test
	public void limitsAreTrackedPerCaller()
	{
		for (int i = 0; i < 3; i++)
		{
			assertTrue(rateLimiter.tryAcquire("user1"));
		}
		assertFalse(rateLimiter.tryAcquire("user1"));
		assertTrue(rateLimiter.tryAcquire("user2"));
	}

	@Test
	public void zeroLimitDisablesRateLimiting()
	{
		rateLimiter.setRequestsPerMinute(0);
		for (int i = 0; i < 100; i++)
		{
			assertTrue(rateLimiter.tryAcquire("user1"));
		}
	}

	@Test
	public void nullKeyIsTreatedAsSingleAnonymousCaller()
	{
		for (int i = 0; i < 3; i++)
		{
			assertTrue(rateLimiter.tryAcquire(null));
		}
		assertFalse(rateLimiter.tryAcquire(null));
	}
}
