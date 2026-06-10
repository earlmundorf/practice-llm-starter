package com.coremcp.services;

import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeepLinkBuilder
{
	private static final Logger LOG = LoggerFactory.getLogger(DeepLinkBuilder.class);
	private static final String BASE_URL_PROPERTY = "coremcp.storefront.baseUrl";
	private static final String DEFAULT_BASE_URL = "http://localhost:5173";

	private final AtomicBoolean warnedDefaultBaseUrl = new AtomicBoolean();

	public String productUrl(final String code)
	{
		if (code == null || code.isBlank()) return null;
		return baseUrl() + "/products/" + encode(code);
	}

	public String orderUrl(final String code)
	{
		if (code == null || code.isBlank()) return null;
		return baseUrl() + "/orders/" + encode(code);
	}

	public String orderHistoryUrl()
	{
		return baseUrl() + "/orders";
	}

	private String baseUrl()
	{
		String url = Config.getString(BASE_URL_PROPERTY, "");
		if (url.isBlank())
		{
			if (warnedDefaultBaseUrl.compareAndSet(false, true))
			{
				LOG.warn("{} is not set — deep links will point at the dev default {}. "
					+ "Set it for any non-local deployment.", BASE_URL_PROPERTY, DEFAULT_BASE_URL);
			}
			url = DEFAULT_BASE_URL;
		}
		if (url.endsWith("/"))
		{
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private static String encode(final String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
