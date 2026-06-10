package com.coremcp.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentStateSnapshotBuilder;

import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.user.data.CustomerData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link AgentStateSnapshotBuilder}: serializes the current customer and
 * session cart into a compact JSON system message. All lookups are best-effort —
 * a failing facade degrades to a smaller snapshot, never a failed turn.
 */
public class DefaultAgentStateSnapshotBuilder implements AgentStateSnapshotBuilder
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentStateSnapshotBuilder.class);

	private CartFacade cartFacade;
	private CustomerFacade customerFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String buildStateSnapshotMessage()
	{
		final Map<String, Object> state = new LinkedHashMap<>();

		try
		{
			final CustomerData customer = customerFacade.getCurrentCustomer();
			if (customer != null)
			{
				final Map<String, Object> customerData = new LinkedHashMap<>();
				customerData.put("uid", customer.getUid());
				customerData.put("name", customer.getName());
				state.put("customer", customerData);
			}
		}
		catch (final Exception e)
		{
			LOG.debug("Could not build customer snapshot: {}", e.getMessage());
		}

		try
		{
			if (cartFacade.hasSessionCart())
			{
				final CartData cart = cartFacade.getSessionCart();
				final Map<String, Object> cartSnap = new LinkedHashMap<>();
				cartSnap.put("code", cart.getCode());
				cartSnap.put("totalItems", cart.getTotalItems());
				if (cart.getSubTotal() != null)
				{
					cartSnap.put("subtotal", cart.getSubTotal().getValue());
				}
				if (cart.getTotalDiscounts() != null)
				{
					cartSnap.put("discounts", cart.getTotalDiscounts().getValue());
				}
				if (cart.getTotalPrice() != null)
				{
					cartSnap.put("total", cart.getTotalPrice().getValue());
				}

				final List<Map<String, Object>> entries = new ArrayList<>();
				if (cart.getEntries() != null)
				{
					for (final OrderEntryData entry : cart.getEntries())
					{
						final Map<String, Object> e = new LinkedHashMap<>();
						if (entry.getProduct() != null)
						{
							e.put("productCode", entry.getProduct().getCode());
							e.put("name", entry.getProduct().getName());
						}
						e.put("qty", entry.getQuantity());
						if (entry.getTotalPrice() != null)
						{
							e.put("lineTotal", entry.getTotalPrice().getValue());
						}
						entries.add(e);
					}
				}
				cartSnap.put("entries", entries);

				if (cart.getAppliedVouchers() != null && !cart.getAppliedVouchers().isEmpty())
				{
					cartSnap.put("appliedVouchers", cart.getAppliedVouchers());
				}

				state.put("cart", cartSnap);
			}
		}
		catch (final Exception e)
		{
			LOG.debug("Could not build cart snapshot: {}", e.getMessage());
		}

		String stateJson;
		try
		{
			stateJson = objectMapper.writeValueAsString(state);
		}
		catch (final Exception e)
		{
			LOG.warn("Could not serialize state snapshot, falling back to empty: {}", e.getMessage());
			stateJson = "{}";
		}

		return "CURRENT STATE (refreshed each turn — use these values directly; do not call cart_get or "
			+ "customer_get just to look up basics already provided here):\n" + stateJson;
	}

	@Required
	public void setCartFacade(final CartFacade cartFacade)
	{
		this.cartFacade = cartFacade;
	}

	@Required
	public void setCustomerFacade(final CustomerFacade customerFacade)
	{
		this.customerFacade = customerFacade;
	}
}
