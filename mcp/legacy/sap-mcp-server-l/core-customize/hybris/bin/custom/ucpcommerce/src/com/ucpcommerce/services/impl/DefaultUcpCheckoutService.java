package com.ucpcommerce.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.dto.UcpDestination;
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpOrder;
import com.ucpcommerce.dto.UcpPaymentInstrument;
import com.ucpcommerce.services.UcpCheckoutService;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.InvalidCartException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *
 * Status is derived from cart state (design S5) — never trusted from the
 * client: a cart with items, a delivery address and a delivery mode is
 * {@code ready_for_complete}; anything less is {@code incomplete}.
 */
public class DefaultUcpCheckoutService implements UcpCheckoutService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpCheckoutService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** Mock payment defaults — the same values coremcp's checkout_set_payment uses. */
	private static final String MOCK_CARD_NUMBER = "4111111111111111";
	private static final String MOCK_CARD_TYPE = "visa";
	private static final String MOCK_CARD_EXPIRY_MONTH = "12";
	private static final String MOCK_CARD_EXPIRY_YEAR = "2028";
	private static final String MOCK_SECURITY_CODE = "123";

	private CartFacade cartFacade;
	private CheckoutFacade checkoutFacade;
	private UserFacade userFacade;
	private CartLoaderStrategy cartLoaderStrategy;
	private UcpCheckoutSessionService ucpCheckoutSessionService;
	private UcpCheckoutMarshaller ucpCheckoutMarshaller;
	private UcpOrderMarshaller ucpOrderMarshaller;

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

		// A destination supplied on create is applied the same way as on update.
		applyFulfillment(payload.getFulfillment(), payload.getBuyer(), messages);

		// The first successful addToCart created the session cart implicitly.
		final CartData cart = currentCart();
		final String status = deriveStatus(cart);
		final UcpBuyer buyer = payload.getBuyer();
		final UcpCheckoutSession session = ucpCheckoutSessionService.create(cart.getCode(), status, toJson(buyer));
		LOG.info("UCP create_checkout: {} → cart {} ({} entries, status {})", session.getCheckoutId(),
			cart.getCode(), cart.getEntries() == null ? 0 : cart.getEntries().size(), status);
		return ucpCheckoutMarshaller.marshal(session.getCheckoutId(), status, cart, buyer, messages);
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
		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus()))
		{
			// The backing cart was consumed by placeOrder — the stored
			// completion response IS the checkout's terminal state (Phase 5).
			final UcpCheckout stored = parseCompletionResponse(session);
			if (stored != null)
			{
				return stored;
			}
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
			currentCart(), parseBuyer(session.getBuyerJson()), List.of());
	}

	@Override
	public UcpCheckout update(final String checkoutId, final UcpCheckoutRequest payload)
	{
		final UcpCheckoutSession session = ucpCheckoutSessionService.get(checkoutId);
		if (session == null)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown or expired checkout id: " + checkoutId)));
		}
		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus())
			|| UcpCheckout.STATUS_CANCELED.equals(session.getStatus()))
		{
			// Terminal statuses are immutable (S5) — reject without touching the cart.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is " + session.getStatus() + " and can no longer be updated")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// A completion is running — mutating the cart underneath it would
			// race placeOrder. Recoverable: the client can retry after the
			// completion settles one way or the other (S5).
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_RECOVERABLE,
				"Checkout " + checkoutId + " has a completion in progress and cannot be updated right now")));
		}
		try
		{
			cartLoaderStrategy.loadCart(session.getCartCode());
		}
		catch (final Exception e)
		{
			LOG.warn("UCP update_checkout {}: backing cart {} could not be loaded: {}",
				checkoutId, session.getCartCode(), e.getMessage());
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Checkout " + checkoutId + " is no longer available")));
		}

		final List<UcpMessage> messages = new ArrayList<>();
		if (payload != null && payload.getLineItems() != null)
		{
			applyLineItemDiffs(payload.getLineItems(), messages);
		}

		// Buyer replacement (fields not representable on the cart, R5).
		UcpBuyer buyer = parseBuyer(session.getBuyerJson());
		if (payload != null && payload.getBuyer() != null)
		{
			buyer = payload.getBuyer();
			ucpCheckoutSessionService.updateBuyer(checkoutId, toJson(buyer));
		}

		if (payload != null)
		{
			applyFulfillment(payload.getFulfillment(), buyer, messages);
		}

		// Persist-back bracket tail: recalculated cart → derived status → entry.
		final CartData cart = currentCart();
		final String status = deriveStatus(cart);
		ucpCheckoutSessionService.update(checkoutId, cart.getCode(), status);
		LOG.info("UCP update_checkout: {} → cart {} ({} entries, status {})", checkoutId, cart.getCode(),
			cart.getEntries() == null ? 0 : cart.getEntries().size(), status);
		return ucpCheckoutMarshaller.marshal(checkoutId, status, cart, buyer, messages);
	}

	@Override
	public UcpCheckout complete(final String checkoutId, final UcpCheckoutRequest payload,
		final String idempotencyKey)
	{
		requireIdempotencyKey(idempotencyKey, "complete_checkout");
		final UcpCheckoutSession session = ucpCheckoutSessionService.get(checkoutId);
		if (session == null)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown or expired checkout id: " + checkoutId)));
		}

		// Idempotency check FIRST (S3): a duplicate key replays the stored
		// response verbatim — never a second placeOrder.
		if (idempotencyKey.equals(session.getIdempotencyKey())
			&& session.getCompletionResponseJson() != null)
		{
			LOG.info("UCP complete_checkout {}: replaying stored completion (order {})",
				checkoutId, session.getOrderCode());
			final UcpCheckout stored = parseCompletionResponse(session);
			if (stored != null)
			{
				return stored;
			}
			// The stored response is unreadable, but the order EXISTS — never
			// place a second one. Rebuild a minimal completed payload.
			return completedFallback(session);
		}

		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus()))
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is already completed (under a different idempotency key)")));
		}
		if (UcpCheckout.STATUS_CANCELED.equals(session.getStatus()))
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is canceled and can no longer be completed")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// A previous complete was accepted but has not settled (concurrent
			// call, or a crash mid-completion). Recoverable — retry later.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_RECOVERABLE,
				"A completion for checkout " + checkoutId + " is already in progress; retry shortly")));
		}

		// Handler validation (R9): the single declared mock handler must be
		// referenced; any credential token for it is accepted (and never read).
		final UcpMessage handlerError = validatePaymentHandler(payload);
		if (handlerError != null)
		{
			return ucpCheckoutMarshaller.error(List.of(handlerError));
		}

		try
		{
			cartLoaderStrategy.loadCart(session.getCartCode());
		}
		catch (final Exception e)
		{
			LOG.warn("UCP complete_checkout {}: backing cart {} could not be loaded: {}",
				checkoutId, session.getCartCode(), e.getMessage());
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Checkout " + checkoutId + " is no longer available")));
		}

		final UcpBuyer buyer = parseBuyer(session.getBuyerJson());

		// Readiness is derived from cart state, never from the stored status (S5).
		final CartData cart = currentCart();
		final String derived = deriveStatus(cart);
		if (!UcpCheckout.STATUS_READY_FOR_COMPLETE.equals(derived))
		{
			ucpCheckoutSessionService.update(checkoutId, cart.getCode(), derived);
			return ucpCheckoutMarshaller.marshal(checkoutId, derived, cart, buyer,
				List.of(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"Checkout is not ready to complete: it needs at least one item, a delivery "
						+ "destination and a delivery mode (current status: " + derived + ")")));
		}

		// Accepted: S5 ready_for_complete → complete_in_progress, key stored.
		ucpCheckoutSessionService.beginCompletion(checkoutId, idempotencyKey);

		final OrderData order;
		try
		{
			runMockPayment(cart);
			order = checkoutFacade.placeOrder();
		}
		catch (final InvalidCartException e)
		{
			LOG.warn("UCP complete_checkout {}: invalid cart: {}", checkoutId, e.getMessage());
			ucpCheckoutSessionService.failCompletion(checkoutId);
			return ucpCheckoutMarshaller.marshal(checkoutId, UcpCheckout.STATUS_READY_FOR_COMPLETE,
				currentCart(), buyer,
				List.of(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"Cannot place order — cart is invalid: " + e.getMessage())));
		}
		catch (final Exception e)
		{
			LOG.warn("UCP complete_checkout {}: payment/place failed: {}", checkoutId, e.getMessage());
			ucpCheckoutSessionService.failCompletion(checkoutId);
			return ucpCheckoutMarshaller.marshal(checkoutId, UcpCheckout.STATUS_READY_FOR_COMPLETE,
				currentCart(), buyer,
				List.of(new UcpMessage("error", "payment_declined", UcpMessage.SEVERITY_RECOVERABLE,
					"Payment could not be processed: " + e.getMessage())));
		}

		// placeOrder succeeded — marshal the completed checkout from the ORDER
		// (the session cart was consumed) and persist the terminal state in one
		// atomic entry save (status, order code, replayable response).
		final UcpCheckout completed = ucpCheckoutMarshaller.marshal(checkoutId,
			UcpCheckout.STATUS_COMPLETED, order, buyer, List.of());
		completed.setOrder(ucpOrderMarshaller.marshal(order));
		ucpCheckoutSessionService.recordCompletion(checkoutId, toJson(completed), order.getCode());
		LOG.info("UCP complete_checkout: {} completed as order {}", checkoutId, order.getCode());
		return completed;
	}

	@Override
	public UcpCheckout cancel(final String checkoutId, final String idempotencyKey)
	{
		requireIdempotencyKey(idempotencyKey, "cancel_checkout");
		final UcpCheckoutSession session = ucpCheckoutSessionService.get(checkoutId);
		if (session == null)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown or expired checkout id: " + checkoutId)));
		}
		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus()))
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is already completed and can no longer be canceled")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// S5 has no complete_in_progress → canceled edge: the completion
			// must settle first. Recoverable — retry after it does.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "invalid_request",
				UcpMessage.SEVERITY_RECOVERABLE,
				"A completion for checkout " + checkoutId + " is in progress; it cannot be canceled right now")));
		}
		if (!UcpCheckout.STATUS_CANCELED.equals(session.getStatus()))
		{
			// incomplete / ready_for_complete → canceled (terminal). The cart
			// itself is left to the platform's abandoned-cart cleanup; the
			// terminal entry status blocks all further mutations.
			ucpCheckoutSessionService.update(checkoutId, session.getCartCode(), UcpCheckout.STATUS_CANCELED);
			LOG.info("UCP cancel_checkout: {} canceled (cart {})", checkoutId, session.getCartCode());
		}
		// Idempotent: a repeated cancel re-returns the canceled state.
		return marshalCanceled(session);
	}

	/** The canceled checkout payload — cart re-marshaled best-effort. */
	protected UcpCheckout marshalCanceled(final UcpCheckoutSession session)
	{
		CartData cart = null;
		try
		{
			cartLoaderStrategy.loadCart(session.getCartCode());
			cart = currentCart();
		}
		catch (final Exception e)
		{
			// A canceled checkout with a swept cart is still canceled — return
			// the terminal state without line items rather than an error.
			LOG.debug("UCP cancel_checkout {}: backing cart {} not loadable: {}",
				session.getCheckoutId(), session.getCartCode(), e.getMessage());
		}
		return ucpCheckoutMarshaller.marshal(session.getCheckoutId(), UcpCheckout.STATUS_CANCELED,
			cart, parseBuyer(session.getBuyerJson()), List.of());
	}

	/**
	 * The mock payment path (design R9/S3) — the same orchestration coremcp's
	 * checkout_set_payment + order_place handlers embody: a payment
	 * subscription with the default mock Visa (billing address = the cart's
	 * delivery address), then authorization with the mock CVV. The client's
	 * credential token is deliberately never read, logged or stored.
	 */
	protected void runMockPayment(final CartData cart)
	{
		final CCPaymentInfoData paymentInfo = new CCPaymentInfoData();
		paymentInfo.setCardNumber(MOCK_CARD_NUMBER);
		paymentInfo.setCardType(MOCK_CARD_TYPE);
		paymentInfo.setExpiryMonth(MOCK_CARD_EXPIRY_MONTH);
		paymentInfo.setExpiryYear(MOCK_CARD_EXPIRY_YEAR);
		final AddressData deliveryAddress = cart != null ? cart.getDeliveryAddress() : null;
		if (deliveryAddress != null)
		{
			paymentInfo.setBillingAddress(deliveryAddress);
			paymentInfo.setAccountHolderName(
				(deliveryAddress.getFirstName() != null ? deliveryAddress.getFirstName() : "") + " "
					+ (deliveryAddress.getLastName() != null ? deliveryAddress.getLastName() : ""));
		}
		final CCPaymentInfoData created = checkoutFacade.createPaymentSubscription(paymentInfo);
		if (created == null)
		{
			throw new IllegalStateException("payment subscription was not created");
		}
		// The existing mock path (OrderPlaceToolHandler) deliberately ignores
		// authorizePayment's boolean: the demo platform's mock payment setup
		// reports a non-ACCEPTED transaction status here (found live in
		// Phase 5) even though placeOrder then succeeds. Failing on it would
		// block every purchase, so we follow the proven flow verbatim.
		if (!checkoutFacade.authorizePayment(MOCK_SECURITY_CODE))
		{
			LOG.debug("UCP complete_checkout: mock authorizePayment did not report ACCEPTED (ignored, "
				+ "as in the proprietary order_place path)");
		}
	}

	/**
	 * Validate the complete payload's payment instrument against the single
	 * declared handler (R9). Returns null when valid, otherwise the
	 * unrecoverable message to return.
	 */
	protected UcpMessage validatePaymentHandler(final UcpCheckoutRequest payload)
	{
		final List<UcpPaymentInstrument> instruments =
			payload != null && payload.getPayment() != null ? payload.getPayment().getInstruments() : null;
		if (instruments == null || instruments.isEmpty())
		{
			return new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_UNRECOVERABLE,
				"payment.instruments must reference a declared payment handler (declared: "
					+ UcpcommerceConstants.PAYMENT_HANDLER_ID + ")");
		}
		for (final UcpPaymentInstrument instrument : instruments)
		{
			if (instrument != null
				&& UcpcommerceConstants.PAYMENT_HANDLER_ID.equals(instrument.getHandlerId()))
			{
				return null;
			}
		}
		final String offered = instruments.stream()
			.map(i -> i != null ? String.valueOf(i.getHandlerId()) : "null")
			.reduce((a, b) -> a + ", " + b).orElse("none");
		return new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_UNRECOVERABLE,
			"Unknown payment handler id(s): " + offered + "; this store declares only "
				+ UcpcommerceConstants.PAYMENT_HANDLER_ID);
	}

	/** Binding spec: complete/cancel MUST carry meta["idempotency-key"] — a missing one is a client protocol bug. */
	private static void requireIdempotencyKey(final String idempotencyKey, final String operation)
	{
		if (idempotencyKey == null || idempotencyKey.isBlank())
		{
			throw new IllegalArgumentException(
				"meta[\"idempotency-key\"] is required for " + operation);
		}
	}

	/**
	 * Last-resort replay when the stored completion response cannot be parsed:
	 * a minimal completed payload carrying the recorded order id — the order
	 * exists, so re-executing the purchase is never an option.
	 */
	protected UcpCheckout completedFallback(final UcpCheckoutSession session)
	{
		final UcpCheckout checkout = ucpCheckoutMarshaller.marshal(session.getCheckoutId(),
			UcpCheckout.STATUS_COMPLETED, null, parseBuyer(session.getBuyerJson()), List.of());
		final UcpOrder order = new UcpOrder();
		order.setId(session.getOrderCode());
		checkout.setOrder(order);
		return checkout;
	}

	/** Parse the stored completion response; null (and a warning) when unreadable. */
	protected UcpCheckout parseCompletionResponse(final UcpCheckoutSession session)
	{
		if (session.getCompletionResponseJson() == null || session.getCompletionResponseJson().isBlank())
		{
			return null;
		}
		try
		{
			return objectMapper.readValue(session.getCompletionResponseJson(), UcpCheckout.class);
		}
		catch (final Exception e)
		{
			LOG.warn("UCP checkout {}: stored completion response is unreadable: {}",
				session.getCheckoutId(), e.getMessage());
			return null;
		}
	}

	private String toJson(final UcpCheckout checkout)
	{
		try
		{
			return objectMapper.writeValueAsString(checkout);
		}
		catch (final Exception e)
		{
			LOG.warn("Could not serialize UCP completion response, storing none: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * The current session cart as seen through the checkout facade.
	 * {@code CartFacade.getSessionCart()} alone is NOT enough: the platform's
	 * {@code CartPopulator} never fills {@code deliveryAddress}/
	 * {@code deliveryMode} — only {@code CheckoutFacade.getCheckoutCart()}
	 * overlays them onto the {@code CartData}, and both the status derivation
	 * (S5) and the {@code fulfillment} echo depend on those fields.
	 */
	protected CartData currentCart()
	{
		return checkoutFacade.getCheckoutCart();
	}

	/**
	 * Derive the UCP checkout status from cart state (design diagram S5).
	 * Shared with later phases: {@code ready_for_complete} needs a deliverable
	 * cart — at least one line item plus a delivery address and mode.
	 * Removing the address on an update naturally falls back to
	 * {@code incomplete}.
	 */
	protected String deriveStatus(final CartData cart)
	{
		final boolean hasItems = cart != null && cart.getEntries() != null && !cart.getEntries().isEmpty();
		if (hasItems && cart.getDeliveryAddress() != null && cart.getDeliveryMode() != null)
		{
			return UcpCheckout.STATUS_READY_FOR_COMPLETE;
		}
		return UcpCheckout.STATUS_INCOMPLETE;
	}

	/**
	 * Apply {@code line_items} as the desired end state of the loaded cart:
	 * new items are added, differing quantities updated (0 or below removes),
	 * and current entries absent from the request are removed. Per-item
	 * failures become {@code recoverable} messages; the rest of the diff
	 * still applies. An empty list is rejected (a checkout keeps at least one
	 * item until it is canceled) and leaves the cart untouched.
	 */
	protected void applyLineItemDiffs(final List<UcpLineItemRequest> requested, final List<UcpMessage> messages)
	{
		if (requested.isEmpty())
		{
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"line_items must not be empty; items left unchanged (cancel the checkout to abandon it)"));
			return;
		}

		// Desired end state keyed by item id; duplicate ids merge by summing.
		final Map<String, Long> desired = new LinkedHashMap<>();
		for (final UcpLineItemRequest lineItem : requested)
		{
			final String itemId = lineItem != null && lineItem.getItem() != null ? lineItem.getItem().getId() : null;
			if (itemId == null || itemId.isBlank())
			{
				messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"Line item is missing item.id"));
				continue;
			}
			desired.merge(itemId, lineItem.getQuantity() != null ? lineItem.getQuantity() : 1L, Long::sum);
		}

		// Snapshot the current entries before mutating (entry numbers are stable).
		final CartData current = currentCart();
		final Map<String, OrderEntryData> entriesByCode = new LinkedHashMap<>();
		if (current != null && current.getEntries() != null)
		{
			for (final OrderEntryData entry : current.getEntries())
			{
				if (entry.getProduct() != null && entry.getProduct().getCode() != null)
				{
					entriesByCode.put(entry.getProduct().getCode(), entry);
				}
			}
		}

		for (final Map.Entry<String, Long> want : desired.entrySet())
		{
			final OrderEntryData entry = entriesByCode.get(want.getKey());
			if (entry == null)
			{
				if (want.getValue() > 0)
				{
					addItem(want.getKey(), want.getValue(), messages);
				}
			}
			else if (!want.getValue().equals(entry.getQuantity()))
			{
				changeQuantity(want.getKey(), entry, Math.max(0L, want.getValue()), messages);
			}
		}
		for (final Map.Entry<String, OrderEntryData> present : entriesByCode.entrySet())
		{
			if (!desired.containsKey(present.getKey()))
			{
				changeQuantity(present.getKey(), present.getValue(), 0L, messages);
			}
		}
	}

	/** Update one existing entry's quantity (0 removes); failures are recoverable messages. */
	protected void changeQuantity(final String itemId, final OrderEntryData entry, final long quantity,
		final List<UcpMessage> messages)
	{
		try
		{
			final CartModificationData modification = cartFacade.updateCartEntry(entry.getEntryNumber(), quantity);
			if (quantity > 0 && modification != null && modification.getQuantity() < quantity)
			{
				messages.add(new UcpMessage("warning", "out_of_stock", UcpMessage.SEVERITY_RECOVERABLE,
					"Item " + itemId + ": only " + modification.getQuantity() + " of " + quantity
						+ " units are available"));
			}
		}
		catch (final Exception e)
		{
			LOG.debug("UCP update_checkout: could not set item {} to quantity {}: {}", itemId, quantity,
				e.getMessage());
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"Could not update item " + itemId + ": " + e.getMessage()));
		}
	}

	/**
	 * Apply the {@code fulfillment} block to the loaded cart: set the
	 * destination as the delivery address, then the delivery mode — explicit
	 * when supplied, otherwise auto-selected (cheapest supported mode) once a
	 * destination is present. Failures are {@code recoverable} messages.
	 */
	protected void applyFulfillment(final UcpFulfillment fulfillment, final UcpBuyer buyer,
		final List<UcpMessage> messages)
	{
		if (fulfillment == null)
		{
			return;
		}
		if (fulfillment.getDestination() != null)
		{
			final AddressData address = toAddressData(fulfillment.getDestination(), buyer);
			try
			{
				// The OCC-conformant inline-address flow: persist the address
				// against the customer first (UserFacade.addAddress populates
				// address.id), then select it. A cart-owned inline address
				// would be applied but fail CheckoutFacade.getCheckoutCart()'s
				// supported-addresses check, so it would never be echoed and
				// the status could not derive ready_for_complete. The address
				// is not visible in the address book (visibleInAddressBook
				// stays false).
				userFacade.addAddress(address);
				if (!checkoutFacade.setDeliveryAddress(address))
				{
					messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
						"Delivery destination was not accepted"));
				}
			}
			catch (final Exception e)
			{
				LOG.debug("UCP checkout: could not set delivery destination: {}", e.getMessage());
				messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"Could not set delivery destination: " + e.getMessage()));
			}
		}
		final String requestedMode = fulfillment.getDeliveryMode();
		if (requestedMode != null && !requestedMode.isBlank())
		{
			try
			{
				if (!checkoutFacade.setDeliveryMode(requestedMode))
				{
					messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
						"Unknown or unsupported delivery mode: " + requestedMode));
				}
			}
			catch (final Exception e)
			{
				LOG.debug("UCP checkout: could not set delivery mode {}: {}", requestedMode, e.getMessage());
				messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"Could not set delivery mode " + requestedMode + ": " + e.getMessage()));
			}
		}
		else
		{
			autoSelectDeliveryMode(messages);
		}
	}

	/**
	 * When a destination is set but no delivery mode was requested (and none
	 * is applied yet), pick the cheapest supported mode so a plain destination
	 * update reaches {@code ready_for_complete} — UCP agents send an address,
	 * not a merchant-specific mode code.
	 */
	protected void autoSelectDeliveryMode(final List<UcpMessage> messages)
	{
		try
		{
			final CartData cart = currentCart();
			if (cart == null || cart.getDeliveryAddress() == null || cart.getDeliveryMode() != null)
			{
				return;
			}
			DeliveryModeData cheapest = null;
			BigDecimal cheapestCost = null;
			for (final DeliveryModeData mode : checkoutFacade.getSupportedDeliveryModes())
			{
				if (mode == null || mode.getCode() == null)
				{
					continue;
				}
				final BigDecimal cost = mode.getDeliveryCost() != null && mode.getDeliveryCost().getValue() != null
					? mode.getDeliveryCost().getValue() : null;
				if (cheapest == null
					|| (cost != null && (cheapestCost == null || cost.compareTo(cheapestCost) < 0)))
				{
					cheapest = mode;
					cheapestCost = cost;
				}
			}
			if (cheapest != null)
			{
				checkoutFacade.setDeliveryMode(cheapest.getCode());
			}
			else
			{
				messages.add(new UcpMessage("warning", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
					"No delivery mode is available for the destination"));
			}
		}
		catch (final Exception e)
		{
			LOG.debug("UCP checkout: could not auto-select a delivery mode: {}", e.getMessage());
			messages.add(new UcpMessage("warning", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"Could not select a delivery mode: " + e.getMessage()));
		}
	}

	/**
	 * Map the UCP destination onto an inline {@code AddressData} (id null →
	 * {@code DefaultCheckoutFacade} creates a new delivery address). Name
	 * fields fall back to the buyer block. A region is passed through as a
	 * hybris region isocode ({@code COUNTRY-REGION}) when the platform has
	 * regions loaded; the surrounding try/catch turns lookup failures into
	 * recoverable messages.
	 */
	protected AddressData toAddressData(final UcpDestination destination, final UcpBuyer buyer)
	{
		final AddressData address = new AddressData();
		address.setFirstName(destination.getFirstName() != null ? destination.getFirstName()
			: buyer != null ? buyer.getFirstName() : null);
		address.setLastName(destination.getLastName() != null ? destination.getLastName()
			: buyer != null ? buyer.getLastName() : null);
		address.setLine1(destination.getLine1());
		address.setLine2(destination.getLine2());
		address.setTown(destination.getCity());
		address.setPostalCode(destination.getPostalCode());
		address.setPhone(destination.getPhoneNumber());
		if (destination.getCountry() != null && !destination.getCountry().isBlank())
		{
			final CountryData country = new CountryData();
			country.setIsocode(destination.getCountry());
			address.setCountry(country);
		}
		if (destination.getRegion() != null && !destination.getRegion().isBlank())
		{
			final RegionData region = new RegionData();
			region.setIsocode(destination.getRegion().contains("-") || destination.getCountry() == null
				? destination.getRegion()
				: destination.getCountry() + "-" + destination.getRegion());
			address.setRegion(region);
		}
		address.setShippingAddress(true);
		return address;
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
		return addItem(itemId, lineItem.getQuantity() != null ? lineItem.getQuantity() : 1L, messages);
	}

	/** {@code CartFacade.addToCart} with the shared per-item message conventions. */
	protected boolean addItem(final String itemId, final long quantity, final List<UcpMessage> messages)
	{
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
			LOG.debug("UCP checkout: could not add item {}: {}", itemId, e.getMessage());
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
	public void setCheckoutFacade(final CheckoutFacade checkoutFacade)
	{
		this.checkoutFacade = checkoutFacade;
	}

	@Required
	public void setUserFacade(final UserFacade userFacade)
	{
		this.userFacade = userFacade;
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

	@Required
	public void setUcpOrderMarshaller(final UcpOrderMarshaller ucpOrderMarshaller)
	{
		this.ucpOrderMarshaller = ucpOrderMarshaller;
	}
}
