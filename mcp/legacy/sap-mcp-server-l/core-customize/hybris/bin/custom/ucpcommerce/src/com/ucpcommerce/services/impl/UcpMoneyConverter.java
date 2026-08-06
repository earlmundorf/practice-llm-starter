package com.ucpcommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * The single money boundary between hybris ({@code BigDecimal} major units)
 * and UCP (integer minor units, e.g. $12.99 → 1299).
 *
 * Every UCP marshaller must use this converter — an ad-hoc {@code ×100}
 * anywhere else is exactly the silent 100× pricing bug the runbook warns
 * about (§2.2), and it breaks for 0-digit (JPY) and 3-digit (BHD) currencies.
 */
public class UcpMoneyConverter
{
	private static final int DEFAULT_FRACTION_DIGITS = 2;

	/**
	 * Convert major units to integer minor units, currency-digit aware.
	 * Sub-minor-unit precision is rounded HALF_UP.
	 *
	 * @return minor units, or null when {@code major} is null
	 */
	public Long toMinorUnits(final BigDecimal major, final String currencyIso)
	{
		if (major == null)
		{
			return null;
		}
		return major.movePointRight(fractionDigits(currencyIso))
			.setScale(0, RoundingMode.HALF_UP)
			.longValueExact();
	}

	/** Convert integer minor units back to major units, currency-digit aware. */
	public BigDecimal toMajorUnits(final long minor, final String currencyIso)
	{
		return BigDecimal.valueOf(minor).movePointLeft(fractionDigits(currencyIso));
	}

	/**
	 * ISO-4217 fraction digits for the currency; falls back to 2 for null,
	 * unknown, or pseudo currencies (which report -1).
	 */
	protected int fractionDigits(final String currencyIso)
	{
		if (currencyIso == null || currencyIso.isBlank())
		{
			return DEFAULT_FRACTION_DIGITS;
		}
		try
		{
			final int digits = Currency.getInstance(currencyIso).getDefaultFractionDigits();
			return digits < 0 ? DEFAULT_FRACTION_DIGITS : digits;
		}
		catch (final IllegalArgumentException e)
		{
			return DEFAULT_FRACTION_DIGITS;
		}
	}
}
