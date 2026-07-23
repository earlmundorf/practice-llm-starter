package com.ucpcommerce.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.services.UcpCheckoutService;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * Default UCP checkout capability. Statelessness contract: the UCP binding
 * carries no transport session, so every operation on an existing checkout
 * resolves the opaque id via {@link UcpCheckoutSessionService}, loads the
 * backing cart into the hybris thread-local session with the same
 * {@link CartLoaderStrategy} bridge coremcp uses, performs facade calls, and
 * persists protocol state back onto the entry (design S2 bracket).
 *
 * Business failures (unknown SKU, unknown id, stock) are returned inside the
 * checkout payload as {@code messages[]} — never thrown, never a 500.
 */
public class DefaultUcpCheckoutService implements UcpCheckoutService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpCheckoutService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private CartFacade cartFacade;
	private CartLoaderStrategy cartLoaderStrategy;
	private UcpCheckoutSessionService ucpCheckoutSessionService;
	private UcpCheckoutMarshaller ucpCheckoutMarshaller;

	@Override
	public UcpCheckout create(final UcpCheckoutRequest payload)
	{
		if (payload == null || payload.getLineItems() == null || payload.getLineItems().isEmpty())
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_UNRECOVERABLE, "checkout.line_items must contain at least one item")));
		}

		final List<UcpMessage> messages = new ArrayList<>();
		boolean anyAdded = false;
		for (final UcpLineItemRequest lineItem : payload.getLineItems())
		{
			anyAdded |= addLineItem(lineItem, messages);
		}

		if (!anyAdded)
		{
			// No cart was created — terminal for this create request.
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_UNRECOVERABLE,
				"No line items could be added; checkout not created"));
			return ucpCheckoutMarshaller.error(messages);
		}

		// The first successful addToCart created the session cart implicitly.
		final CartData cart = cartFacade.getSessionCart();
		final UcpBuyer buyer = payload.getBuyer();
		final UcpCheckoutSession session = ucpCheckoutSessionService.create(
			cart.getCode(), UcpCheckout.STATUS_INCOMPLETE, toJson(buyer));
		LOG.info("UCP create_checkout: {} → cart {} ({} entries)", session.getCheckoutId(), cart.getCode(),
			cart.getEntries() == null ? 0 : cart.getEntries().size());
		return ucpCheckoutMarshaller.marshal(session.getCheckoutId(), UcpCheckout.STATUS_INCOMPLETE,
			cart, buyer, messages);
	}

	@Override
	public UcpCheckout get(final String checkoutId)
	{
		final UcpCheckoutSession session = ucpCheckoutSessionService.get(checkoutId);
		if (session == null)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown or expired checkout id: " + checkoutId)));
		}
		try
		{
			cartLoaderStrategy.loadCart(session.getCartCode());
		}
		catch (final Exception e)
		{
			// Cart vanished underneath the entry (e.g. platform cart cleanup) —
			// the checkout is effectively gone; still a payload, never a 500.
			LOG.warn("UCP get_checkout {}: backing cart {} could not be loaded: {}",
				checkoutId, session.getCartCode(), e.getMessage());
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Checkout " + checkoutId + " is no longer available")));
		}
		return ucpCheckoutMarshaller.marshal(session.getCheckoutId(), session.getStatus(),
			cartFacade.getSessionCart(), parseBuyer(session.getBuyerJson()), List.of());
	}

	/**
	 * Add one requested line item to the session cart; problems become
	 * messages (per-line misses are recoverable — the rest of the request
	 * still applies).
	 *
	 * @return true when at least one unit was added
	 */
	protected boolean addLineItem(final UcpLineItemRequest lineItem, final List<UcpMessage> messages)
	{
		final String itemId = lineItem != null && lineItem.getItem() != null ? lineItem.getItem().getId() : null;
		if (itemId == null || itemId.isBlank())
		{
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"Line item is missing item.id"));
			return false;
		}
		final long quantity = lineItem.getQuantity() != null ? lineItem.getQuantity() : 1L;
		try
		{
			final CartModificationData modification = cartFacade.addToCart(itemId, quantity);
			final long added = modification != null ? modification.getQuantityAdded() : 0L;
			if (added <= 0)
			{
				messages.add(new UcpMessage("error", "out_of_stock", UcpMessage.SEVERITY_RECOVERABLE,
					"Item " + itemId + " could not be added (out of stock)"));
				return false;
			}
			if (added < quantity)
			{
				messages.add(new UcpMessage("warning", "out_of_stock", UcpMessage.SEVERITY_RECOVERABLE,
					"Item " + itemId + ": only " + added + " of " + quantity + " units could be added"));
			}
			return true;
		}
		catch (final Exception e)
		{
			LOG.debug("UCP create_checkout: could not add item {}: {}", itemId, e.getMessage());
			messages.add(new UcpMessage("error", "not_found", UcpMessage.SEVERITY_RECOVERABLE,
				"Unknown or unavailable item: " + itemId));
			return false;
		}
	}

	private String toJson(final UcpBuyer buyer)
	{
		if (buyer == null)
		{
			return null;
		}
		try
		{
			return objectMapper.writeValueAsString(buyer);
		}
		catch (final Exception e)
		{
			LOG.warn("Could not serialize UCP buyer, storing none: {}", e.getMessage());
			return null;
		}
	}

	private UcpBuyer parseBuyer(final String buyerJson)
	{
		if (buyerJson == null || buyerJson.isBlank())
		{
			return null;
		}
		try
		{
			return objectMapper.readValue(buyerJson, UcpBuyer.class);
		}
		catch (final Exception e)
		{
			LOG.warn("Could not parse stored UCP buyer, returning none: {}", e.getMessage());
			return null;
		}
	}

	@Required
	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}

	@Required
	public void setCartLoaderStrategy(final CartLoaderStrategy cartLoaderStrategy)
	{
		this.cartLoaderStrategy = cartLoaderStrategy;
	}

	@Required
	public void setUcpCheckoutSessionService(final UcpCheckoutSessionService ucpCheckoutSessionService)
	{
		this.ucpCheckoutSessionService = ucpCheckoutSessionService;
	}

	@Required
	public void setUcpCheckoutMarshaller(final UcpCheckoutMarshaller ucpCheckoutMarshaller)
	{
		this.ucpCheckoutMarshaller = ucpCheckoutMarshaller;
	}
}
