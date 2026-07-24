package com.ucpcommerce.services.impl;

import com.coremcp.services.DeepLinkBuilder;
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpOrder;
import com.ucpcommerce.dto.UcpOrderLineItem;
import com.ucpcommerce.dto.UcpTotal;

import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderHistoryData;

import org.springframework.beans.factory.annotation.Required;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Marshals hybris order data into UCP {@code order} objects. Three shapes:
 * <ul>
 *   <li>{@link #marshal(OrderData)} — the deliberately minimal
 *       ({@code id} + {@code created_at}) block embedded in a completed
 *       checkout (design S3). Kept minimal so Phase 5's stored completion
 *       responses replay unchanged;</li>
 *   <li>{@link #marshalFull(OrderData)} — the full UCP order schema for the
 *       order capability's {@code get_order}: status, currency, line items,
 *       totals and fulfillment, reusing {@link UcpCheckoutMarshaller}'s
 *       line-item/totals/fulfillment mapping (it accepts
 *       {@code AbstractOrderData} for exactly this);</li>
 *   <li>{@link #marshalSummary(OrderHistoryData)} — one {@code list_orders}
 *       history entry: id, created_at, status and the order total.</li>
 * </ul>
 * All money crosses the major→minor boundary only via the centralized
 * {@link UcpMoneyConverter}.
 */
public class UcpOrderMarshaller
{
	private UcpCheckoutMarshaller ucpCheckoutMarshaller;
	private UcpMoneyConverter ucpMoneyConverter;
	private DeepLinkBuilder deepLinkBuilder;

	/**
	 * Minimal embedded order block ({@code id} + {@code created_at} +
	 * {@code permalink_url}). The permalink is REQUIRED by the spec's
	 * OrderConfirmation (the reference client reads it after complete — ADR
	 * 0003) and points at the ThinkShop storefront order page via coremcp's
	 * {@link DeepLinkBuilder}. Completion responses stored before the field
	 * existed replay without it.
	 */
	public UcpOrder marshal(final OrderData orderData)
	{
		if (orderData == null)
		{
			return null;
		}
		final UcpOrder order = new UcpOrder();
		order.setId(orderData.getCode());
		order.setCreatedAt(iso(orderData.getCreated()));
		order.setPermalinkUrl(deepLinkBuilder.orderUrl(orderData.getCode()));
		return order;
	}

	/**
	 * Full UCP order schema for the order capability ({@code get_order}).
	 *
	 * @param orderData  the loaded hybris order
	 * @param checkoutId the UCP checkout session the order was placed from,
	 *                   or null when unknown (legacy orders / swept session) —
	 *                   {@code checkout_id} is then omitted
	 */
	public UcpOrder marshalFull(final OrderData orderData, final String checkoutId)
	{
		final UcpOrder order = marshal(orderData);
		if (order == null)
		{
			return null;
		}
		order.setCheckoutId(checkoutId);
		order.setStatus(wireStatus(orderData.getStatus() != null ? orderData.getStatus().getCode() : null));
		if (orderData.getTotalPrice() != null && orderData.getTotalPrice().getCurrencyIso() != null)
		{
			order.setCurrency(orderData.getTotalPrice().getCurrencyIso());
		}
		else if (orderData.getSubTotal() != null)
		{
			order.setCurrency(orderData.getSubTotal().getCurrencyIso());
		}
		// Same-package reuse of the checkout marshaller's AbstractOrderData
		// mapping — one line-item/totals implementation for both checkout and
		// order payloads; lines are then lifted to the ORDER line-item shape
		// (quantity object + required derived status, order_line_item.json).
		order.setLineItems(toOrderLineItems(ucpCheckoutMarshaller.marshalLineItems(orderData)));
		order.setTotals(ucpCheckoutMarshaller.marshalTotals(orderData));
		// order.json requires fulfillment — an empty object when the order
		// carries no address/mode (its expectations/events are all optional).
		final UcpFulfillment fulfillment = ucpCheckoutMarshaller.marshalFulfillment(orderData);
		order.setFulfillment(fulfillment != null ? fulfillment : new UcpFulfillment());
		return order;
	}

	/**
	 * Lift checkout-shaped lines to order lines: quantity becomes the
	 * {@code {original,total,fulfilled}} block and every line is
	 * {@code processing} (no fulfillment process runs on this demo platform).
	 */
	protected List<UcpOrderLineItem> toOrderLineItems(final List<UcpLineItem> lineItems)
	{
		final List<UcpOrderLineItem> orderLines = new ArrayList<>();
		for (final UcpLineItem line : lineItems)
		{
			final UcpOrderLineItem orderLine = new UcpOrderLineItem();
			orderLine.setId(line.getId());
			orderLine.setItem(line.getItem());
			orderLine.setTotals(line.getTotals());
			orderLine.setQuantity(new UcpOrderLineItem.Quantity(line.getQuantity(), line.getQuantity(), 0L));
			orderLine.setStatus(UcpOrderLineItem.STATUS_PROCESSING);
			orderLines.add(orderLine);
		}
		return orderLines;
	}

	/** One order-history entry for {@code list_orders}. */
	public UcpOrder marshalSummary(final OrderHistoryData historyData)
	{
		if (historyData == null)
		{
			return null;
		}
		final UcpOrder order = new UcpOrder();
		order.setId(historyData.getCode());
		order.setCreatedAt(iso(historyData.getPlaced()));
		order.setPermalinkUrl(deepLinkBuilder.orderUrl(historyData.getCode()));
		order.setStatus(wireStatus(historyData.getStatus() != null ? historyData.getStatus().getCode() : null));
		if (historyData.getTotal() != null && historyData.getTotal().getValue() != null)
		{
			order.setCurrency(historyData.getTotal().getCurrencyIso());
			order.setTotals(List.of(new UcpTotal(UcpTotal.TYPE_TOTAL, ucpMoneyConverter.toMinorUnits(
				historyData.getTotal().getValue(), historyData.getTotal().getCurrencyIso()))));
		}
		return order;
	}

	/**
	 * Hybris order-status codes are UPPERCASE; the UCP wire style is lowercase.
	 * Orders placed through the mock {@code placeOrder} path carry no hybris
	 * status at all (no fulfillment process runs on this demo platform) — a
	 * just-placed order is {@code created} on the UCP wire (the mandatory first
	 * order-lifecycle event in the spec).
	 */
	protected String wireStatus(final String hybrisStatusCode)
	{
		return hybrisStatusCode == null ? "created" : hybrisStatusCode.toLowerCase(Locale.ROOT);
	}

	private static String iso(final Date date)
	{
		return date == null ? null : DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
	}

	@Required
	public void setUcpCheckoutMarshaller(final UcpCheckoutMarshaller ucpCheckoutMarshaller)
	{
		this.ucpCheckoutMarshaller = ucpCheckoutMarshaller;
	}

	@Required
	public void setUcpMoneyConverter(final UcpMoneyConverter ucpMoneyConverter)
	{
		this.ucpMoneyConverter = ucpMoneyConverter;
	}

	@Required
	public void setDeepLinkBuilder(final DeepLinkBuilder deepLinkBuilder)
	{
		this.deepLinkBuilder = deepLinkBuilder;
	}
}
