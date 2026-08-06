package com.ucpcommerce.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpAppliedDiscount;
import com.ucpcommerce.dto.UcpBuyer;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.dto.UcpDestination;
import com.ucpcommerce.dto.UcpDiscounts;
import com.ucpcommerce.dto.UcpFulfillment;
import com.ucpcommerce.dto.UcpFulfillmentGroup;
import com.ucpcommerce.dto.UcpFulfillmentMethod;
import com.ucpcommerce.dto.UcpFulfillmentOption;
import com.ucpcommerce.dto.UcpLineItem;
import com.ucpcommerce.dto.UcpLineItemRequest;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpOrder;
import com.ucpcommerce.dto.UcpPayment;
import com.ucpcommerce.dto.UcpPaymentInstrument;
import com.ucpcommerce.dto.UcpShippingDestination;
import com.ucpcommerce.dto.UcpTotal;
import com.ucpcommerce.services.UcpCheckoutService;
import com.ucpcommerce.services.UcpCheckoutSessionService;
import com.ucpcommerce.services.UcpIdempotencyService;

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
import de.hybris.platform.commercefacades.voucher.VoucherFacade;
import de.hybris.platform.commercefacades.voucher.data.VoucherData;
import de.hybris.platform.commercefacades.voucher.exceptions.VoucherOperationException;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
	private VoucherFacade voucherFacade;
	private UcpIdempotencyService ucpIdempotencyService;
	private CartLoaderStrategy cartLoaderStrategy;
	private UcpCheckoutSessionService ucpCheckoutSessionService;
	private UcpCheckoutMarshaller ucpCheckoutMarshaller;
	private UcpOrderMarshaller ucpOrderMarshaller;
	private UcpMoneyConverter ucpMoneyConverter;

	@Override
	public UcpCheckout create(final UcpCheckoutRequest payload)
	{
		return create(payload, null);
	}

	@Override
	public UcpCheckout create(final UcpCheckoutRequest payload, final String idempotencyKey)
	{
		// Optional idempotency (official binding): an identical same-key retry
		// replays the stored response verbatim; a different payload under the
		// same key is a 409 conflict.
		final String requestHash = idempotencyKey != null && !idempotencyKey.isBlank()
			? requestHash(payload) : null;
		if (requestHash != null)
		{
			final UcpIdempotencyService.Consultation seen =
				ucpIdempotencyService.consult("create", idempotencyKey, requestHash);
			if (seen.getOutcome() == UcpIdempotencyService.Outcome.CONFLICT)
			{
				return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
					UcpMessage.SEVERITY_UNRECOVERABLE,
					"Idempotency-Key was already used with a different request payload")));
			}
			if (seen.getOutcome() == UcpIdempotencyService.Outcome.REPLAY && seen.getResponseJson() != null)
			{
				final UcpCheckout replay = parseCheckoutJson(seen.getResponseJson());
				if (replay != null)
				{
					return replay;
				}
			}
		}
		final UcpCheckout created = doCreate(payload);
		if (requestHash != null && created.getUcp() != null && !"error".equals(created.getUcp().getStatus()))
		{
			ucpIdempotencyService.record("create", idempotencyKey, requestHash, toJson(created));
		}
		return created;
	}

	protected UcpCheckout doCreate(final UcpCheckoutRequest payload)
	{
		final UcpMessage versionError = validateRequestedVersion(payload);
		if (versionError != null)
		{
			return ucpCheckoutMarshaller.error(List.of(versionError));
		}
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

		// Discount codes and a destination supplied on create are applied the
		// same way as on update.
		applyDiscounts(payload.getDiscounts(), messages);
		applyFulfillment(payload.getFulfillment(), payload.getBuyer(), messages);

		// The first successful addToCart created the session cart implicitly.
		final CartData cart = currentCart();
		final String status = deriveStatus(cart);
		final UcpBuyer buyer = payload.getBuyer();
		final UcpCheckoutSession session = ucpCheckoutSessionService.create(cart.getCode(), status, toJson(buyer));
		LOG.info("UCP create_checkout: {} → cart {} ({} entries, status {})", session.getCheckoutId(),
			cart.getCode(), cart.getEntries() == null ? 0 : cart.getEntries().size(), status);
		final UcpCheckout checkout = ucpCheckoutMarshaller.marshal(session.getCheckoutId(), status, cart, buyer,
			messages);
		echoPayment(checkout, payload);
		attachAppliedDiscounts(checkout);
		attachFulfillmentNegotiation(checkout, payload.getFulfillment(), cart);
		return checkout;
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
		final CartData cart = currentCart();
		final UcpCheckout checkout = ucpCheckoutMarshaller.marshal(session.getCheckoutId(), session.getStatus(),
			cart, parseBuyer(session.getBuyerJson()), List.of());
		attachAppliedDiscounts(checkout);
		attachFulfillmentNegotiation(checkout, null, cart);
		return checkout;
	}

	@Override
	public UcpCheckout update(final String checkoutId, final UcpCheckoutRequest payload)
	{
		return update(checkoutId, payload, null);
	}

	@Override
	public UcpCheckout update(final String checkoutId, final UcpCheckoutRequest payload,
		final String idempotencyKey)
	{
		final String requestHash = idempotencyKey != null && !idempotencyKey.isBlank()
			? requestHash(payload) : null;
		if (requestHash != null)
		{
			final UcpIdempotencyService.Consultation seen =
				ucpIdempotencyService.consult("update:" + checkoutId, idempotencyKey, requestHash);
			if (seen.getOutcome() == UcpIdempotencyService.Outcome.CONFLICT)
			{
				return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
					UcpMessage.SEVERITY_UNRECOVERABLE,
					"Idempotency-Key was already used with a different request payload")));
			}
			if (seen.getOutcome() == UcpIdempotencyService.Outcome.REPLAY && seen.getResponseJson() != null)
			{
				final UcpCheckout replay = parseCheckoutJson(seen.getResponseJson());
				if (replay != null)
				{
					return replay;
				}
			}
		}
		final UcpCheckout updated = doUpdate(checkoutId, payload);
		if (requestHash != null && updated.getUcp() != null && !"error".equals(updated.getUcp().getStatus()))
		{
			ucpIdempotencyService.record("update:" + checkoutId, idempotencyKey, requestHash, toJson(updated));
		}
		return updated;
	}

	protected UcpCheckout doUpdate(final String checkoutId, final UcpCheckoutRequest payload)
	{
		final UcpMessage versionError = validateRequestedVersion(payload);
		if (versionError != null)
		{
			return ucpCheckoutMarshaller.error(List.of(versionError));
		}
		final UcpCheckoutSession session = ucpCheckoutSessionService.get(checkoutId);
		if (session == null)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Unknown or expired checkout id: " + checkoutId)));
		}
		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus())
			|| UcpCheckout.STATUS_CANCELED.equals(session.getStatus()))
		{
			// Terminal statuses are immutable (S5) — reject without touching the
			// cart. Code "conflict" → HTTP 409 over REST.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is " + session.getStatus() + " and can no longer be updated")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// A completion is running — mutating the cart underneath it would
			// race placeOrder. Recoverable: the client can retry after the
			// completion settles one way or the other (S5).
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
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
			applyDiscounts(payload.getDiscounts(), messages);
			applyFulfillment(payload.getFulfillment(), buyer, messages);
		}

		// Persist-back bracket tail: recalculated cart → derived status → entry.
		final CartData cart = currentCart();
		final String status = deriveStatus(cart);
		ucpCheckoutSessionService.update(checkoutId, cart.getCode(), status);
		LOG.info("UCP update_checkout: {} → cart {} ({} entries, status {})", checkoutId, cart.getCode(),
			cart.getEntries() == null ? 0 : cart.getEntries().size(), status);
		final UcpCheckout checkout = ucpCheckoutMarshaller.marshal(checkoutId, status, cart, buyer, messages);
		echoPayment(checkout, payload);
		attachAppliedDiscounts(checkout);
		attachFulfillmentNegotiation(checkout, payload != null ? payload.getFulfillment() : null, cart);
		return checkout;
	}

	@Override
	public UcpCheckout complete(final String checkoutId, final UcpCheckoutRequest payload,
		final String idempotencyKey)
	{
		requireIdempotencyKey(idempotencyKey, "complete_checkout");
		// Same-key/DIFFERENT-payload retry is a 409 conflict (official
		// binding) — checked against the hash stored when the first complete
		// was accepted; the response replay itself comes from the session.
		final String completeHash = requestHash(payload);
		final UcpIdempotencyService.Consultation seen =
			ucpIdempotencyService.consult("complete:" + checkoutId, idempotencyKey, completeHash);
		if (seen.getOutcome() == UcpIdempotencyService.Outcome.CONFLICT)
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Idempotency-Key was already used with a different request payload")));
		}
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
		if (idempotencyKey.equals(session.getIdempotencyKey())
			&& UcpCheckout.STATUS_COMPLETED.equals(session.getStatus()))
		{
			// Same accepted key, but no stored response (e.g. the record write
			// hit a column limit): the order exists — replay the fallback, not
			// a bogus "different idempotency key" error.
			return completedFallback(session);
		}

		if (UcpCheckout.STATUS_COMPLETED.equals(session.getStatus()))
		{
			// Code "conflict" → HTTP 409 over REST (idempotency conflict).
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is already completed (under a different idempotency key)")));
		}
		if (UcpCheckout.STATUS_CANCELED.equals(session.getStatus()))
		{
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is canceled and can no longer be completed")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// A previous complete was accepted but has not settled (concurrent
			// call, or a crash mid-completion). Recoverable — retry later.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_RECOVERABLE,
				"A completion for checkout " + checkoutId + " is already in progress; retry shortly")));
		}

		// Handler validation (R9): the single declared mock handler must be
		// referenced; any credential token for it is accepted (and never read)
		// EXCEPT the well-known decline probe "fail_token", which the mock
		// declines so agents can exercise the payment-failure path.
		final UcpMessage handlerError = validatePaymentHandler(payload);
		if (handlerError != null)
		{
			return ucpCheckoutMarshaller.error(List.of(handlerError));
		}
		final UcpMessage declined = probeDeclineToken(payload);
		if (declined != null)
		{
			return ucpCheckoutMarshaller.error(List.of(declined));
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
			// Code "not_ready" → HTTP 400 over REST (official binding). The
			// message leads with the spec's canonical phrasing.
			final UcpCheckout notReady = ucpCheckoutMarshaller.marshal(checkoutId, derived, cart, buyer,
				List.of(new UcpMessage("error", "not_ready", UcpMessage.SEVERITY_RECOVERABLE,
					"Fulfillment address and option must be selected before completing: the checkout "
						+ "needs at least one item, a delivery destination and a delivery mode "
						+ "(current status: " + derived + ")")));
			attachFulfillmentNegotiation(notReady, null, cart);
			return notReady;
		}

		// Accepted: S5 ready_for_complete → complete_in_progress, key stored.
		ucpCheckoutSessionService.beginCompletion(checkoutId, idempotencyKey);
		// Pin the accepted request's hash so a same-key retry with a DIFFERENT
		// payload is a 409 (the session entry replays the response itself).
		ucpIdempotencyService.record("complete:" + checkoutId, idempotencyKey, completeHash, null);

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
		echoPayment(completed, payload);
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
			// Code "conflict" → HTTP 409 over REST.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
				UcpMessage.SEVERITY_UNRECOVERABLE,
				"Checkout " + checkoutId + " is already completed and can no longer be canceled")));
		}
		if (UcpCheckout.STATUS_COMPLETE_IN_PROGRESS.equals(session.getStatus()))
		{
			// S5 has no complete_in_progress → canceled edge: the completion
			// must settle first. Recoverable — retry after it does.
			return ucpCheckoutMarshaller.error(List.of(new UcpMessage("error", "conflict",
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
	/**
	 * Version negotiation (protocol.md): a request whose {@code ucp.version}
	 * names a version this server does not implement is rejected —
	 * {@code version_unsupported}, HTTP 422 over REST. An absent block or
	 * version means "whatever the server speaks" and passes.
	 */
	protected UcpMessage validateRequestedVersion(final UcpCheckoutRequest payload)
	{
		final String requested = payload != null && payload.getUcp() != null
			? payload.getUcp().getVersion() : null;
		if (requested == null || requested.isBlank())
		{
			return null;
		}
		final String pinned = getPinnedUcpVersion();
		if (pinned.equals(requested))
		{
			return null;
		}
		return new UcpMessage("error", "version_unsupported", UcpMessage.SEVERITY_UNRECOVERABLE,
			"UCP version " + requested + " is not supported; this server implements " + pinned);
	}

	/** The pinned UCP spec version — config seam, overridable in tests. */
	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY,
			UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	/**
	 * The mock handler's decline probe: a credential {@code token} of
	 * {@code fail_token} is declined (code {@code payment_declined}, HTTP 402
	 * over REST) BEFORE the completion is accepted — no state transition, no
	 * order. Every other token is accepted and never inspected further.
	 */
	protected UcpMessage probeDeclineToken(final UcpCheckoutRequest payload)
	{
		final List<UcpPaymentInstrument> instruments =
			payload != null && payload.getPayment() != null ? payload.getPayment().getInstruments() : null;
		if (instruments == null)
		{
			return null;
		}
		for (final UcpPaymentInstrument instrument : instruments)
		{
			if (instrument != null && instrument.getCredential() != null
				&& "fail_token".equals(instrument.getCredential().get("token")))
			{
				return new UcpMessage("error", "payment_declined", UcpMessage.SEVERITY_RECOVERABLE,
					"Payment was declined by the mock handler (fail_token decline probe)");
			}
		}
		return null;
	}

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
				&& (UcpcommerceConstants.PAYMENT_HANDLER_ID.equals(instrument.getHandlerId())
					|| UcpcommerceConstants.PAYMENT_HANDLER_ALIAS.equals(instrument.getHandlerId())))
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
	/**
	 * Applies the {@code discounts.codes} list declaratively, like
	 * {@code line_items}: codes not yet applied are applied via the voucher
	 * facade, applied codes absent from the list are released, and a null
	 * block leaves the applied codes untouched. Per-code failures (unknown,
	 * expired, not releasable) are recoverable messages — the rest of the
	 * update still lands, never a 500.
	 */
	protected void applyDiscounts(final UcpDiscounts discounts, final List<UcpMessage> messages)
	{
		if (discounts == null || discounts.getCodes() == null)
		{
			return;
		}
		final List<String> requested = new ArrayList<>();
		for (final String code : discounts.getCodes())
		{
			if (code != null && !code.isBlank() && !containsIgnoreCase(requested, code.trim()))
			{
				requested.add(code.trim());
			}
		}
		// Codes match CASE-INSENSITIVELY (discount.md / conformance DSC-005);
		// releases always use the server-side canonical code.
		final List<String> applied = appliedVoucherCodes();
		for (final String code : applied)
		{
			if (!containsIgnoreCase(requested, code))
			{
				try
				{
					voucherFacade.releaseVoucher(code);
				}
				catch (final VoucherOperationException e)
				{
					LOG.warn("UCP discounts: could not release voucher '{}': {}", code, e.getMessage());
					messages.add(new UcpMessage("warning", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
						"Discount code '" + code + "' could not be removed"));
				}
			}
		}
		for (final String code : requested)
		{
			if (!containsIgnoreCase(applied, code))
			{
				applyDiscountCode(code, messages);
			}
		}
	}

	/**
	 * Applies one requested code, tolerating case variants: hybris coupon
	 * codes are stored canonically (typically uppercase), so an as-given miss
	 * retries the upper- and lowercase forms before reporting the code
	 * invalid. The response echo always carries the canonical applied code.
	 */
	protected void applyDiscountCode(final String code, final List<UcpMessage> messages)
	{
		final List<String> candidates = new ArrayList<>();
		candidates.add(code);
		final String upper = code.toUpperCase(Locale.ROOT);
		final String lower = code.toLowerCase(Locale.ROOT);
		if (!candidates.contains(upper))
		{
			candidates.add(upper);
		}
		if (!candidates.contains(lower))
		{
			candidates.add(lower);
		}
		VoucherOperationException lastFailure = null;
		for (final String candidate : candidates)
		{
			try
			{
				voucherFacade.applyVoucher(candidate);
				return;
			}
			catch (final VoucherOperationException e)
			{
				lastFailure = e;
			}
		}
		LOG.info("UCP discounts: voucher '{}' rejected: {}", code,
			lastFailure != null ? lastFailure.getMessage() : "unknown");
		messages.add(new UcpMessage("warning", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
			"Discount code '" + code + "' is not valid for this checkout"));
	}

	private static boolean containsIgnoreCase(final List<String> codes, final String code)
	{
		for (final String candidate : codes)
		{
			if (candidate.equalsIgnoreCase(code))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Overlays the applied-codes echo from the voucher facade — authoritative
	 * for both the coupon-backed facade (this platform aliases
	 * {@code defaultCouponFacade} as {@code voucherFacade}) and classic
	 * vouchers, unlike {@code cartData.appliedVouchers} which only the classic
	 * voucher populator fills.
	 */
	protected void attachAppliedDiscounts(final UcpCheckout checkout)
	{
		final List<String> codes = new ArrayList<>();
		final List<UcpAppliedDiscount> applied = new ArrayList<>();
		try
		{
			for (final VoucherData voucher : voucherFacade.getVouchersForCart())
			{
				final String code = voucher.getVoucherCode() != null ? voucher.getVoucherCode() : voucher.getCode();
				if (code == null || codes.contains(code))
				{
					continue;
				}
				codes.add(code);
				// Official applied_discount entry (discount.json): title and
				// amount are REQUIRED; amount is the positive magnitude in
				// minor units (the signed effect lives in totals[]).
				final String title = voucher.getName() != null ? voucher.getName()
					: (voucher.getDescription() != null ? voucher.getDescription() : code);
				Long amount = 0L;
				if (voucher.getAppliedValue() != null && voucher.getAppliedValue().getValue() != null)
				{
					amount = Math.abs(ucpMoneyConverter.toMinorUnits(
						voucher.getAppliedValue().getValue(), voucher.getAppliedValue().getCurrencyIso()));
				}
				applied.add(new UcpAppliedDiscount(code, title, amount));
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("UCP discounts: could not read applied vouchers: {}", e.getMessage());
		}
		if (!codes.isEmpty())
		{
			final UcpDiscounts discounts = new UcpDiscounts(codes);
			discounts.setApplied(applied);
			checkout.setDiscounts(discounts);
		}
	}

	/** Voucher codes currently applied to the session cart. */
	protected List<String> appliedVoucherCodes()
	{
		final List<String> codes = new ArrayList<>();
		try
		{
			for (final VoucherData voucher : voucherFacade.getVouchersForCart())
			{
				final String code = voucher.getVoucherCode() != null ? voucher.getVoucherCode() : voucher.getCode();
				if (code != null && !codes.contains(code))
				{
					codes.add(code);
				}
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("UCP discounts: could not read applied vouchers: {}", e.getMessage());
		}
		return codes;
	}

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
				// Clamp-and-warn posture (checkout-rest.md "Business Outcomes").
				messages.add(new UcpMessage("warning", "quantity_adjusted", UcpMessage.SEVERITY_RECOVERABLE,
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
	 * Apply the {@code fulfillment} block to the loaded cart. Two request
	 * shapes are honored (ADR 0003):
	 * <ul>
	 *   <li>the spec negotiation flow — {@code methods[]} with
	 *       {@code selected_destination_id} (a saved-address id offered in a
	 *       previous response) and {@code groups[].selected_option_id}
	 *       (option ids are hybris delivery-mode codes);</li>
	 *   <li>the legacy ThinkShop shorthand — an inline {@code destination}
	 *       plus optional {@code delivery_mode}, kept for backward compat.</li>
	 * </ul>
	 * When a destination is applied and no mode was chosen either way, the
	 * cheapest supported mode is auto-selected. Failures are
	 * {@code recoverable} messages.
	 */
	protected void applyFulfillment(final UcpFulfillment fulfillment, final UcpBuyer buyer,
		final List<UcpMessage> messages)
	{
		if (fulfillment == null)
		{
			return;
		}
		// Spec negotiation: select a previously offered saved destination by
		// id — resolved server-side against the customer's address book (the
		// coremcp addressId flow).
		final UcpFulfillmentMethod method = firstMethod(fulfillment);
		if (method != null && method.getSelectedDestinationId() != null
			&& !method.getSelectedDestinationId().isBlank())
		{
			final String selectedId = method.getSelectedDestinationId();
			boolean applied = false;
			// Resolve the id against the ADDRESS BOOK explicitly — the facade's
			// setDeliveryAddress silently resolves an unknown id to null (which
			// CLEARS the address and still returns true).
			final AddressData saved = findSavedAddress(selectedId);
			if (saved != null)
			{
				try
				{
					applied = checkoutFacade.setDeliveryAddress(saved);
				}
				catch (final Exception e)
				{
					LOG.debug("UCP checkout: could not select destination {}: {}", selectedId, e.getMessage());
				}
			}
			if (!applied)
			{
				// Not a saved-address id — the official flow also allows the
				// CLIENT to supply the destination inline in this same
				// methods[].destinations list and select it in one call.
				final UcpShippingDestination inline = findInlineDestination(method, selectedId);
				if (inline != null)
				{
					applied = applyInlineDestination(inline, buyer, messages);
				}
				else
				{
					messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
						"Unknown fulfillment destination id: " + selectedId));
				}
			}
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
		// Delivery mode: the legacy explicit code wins, else the negotiation's
		// selected option id (option ids ARE delivery-mode codes), else
		// auto-selection once a destination is present.
		String requestedMode = fulfillment.getDeliveryMode();
		if ((requestedMode == null || requestedMode.isBlank()) && method != null && method.getGroups() != null)
		{
			for (final UcpFulfillmentGroup group : method.getGroups())
			{
				if (group != null && group.getSelectedOptionId() != null && !group.getSelectedOptionId().isBlank())
				{
					requestedMode = group.getSelectedOptionId();
					break;
				}
			}
		}
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

	/** The customer's saved (address-book) address with the given id, or null. */
	protected AddressData findSavedAddress(final String addressId)
	{
		try
		{
			final List<AddressData> book = userFacade.getAddressBook();
			if (book != null)
			{
				for (final AddressData address : book)
				{
					if (address != null && addressId.equals(address.getId()))
					{
						return address;
					}
				}
			}
		}
		catch (final Exception e)
		{
			LOG.debug("UCP checkout: could not read the address book: {}", e.getMessage());
		}
		return null;
	}

	/** The request-supplied inline destination with the given id, or null. */
	private static UcpShippingDestination findInlineDestination(final UcpFulfillmentMethod method,
		final String destinationId)
	{
		if (method.getDestinations() == null)
		{
			return null;
		}
		for (final UcpShippingDestination destination : method.getDestinations())
		{
			if (destination != null && destinationId.equals(destination.getId()))
			{
				return destination;
			}
		}
		return null;
	}

	/**
	 * Applies a client-supplied inline destination (official
	 * {@code shipping_destination} field names) via the same persist-then-
	 * select path as the legacy inline {@code fulfillment.destination}.
	 */
	protected boolean applyInlineDestination(final UcpShippingDestination destination, final UcpBuyer buyer,
		final List<UcpMessage> messages)
	{
		final AddressData address = toAddressData(destination, buyer);
		try
		{
			userFacade.addAddress(address);
			if (checkoutFacade.setDeliveryAddress(address))
			{
				return true;
			}
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"Delivery destination " + destination.getId() + " was not accepted (this store may not "
					+ "deliver to " + destination.getAddressCountry() + ")"));
		}
		catch (final Exception e)
		{
			LOG.debug("UCP checkout: could not apply inline destination {}: {}",
				destination.getId(), e.getMessage());
			messages.add(new UcpMessage("error", "invalid_request", UcpMessage.SEVERITY_RECOVERABLE,
				"Could not set delivery destination " + destination.getId() + ": " + e.getMessage()));
		}
		return false;
	}

	/** Official {@code shipping_destination} names → hybris address data. */
	protected AddressData toAddressData(final UcpShippingDestination destination, final UcpBuyer buyer)
	{
		final AddressData address = new AddressData();
		address.setFirstName(destination.getFirstName() != null ? destination.getFirstName()
			: buyer != null && buyer.getFirstName() != null ? buyer.getFirstName() : "UCP");
		address.setLastName(destination.getLastName() != null ? destination.getLastName()
			: buyer != null && buyer.getLastName() != null ? buyer.getLastName() : "Agent");
		address.setLine1(destination.getStreetAddress());
		address.setLine2(destination.getExtendedAddress());
		address.setTown(destination.getAddressLocality());
		address.setPostalCode(destination.getPostalCode());
		address.setPhone(destination.getPhoneNumber());
		if (destination.getAddressCountry() != null && !destination.getAddressCountry().isBlank())
		{
			final CountryData country = new CountryData();
			country.setIsocode(destination.getAddressCountry());
			address.setCountry(country);
		}
		if (destination.getAddressRegion() != null && !destination.getAddressRegion().isBlank())
		{
			final RegionData region = new RegionData();
			region.setIsocode(destination.getAddressRegion().contains("-")
				|| destination.getAddressCountry() == null
				? destination.getAddressRegion()
				: destination.getAddressCountry() + "-" + destination.getAddressRegion());
			address.setRegion(region);
		}
		address.setShippingAddress(true);
		return address;
	}

	/** First method of a request fulfillment block, or null. */
	private static UcpFulfillmentMethod firstMethod(final UcpFulfillment fulfillment)
	{
		if (fulfillment == null || fulfillment.getMethods() == null || fulfillment.getMethods().isEmpty())
		{
			return null;
		}
		return fulfillment.getMethods().get(0);
	}

	/**
	 * Echo the request's payment instruments onto the response (the reference
	 * client feeds {@code response.payment} into its next update request —
	 * ADR 0003). The marshaller already defaulted an empty instruments block.
	 */
	protected void echoPayment(final UcpCheckout checkout, final UcpCheckoutRequest payload)
	{
		final List<UcpPaymentInstrument> instruments =
			payload != null && payload.getPayment() != null ? payload.getPayment().getInstruments() : null;
		if (instruments != null && !instruments.isEmpty())
		{
			// STRIP credentials before echo — the echoed instruments are also
			// what recordCompletion persists, and raw payment credentials are
			// never echoed, logged or stored (review finding / runbook §5/§8).
			final List<UcpPaymentInstrument> sanitized = new ArrayList<>();
			for (final UcpPaymentInstrument instrument : instruments)
			{
				if (instrument != null)
				{
					sanitized.add(instrument.sanitized());
				}
			}
			checkout.setPayment(new UcpPayment(sanitized));
		}
	}

	/**
	 * Attach the spec fulfillment negotiation state (ADR 0003) to a response,
	 * rebuilt statelessly from the request + cart + address book on every
	 * call:
	 * <ul>
	 *   <li>a method is present when the request negotiated one OR the cart
	 *       already has a delivery address (so get_checkout keeps echoing the
	 *       negotiated state);</li>
	 *   <li>{@code destinations[]} are the customer's saved addresses; the
	 *       applied cart address is appended when it matches none of them
	 *       (the cart holds a clone with its own id);</li>
	 *   <li>{@code groups[].options[]} (the supported delivery modes, cheapest
	 *       first) appear once a destination is applied —
	 *       {@code selected_option_id} echoes the cart's current mode.</li>
	 * </ul>
	 */
	protected void attachFulfillmentNegotiation(final UcpCheckout checkout, final UcpFulfillment requested,
		final CartData cart)
	{
		final UcpFulfillmentMethod requestMethod = firstMethod(requested);
		final AddressData applied = cart != null ? cart.getDeliveryAddress() : null;
		if (requestMethod == null && applied == null)
		{
			return;
		}

		final UcpFulfillmentMethod method = new UcpFulfillmentMethod();
		method.setId(requestMethod != null && requestMethod.getId() != null
			? requestMethod.getId() : "method_1");
		method.setType(requestMethod != null && requestMethod.getType() != null
			? requestMethod.getType() : UcpFulfillmentMethod.TYPE_SHIPPING);
		final List<String> allLineItemIds = new ArrayList<>();
		if (checkout.getLineItems() != null)
		{
			for (final UcpLineItem lineItem : checkout.getLineItems())
			{
				allLineItemIds.add(lineItem.getId());
			}
		}
		method.setLineItemIds(requestMethod != null && requestMethod.getLineItemIds() != null
			&& !requestMethod.getLineItemIds().isEmpty() ? requestMethod.getLineItemIds() : allLineItemIds);

		// Offer the saved addresses as destinations.
		final List<UcpShippingDestination> destinations = new ArrayList<>();
		try
		{
			final List<AddressData> book = userFacade.getAddressBook();
			if (book != null)
			{
				for (final AddressData address : book)
				{
					if (address != null && address.getId() != null)
					{
						destinations.add(toDestination(address));
					}
				}
			}
		}
		catch (final Exception e)
		{
			LOG.debug("UCP checkout: could not read the address book: {}", e.getMessage());
		}
		// Union echo (official behavior): the response offers the saved
		// addresses PLUS any destinations the request supplied inline, under
		// the CLIENT's ids — a new address must remain addressable by the id
		// the client minted for it.
		if (requestMethod != null && requestMethod.getDestinations() != null)
		{
			for (final UcpShippingDestination clientDestination : requestMethod.getDestinations())
			{
				if (clientDestination != null && clientDestination.getId() != null
					&& !clientDestination.getId().isBlank()
					&& destinations.stream().noneMatch(d -> clientDestination.getId().equals(d.getId())))
				{
					destinations.add(clientDestination);
				}
			}
		}
		String selectedDestinationId = null;
		if (applied != null)
		{
			// Prefer a request-supplied inline destination match — echo the
			// client's id, not the cart's internal address clone id.
			if (requestMethod != null && requestMethod.getDestinations() != null)
			{
				for (final UcpShippingDestination clientDestination : requestMethod.getDestinations())
				{
					if (clientDestination != null && clientDestination.getId() != null
						&& sameAddress(clientDestination, applied))
					{
						selectedDestinationId = clientDestination.getId();
						break;
					}
				}
			}
			if (selectedDestinationId == null)
			{
				for (final UcpShippingDestination destination : destinations)
				{
					if (sameAddress(destination, applied))
					{
						selectedDestinationId = destination.getId();
						break;
					}
				}
			}
			if (selectedDestinationId == null && applied.getId() != null)
			{
				// Applied via the legacy inline-destination path — the cart's
				// address clone is not in the book; offer it as itself.
				final UcpShippingDestination current = toDestination(applied);
				destinations.add(current);
				selectedDestinationId = current.getId();
			}
		}
		if (!destinations.isEmpty())
		{
			method.setDestinations(destinations);
		}
		method.setSelectedDestinationId(selectedDestinationId);

		// The group block is ALWAYS attached (clients index groups[0]
		// unconditionally); options populate once a destination is applied —
		// delivery modes depend on the address, so the list is empty before.
		final UcpFulfillmentGroup group = new UcpFulfillmentGroup();
		String groupId = "group_1";
		if (requestMethod != null && requestMethod.getGroups() != null
			&& !requestMethod.getGroups().isEmpty() && requestMethod.getGroups().get(0) != null
			&& requestMethod.getGroups().get(0).getId() != null)
		{
			groupId = requestMethod.getGroups().get(0).getId();
		}
		group.setId(groupId);
		group.setLineItemIds(method.getLineItemIds());
		group.setOptions(applied != null ? deliveryModeOptions() : List.of());
		group.setSelectedOptionId(applied != null && cart.getDeliveryMode() != null
			? cart.getDeliveryMode().getCode() : null);
		method.setGroups(List.of(group));

		final UcpFulfillment fulfillment = checkout.getFulfillment() != null
			? checkout.getFulfillment() : new UcpFulfillment();
		fulfillment.setMethods(List.of(method));
		checkout.setFulfillment(fulfillment);
	}

	/** The supported delivery modes as fulfillment options, cheapest first. */
	protected List<UcpFulfillmentOption> deliveryModeOptions()
	{
		final List<UcpFulfillmentOption> options = new ArrayList<>();
		try
		{
			final List<DeliveryModeData> modes = new ArrayList<>(checkoutFacade.getSupportedDeliveryModes());
			modes.sort((a, b) -> costOf(a).compareTo(costOf(b)));
			for (final DeliveryModeData mode : modes)
			{
				if (mode == null || mode.getCode() == null)
				{
					continue;
				}
				final UcpFulfillmentOption option = new UcpFulfillmentOption();
				option.setId(mode.getCode());
				option.setTitle(mode.getName() != null ? mode.getName() : mode.getCode());
				option.setDescription(mode.getDescription());
				Long amount = Long.valueOf(0L);
				if (mode.getDeliveryCost() != null && mode.getDeliveryCost().getValue() != null)
				{
					amount = ucpMoneyConverter.toMinorUnits(mode.getDeliveryCost().getValue(),
						mode.getDeliveryCost().getCurrencyIso());
				}
				option.setTotals(List.of(new UcpTotal(UcpTotal.TYPE_SUBTOTAL, amount),
					new UcpTotal(UcpTotal.TYPE_TOTAL, amount)));
				options.add(option);
			}
		}
		catch (final Exception e)
		{
			LOG.debug("UCP checkout: could not list delivery modes: {}", e.getMessage());
		}
		return options;
	}

	private static BigDecimal costOf(final DeliveryModeData mode)
	{
		return mode != null && mode.getDeliveryCost() != null && mode.getDeliveryCost().getValue() != null
			? mode.getDeliveryCost().getValue() : BigDecimal.ZERO;
	}

	/** Map a hybris address onto the spec ShippingDestination (PostalAddress + id). */
	protected UcpShippingDestination toDestination(final AddressData address)
	{
		final UcpShippingDestination destination = new UcpShippingDestination();
		destination.setId(address.getId());
		destination.setStreetAddress(address.getLine1());
		destination.setExtendedAddress(address.getLine2());
		destination.setAddressLocality(address.getTown());
		if (address.getRegion() != null)
		{
			destination.setAddressRegion(address.getRegion().getIsocodeShort() != null
				? address.getRegion().getIsocodeShort() : address.getRegion().getIsocode());
		}
		if (address.getCountry() != null)
		{
			destination.setAddressCountry(address.getCountry().getIsocode());
		}
		destination.setPostalCode(address.getPostalCode());
		destination.setFirstName(address.getFirstName());
		destination.setLastName(address.getLastName());
		destination.setPhoneNumber(address.getPhone());
		return destination;
	}

	/**
	 * Field-wise match between an offered destination and the cart's applied
	 * delivery address — the cart holds a CLONE of the selected book address
	 * with its own id, so ids never match across the two.
	 */
	private static boolean sameAddress(final UcpShippingDestination destination, final AddressData applied)
	{
		return equalsIgnoreCaseSafe(destination.getStreetAddress(), applied.getLine1())
			&& equalsIgnoreCaseSafe(destination.getPostalCode(), applied.getPostalCode())
			&& equalsIgnoreCaseSafe(destination.getAddressLocality(), applied.getTown());
	}

	private static boolean equalsIgnoreCaseSafe(final String a, final String b)
	{
		return a == null ? b == null : a.equalsIgnoreCase(b);
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
				// Clamp-and-warn posture (checkout-rest.md "Business Outcomes"):
				// the canonical code for a stock-clamped quantity.
				messages.add(new UcpMessage("warning", "quantity_adjusted", UcpMessage.SEVERITY_RECOVERABLE,
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

	/** SHA-256 hex of the canonical request payload JSON (idempotency compare). */
	protected String requestHash(final UcpCheckoutRequest payload)
	{
		try
		{
			final String canonical = objectMapper.writeValueAsString(payload);
			final java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			final byte[] hashed = digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			final StringBuilder hex = new StringBuilder(hashed.length * 2);
			for (final byte b : hashed)
			{
				hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (final Exception e)
		{
			LOG.debug("UCP idempotency: could not hash request: {}", e.getMessage());
			return "unhashable";
		}
	}

	/** Deserialize a stored checkout response for idempotent replay; null on failure. */
	protected UcpCheckout parseCheckoutJson(final String json)
	{
		try
		{
			return objectMapper.readValue(json, UcpCheckout.class);
		}
		catch (final Exception e)
		{
			LOG.warn("UCP idempotency: stored response unreadable: {}", e.getMessage());
			return null;
		}
	}

	@Required
	public void setVoucherFacade(final VoucherFacade voucherFacade)
	{
		this.voucherFacade = voucherFacade;
	}

	@Required
	public void setUcpIdempotencyService(final UcpIdempotencyService ucpIdempotencyService)
	{
		this.ucpIdempotencyService = ucpIdempotencyService;
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

	@Required
	public void setUcpMoneyConverter(final UcpMoneyConverter ucpMoneyConverter)
	{
		this.ucpMoneyConverter = ucpMoneyConverter;
	}
}
