package com.ucpcommerce.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.hybris.bootstrap.annotations.UnitTest;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;


@UnitTest
public class UcpMoneyConverterTest
{
	private UcpMoneyConverter converter;

	@Before
	public void setUp()
	{
		converter = new UcpMoneyConverter();
	}

	@Test
	public void testTwoDigitCurrencyToMinorUnits()
	{
		assertEquals(Long.valueOf(129999L), converter.toMinorUnits(new BigDecimal("1299.99"), "USD"));
		assertEquals(Long.valueOf(1299L), converter.toMinorUnits(new BigDecimal("12.99"), "USD"));
		assertEquals(Long.valueOf(0L), converter.toMinorUnits(BigDecimal.ZERO, "USD"));
	}

	@Test
	public void testZeroDigitCurrencyToMinorUnits()
	{
		// JPY has no minor unit — major units ARE minor units.
		assertEquals(Long.valueOf(1234L), converter.toMinorUnits(new BigDecimal("1234"), "JPY"));
	}

	@Test
	public void testThreeDigitCurrencyToMinorUnits()
	{
		// BHD uses 3 fraction digits (fils).
		assertEquals(Long.valueOf(1234L), converter.toMinorUnits(new BigDecimal("1.234"), "BHD"));
	}

	@Test
	public void testSubMinorPrecisionRoundsHalfUp()
	{
		assertEquals(Long.valueOf(1001L), converter.toMinorUnits(new BigDecimal("10.005"), "USD"));
		assertEquals(Long.valueOf(1000L), converter.toMinorUnits(new BigDecimal("10.004"), "USD"));
	}

	@Test
	public void testNullMajorReturnsNull()
	{
		assertNull(converter.toMinorUnits(null, "USD"));
	}

	@Test
	public void testUnknownOrPseudoCurrencyFallsBackToTwoDigits()
	{
		// XXX (ISO "no currency") reports -1 fraction digits; garbage isn't a valid ISO code.
		assertEquals(Long.valueOf(150L), converter.toMinorUnits(new BigDecimal("1.50"), "XXX"));
		assertEquals(Long.valueOf(150L), converter.toMinorUnits(new BigDecimal("1.50"), "NOPE"));
		assertEquals(Long.valueOf(150L), converter.toMinorUnits(new BigDecimal("1.50"), null));
	}

	@Test
	public void testToMajorUnitsRoundTrips()
	{
		assertEquals(0, new BigDecimal("1299.99").compareTo(converter.toMajorUnits(129999L, "USD")));
		assertEquals(0, new BigDecimal("1234").compareTo(converter.toMajorUnits(1234L, "JPY")));
		assertEquals(0, new BigDecimal("1.234").compareTo(converter.toMajorUnits(1234L, "BHD")));
	}

	@Test
	public void testNegativeAmounts()
	{
		// Discounts/adjustments are negative money — must survive the boundary too.
		assertEquals(Long.valueOf(-500L), converter.toMinorUnits(new BigDecimal("-5.00"), "USD"));
		assertEquals(0, new BigDecimal("-5.00").compareTo(converter.toMajorUnits(-500L, "USD")));
	}
}
