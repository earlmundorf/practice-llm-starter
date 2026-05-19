package com.coremcp.services.impl;

import com.coremcp.services.McpCartSessionService;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.CartService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link McpCartSessionService}.
 * Wraps CartLoaderStrategy and CartService to provide cart session management
 * for MCP and Agent controllers.
 */
public class DefaultMcpCartSessionService implements McpCartSessionService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpCartSessionService.class);

	private CartLoaderStrategy cartLoaderStrategy;
	private CartService cartService;

	@Override
	public void loadCart(final String cartCode)
	{
		if (cartCode == null)
		{
			return;
		}
		try
		{
			cartLoaderStrategy.loadCart(cartCode);
		}
		catch (final Exception e)
		{
			LOG.debug("Could not load cart {}: {}", cartCode, e.getMessage());
		}
	}

	@Override
	public void loadCartOrCurrent(final String cartCode)
	{
		final String code = (cartCode != null && !cartCode.isEmpty()) ? cartCode : "current";
		try
		{
			cartLoaderStrategy.loadCart(code);
		}
		catch (final Exception e)
		{
			LOG.debug("No existing cart to load ({}): {}", code, e.getMessage());
		}
	}

	@Override
	public String getSessionCartCode()
	{
		try
		{
			if (cartService.hasSessionCart())
			{
				return cartService.getSessionCart().getCode();
			}
		}
		catch (final Exception e)
		{
			LOG.debug("Could not get session cart code: {}", e.getMessage());
		}
		return null;
	}

	public void setCartLoaderStrategy(final CartLoaderStrategy cartLoaderStrategy)
	{
		this.cartLoaderStrategy = cartLoaderStrategy;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}
}
