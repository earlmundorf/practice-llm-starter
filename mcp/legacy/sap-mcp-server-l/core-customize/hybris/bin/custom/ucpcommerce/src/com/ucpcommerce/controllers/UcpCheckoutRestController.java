package com.ucpcommerce.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ucpcommerce.dto.UcpCheckoutRequest;
import com.ucpcommerce.services.UcpCheckoutService;

import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * UCP REST checkout binding (Phase 7): the five checkout-session routes over
 * the identical binding-agnostic {@link UcpCheckoutService} the MCP tools use
 * (design R12) — zero service-layer changes.
 *
 * Resource naming: {@code /checkout-sessions} (ADR 0002) — the shape of
 * Google's Native-checkout REST surface documented in the task research; the
 * pinned spec could not be consulted locally, so the researched production
 * client shape wins over the runbook's alternate {@code /checkouts} spelling.
 *
 * <pre>
 *   POST /occ/v2/{baseSiteId}/ucp/checkout-sessions                  create
 *   GET  /occ/v2/{baseSiteId}/ucp/checkout-sessions/{id}             get
 *   PUT  /occ/v2/{baseSiteId}/ucp/checkout-sessions/{id}             update
 *   POST /occ/v2/{baseSiteId}/ucp/checkout-sessions/{id}/complete    complete
 *   POST /occ/v2/{baseSiteId}/ucp/checkout-sessions/{id}/cancel      cancel
 * </pre>
 *
 * The {@code Idempotency-Key} header maps to the same service parameter the
 * MCP tools take from {@code meta["idempotency-key"]}; the service itself
 * enforces its presence on complete/cancel (Phase 5 decision), so a missing
 * header is an {@link IllegalArgumentException} → HTTP 400 here.
 *
 * Payload {@code id} rule (corrected in ADR 0003): the SDK's
 * {@code CheckoutUpdateRequest} carries an {@code id} and the official
 * reference client sends it on every update, so a body id MATCHING the path
 * id is accepted; a MISMATCHED id (or any id on create) is still a client
 * protocol bug → 400.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class UcpCheckoutRestController extends AbstractUcpRestController
{
	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	@Resource(name = "ucpCheckoutService")
	private UcpCheckoutService ucpCheckoutService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/checkout-sessions", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public String create(@RequestBody final String body, final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpCheckoutService.create(parseCheckoutPayload(body, null)), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/checkout-sessions/{checkoutId}", method = RequestMethod.GET,
		produces = "application/json")
	@ResponseBody
	public String get(@PathVariable final String checkoutId, final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpCheckoutService.get(checkoutId), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/checkout-sessions/{checkoutId}", method = RequestMethod.PUT,
		produces = "application/json")
	@ResponseBody
	public String update(@PathVariable final String checkoutId, @RequestBody final String body,
		final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(ucpCheckoutService.update(checkoutId, parseCheckoutPayload(body, checkoutId)), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/checkout-sessions/{checkoutId}/complete", method = RequestMethod.POST,
		produces = "application/json")
	@ResponseBody
	public String complete(@PathVariable final String checkoutId, @RequestBody final String body,
		@RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) final String idempotencyKey,
		final HttpServletResponse response) throws IOException
	{
		try
		{
			return json(
				ucpCheckoutService.complete(checkoutId, parseCheckoutPayload(body, checkoutId), idempotencyKey),
				response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/ucp/checkout-sessions/{checkoutId}/cancel", method = RequestMethod.POST,
		produces = "application/json")
	@ResponseBody
	public String cancel(@PathVariable final String checkoutId,
		@RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) final String idempotencyKey,
		final HttpServletResponse response) throws IOException
	{
		// Cancel takes no checkout payload (any request body is ignored, as on
		// the MCP binding where cancel_checkout carries only id + meta).
		try
		{
			return json(ucpCheckoutService.cancel(checkoutId, idempotencyKey), response);
		}
		catch (final IllegalArgumentException e)
		{
			return badRequest(e.getMessage(), response);
		}
	}

	/**
	 * Parse the raw request body into the shared inbound checkout DTO,
	 * enforcing the payload-id rule (ADR 0003) before conversion: an id equal
	 * to the addressed resource is accepted (the SDK update-request shape),
	 * anything else — an id on create, or a mismatch — is a protocol bug.
	 *
	 * @param expectedId the path id the body may echo, or null on create
	 */
	private UcpCheckoutRequest parseCheckoutPayload(final String body, final String expectedId)
	{
		final Map<String, Object> raw;
		try
		{
			raw = objectMapper.readValue(body == null ? "" : body, new TypeReference<Map<String, Object>>()
			{
			});
		}
		catch (final Exception e)
		{
			throw new IllegalArgumentException("checkout payload must be a JSON object");
		}
		if (raw == null)
		{
			throw new IllegalArgumentException("checkout payload is required");
		}
		if (raw.containsKey("id"))
		{
			if (expectedId == null)
			{
				throw new IllegalArgumentException(
					"checkout payload must not contain an id on create; the response mints one");
			}
			if (!expectedId.equals(raw.get("id")))
			{
				throw new IllegalArgumentException("checkout payload id does not match the URL path id");
			}
			raw.remove("id");
		}
		try
		{
			return objectMapper.convertValue(raw, UcpCheckoutRequest.class);
		}
		catch (final Exception e)
		{
			throw new IllegalArgumentException("invalid checkout payload: " + e.getMessage());
		}
	}
}
