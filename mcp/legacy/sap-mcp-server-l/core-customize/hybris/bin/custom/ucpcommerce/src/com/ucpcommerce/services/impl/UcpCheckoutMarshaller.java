package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpProduct;
import com.ucpcommerce.dto.UcpTotal;

import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.util.Config;

import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * Marshals a hybris {@code CartData} into the UCP {@code checkout} object
 * (runbook §2.2 / §3.5 field mapping). All money crosses the major→minor
 * boundary here, and only via the centralized {@link UcpMoneyConverter}
 * (the silent-100×-bug guard).
 *
 * Accepts {@link AbstractOrderData} so Phase 5 can reuse the line-item/totals
 * mapping for the completed checkout's embedded order.
 */
public class UcpCheckoutMarshaller
{
	private UcpMoneyConverter ucpMoneyConverter;

	/**
	 * Marshal a cart into a success-enveloped checkout object.
	 *
	 * @param checkoutId the opaque UCP checkout id
	 * @param status     derived UCP status code (S5)
	 * @param cart       the loaded session cart
	 * @param buyer      stored buyer block, or null
	 * @param messages   accumulated business messages (may be null/empty)
	 */
	public UcpCheckout marshal(final String checkoutId, final String status, final AbstractOrderData cart,
		final UcpBuyer buyer, final List<UcpMessage> messages)
	{
		final UcpCheckout checkout = new UcpCheckout();
		checkout.setUcp(envelope("success"));
		checkout.setId(checkoutId);
		checkout.setStatus(status);
		checkout.setCurrency(currencyOf(cart));
		checkout.setLineItems(marshalLineItems(cart));
		checkout.setTotals(marshalTotals(cart));
		checkout.setBuyer(buyer);
		if (messages != null && !messages.isEmpty())
		{
			checkout.setMessages(messages);
		}
		return checkout;
	}

	/**
	 * A business-error checkout payload: {@code ucp.status="error"} +
	 * {@code messages[]}, no checkout body — never a 500/transport error.
	 */
	public UcpCheckout error(final List<UcpMessage> messages)
	{
		final UcpCheckout checkout = new UcpCheckout();
		checkout.setUcp(envelope("error"));
		checkout.setMessages(messages);
		return checkout;
	}

	protected List<UcpLineItem> marshalLineItems(final AbstractOrderData cart)
	{
		final List<UcpLineItem> lineItems = new ArrayList<>();
		if (cart == null || cart.getEntries() == null)
		{
			return lineItems;
		}
		for (final OrderEntryData entry : cart.getEntries())
		{
			final UcpLineItem lineItem = new UcpLineItem();
			lineItem.setId("li_" + entry.getEntryNumber());
			lineItem.setQuantity(entry.getQuantity());

			final UcpProduct item = new UcpProduct();
			if (entry.getProduct() != null)
			{
				item.setId(entry.getProduct().getCode());
				item.setTitle(entry.getProduct().getName());
			}
			if (entry.getBasePrice() != null)
			{
				item.setPrice(minor(entry.getBasePrice()));
				item.setCurrency(entry.getBasePrice().getCurrencyIso());
			}
			lineItem.setItem(item);

			if (entry.getTotalPrice() != null)
			{
				lineItem.setTotals(List.of(new UcpTotal(UcpTotal.TYPE_SUBTOTAL, minor(entry.getTotalPrice()))));
			}
			lineItems.add(lineItem);
		}
		return lineItems;
	}

	protected List<UcpTotal> marshalTotals(final AbstractOrderData cart)
	{
		final List<UcpTotal> totals = new ArrayList<>();
		if (cart == null)
		{
			return totals;
		}
		addTotal(totals, UcpTotal.TYPE_SUBTOTAL, cart.getSubTotal(), true);
		// Discounts appear once Drools promotions fire (Phase 4 asserts this);
		// reported as a positive amount under type "discount".
		addTotal(totals, UcpTotal.TYPE_DISCOUNT, cart.getTotalDiscounts(), false);
		addTotal(totals, UcpTotal.TYPE_TAX, cart.getTotalTax(), false);
		addTotal(totals, UcpTotal.TYPE_SHIPPING, cart.getDeliveryCost(), false);
		addTotal(totals, UcpTotal.TYPE_TOTAL, cart.getTotalPrice(), true);
		return totals;
	}

	/** Adds a totals entry; optional zero-valued entries are suppressed. */
	private void addTotal(final List<UcpTotal> totals, final String type, final PriceData price,
		final boolean includeWhenZero)
	{
		final Long amount = minor(price);
		if (amount == null || (amount == 0L && !includeWhenZero))
		{
			return;
		}
		totals.add(new UcpTotal(type, amount));
	}

	private Long minor(final PriceData price)
	{
		if (price == null || price.getValue() == null)
		{
			return null;
		}
		return ucpMoneyConverter.toMinorUnits(price.getValue(), price.getCurrencyIso());
	}

	private String currencyOf(final AbstractOrderData cart)
	{
		if (cart == null)
		{
			return null;
		}
		if (cart.getTotalPrice() != null && cart.getTotalPrice().getCurrencyIso() != null)
		{
			return cart.getTotalPrice().getCurrencyIso();
		}
		return cart.getSubTotal() != null ? cart.getSubTotal().getCurrencyIso() : null;
	}

	protected UcpEnvelope envelope(final String status)
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus(status);
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setUcpMoneyConverter(final UcpMoneyConverter ucpMoneyConverter)
	{
		this.ucpMoneyConverter = ucpMoneyConverter;
	}
}
