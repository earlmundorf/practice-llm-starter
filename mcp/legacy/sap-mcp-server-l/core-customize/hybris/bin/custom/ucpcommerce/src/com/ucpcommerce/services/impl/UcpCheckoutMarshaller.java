package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpDestination;
import com.ucpcommerce.dto.UcpDiscounts;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpPayment;
import com.ucpcommerce.dto.UcpProduct;
import com.ucpcommerce.dto.UcpTotal;
import com.ucpcommerce.services.UcpProfileService;

import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.user.data.AddressData;
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
	private UcpProfileService ucpProfileService;

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
		checkout.setFulfillment(marshalFulfillment(cart));
		checkout.setDiscounts(marshalDiscounts(cart));
		// Base-schema conformance (ADR 0003): links is REQUIRED (empty like the
		// sample server), and payment is echoed on every response because the
		// reference client feeds response.payment into its next request. The
		// service overlays the request's instruments when there are any.
		checkout.setLinks(List.of());
		checkout.setPayment(new UcpPayment(List.of()));
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
				// Per-line breakdown (the SDK LineItem requires one): subtotal is
				// the PRE-DISCOUNT extended amount (unit price × quantity) so the
				// entries sum — hybris's entry.totalPrice is already net of
				// entry-level promotion discounts (the BOGO double-count bug).
				final Long lineTotal = minor(entry.getTotalPrice());
				final Long extended = extendedSubtotal(entry);
				final List<UcpTotal> lineTotals = new ArrayList<>();
				lineTotals.add(new UcpTotal(UcpTotal.TYPE_SUBTOTAL, extended != null ? extended : lineTotal));
				if (extended != null && lineTotal != null && !extended.equals(lineTotal))
				{
					lineTotals.add(new UcpTotal(UcpTotal.TYPE_DISCOUNT, lineTotal - extended));
				}
				// total is emitted LAST — clients read totals[-1].
				lineTotals.add(new UcpTotal(UcpTotal.TYPE_TOTAL, lineTotal));
				lineItem.setTotals(lineTotals);
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
		// Wire subtotal is the pre-discount item sum (official total.json
		// semantics: subtotal + discount + tax + fulfillment = total). Hybris's
		// cart.subTotal is Σ entry.totalPrice, which entry-level promotion
		// discounts have ALREADY reduced — emitting it next to totalDiscounts
		// (which includes those same discounts) double-counts and the block no
		// longer sums. Falls back to cart.subTotal when a unit price is missing.
		final Long itemsSubtotal = itemsSubtotal(cart);
		if (itemsSubtotal != null)
		{
			totals.add(new UcpTotal(UcpTotal.TYPE_SUBTOTAL, itemsSubtotal));
		}
		else
		{
			addTotal(totals, UcpTotal.TYPE_SUBTOTAL, cart.getSubTotal(), true, false);
		}
		// Discounts appear once Drools promotions fire (Phase 4 asserts this).
		// SIGN per the official total.json (ADR 0003): discount entries carry a
		// NEGATIVE amount (hybris reports the discount magnitude as positive).
		addTotal(totals, UcpTotal.TYPE_DISCOUNT, cart.getTotalDiscounts(), false, true);
		addTotal(totals, UcpTotal.TYPE_TAX, cart.getTotalTax(), false, false);
		// Delivery cost under the well-known type "fulfillment" (ADR 0003).
		addTotal(totals, UcpTotal.TYPE_FULFILLMENT, cart.getDeliveryCost(), false, false);
		// total is emitted LAST — clients read totals[-1] as the running total.
		addTotal(totals, UcpTotal.TYPE_TOTAL, cart.getTotalPrice(), true, false);
		return totals;
	}

	/**
	 * The {@code fulfillment} echo: the applied delivery address and mode as
	 * read back off the cart (never trusted from the client). Null when the
	 * checkout has no destination yet.
	 */
	protected UcpFulfillment marshalFulfillment(final AbstractOrderData cart)
	{
		if (cart == null || (cart.getDeliveryAddress() == null && cart.getDeliveryMode() == null))
		{
			return null;
		}
		final UcpFulfillment fulfillment = new UcpFulfillment();
		if (cart.getDeliveryAddress() != null)
		{
			fulfillment.setDestination(marshalDestination(cart.getDeliveryAddress()));
		}
		if (cart.getDeliveryMode() != null)
		{
			fulfillment.setDeliveryMode(cart.getDeliveryMode().getCode());
			fulfillment.setDeliveryModeName(cart.getDeliveryMode().getName());
		}
		return fulfillment;
	}

	/** Echo of the applied discount (voucher) codes; null when none. */
	protected UcpDiscounts marshalDiscounts(final AbstractOrderData cart)
	{
		if (cart == null || cart.getAppliedVouchers() == null || cart.getAppliedVouchers().isEmpty())
		{
			return null;
		}
		final List<String> codes = new ArrayList<>();
		for (final String code : cart.getAppliedVouchers())
		{
			if (code != null && !code.isBlank() && !codes.contains(code))
			{
				codes.add(code);
			}
		}
		return codes.isEmpty() ? null : new UcpDiscounts(codes);
	}

	protected UcpDestination marshalDestination(final AddressData address)
	{
		final UcpDestination destination = new UcpDestination();
		destination.setFirstName(address.getFirstName());
		destination.setLastName(address.getLastName());
		destination.setLine1(address.getLine1());
		destination.setLine2(address.getLine2());
		destination.setCity(address.getTown());
		if (address.getRegion() != null)
		{
			destination.setRegion(address.getRegion().getIsocodeShort() != null
				? address.getRegion().getIsocodeShort() : address.getRegion().getIsocode());
		}
		destination.setPostalCode(address.getPostalCode());
		if (address.getCountry() != null)
		{
			destination.setCountry(address.getCountry().getIsocode());
		}
		destination.setPhoneNumber(address.getPhone());
		return destination;
	}

	/**
	 * Adds a totals entry; optional zero-valued entries are suppressed.
	 * {@code negate} flips the sign for discount-type entries (schema:
	 * discounts are negative on the receipt; hybris reports magnitudes).
	 */
	private void addTotal(final List<UcpTotal> totals, final String type, final PriceData price,
		final boolean includeWhenZero, final boolean negate)
	{
		final Long amount = minor(price);
		if (amount == null || (amount == 0L && !includeWhenZero))
		{
			return;
		}
		totals.add(new UcpTotal(type, negate ? -Math.abs(amount) : amount));
	}

	/** Pre-discount extended amount for one entry: unit price × quantity. */
	private Long extendedSubtotal(final OrderEntryData entry)
	{
		final Long unit = minor(entry.getBasePrice());
		if (unit == null || entry.getQuantity() == null)
		{
			return null;
		}
		return unit * entry.getQuantity();
	}

	/** Pre-discount item sum across all entries; null when not derivable. */
	private Long itemsSubtotal(final AbstractOrderData cart)
	{
		if (cart.getEntries() == null)
		{
			return null;
		}
		long sum = 0L;
		for (final OrderEntryData entry : cart.getEntries())
		{
			final Long extended = extendedSubtotal(entry);
			if (extended == null)
			{
				return null;
			}
			sum += extended;
		}
		return sum;
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

	/**
	 * Checkout envelopes carry the {@code payment_handlers} registry — the
	 * official {@code response_checkout_schema} requires it (the agent reads
	 * the usable handlers off the checkout response, not the profile).
	 */
	protected UcpEnvelope envelope(final String status)
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus(status);
		envelope.setPaymentHandlers(ucpProfileService.paymentHandlerRegistry());
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

	@Required
	public void setUcpProfileService(final UcpProfileService ucpProfileService)
	{
		this.ucpProfileService = ucpProfileService;
	}
}
