package com.ucpcommerce.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpCheckout;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpMessage;

import de.hybris.platform.util.Config;

import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared plumbing for the UCP REST binding controllers (Phase 7).
 *
 * The REST controllers are thin adapters over the identical binding-agnostic
 * capability services the MCP tools use (design R12). Error taxonomy mirrors
 * the MCP binding exactly:
 * <ul>
 *   <li><strong>Business errors</strong> travel inside an HTTP-200 payload
 *       ({@code ucp.status="error"} + {@code messages[]}) — the services
 *       produce them; the controllers just serialize.</li>
 *   <li><strong>Client protocol bugs</strong> (malformed body, a checkout
 *       payload carrying an {@code id}, a missing {@code Idempotency-Key}
 *       header) surface as {@link IllegalArgumentException} — the REST
 *       equivalent of an MCP {@code isError} tool result — and are mapped to
 *       <strong>HTTP 400</strong> with a UCP error envelope body.</li>
 * </ul>
 */
abstract class AbstractUcpRestController
{
	protected final ObjectMapper objectMapper = new ObjectMapper();

	/** Serialize a capability-service payload as the HTTP-200 JSON response. */
	protected String json(final Object payload, final HttpServletResponse response) throws IOException
	{
		response.setContentType("application/json");
		return objectMapper.writeValueAsString(payload);
	}

	/**
	 * Serialize a checkout payload with the official REST status semantics:
	 * most business errors stay in-band (HTTP 200, {@code ucp.status="error"}
	 * + {@code messages[]}), but the spec pins dedicated statuses for a few
	 * well-known outcomes — state conflicts are 409, a declined payment is
	 * 402, an unsupported requested UCP version is 422, completing a checkout
	 * that is not ready is 400, and a successful CREATE is 201.
	 */
	protected String jsonCheckout(final UcpCheckout payload, final HttpServletResponse response,
		final boolean created) throws IOException
	{
		final int status = checkoutHttpStatus(payload, created);
		if (status != HttpServletResponse.SC_OK)
		{
			response.setStatus(status);
		}
		return json(payload, response);
	}

	private static int checkoutHttpStatus(final UcpCheckout payload, final boolean created)
	{
		if (payload != null && payload.getMessages() != null)
		{
			for (final UcpMessage message : payload.getMessages())
			{
				switch (String.valueOf(message != null ? message.getCode() : null))
				{
					case "conflict":
						return HttpServletResponse.SC_CONFLICT;
					case "payment_declined":
						return HttpServletResponse.SC_PAYMENT_REQUIRED;
					case "version_unsupported":
						return 422;
					case "not_ready":
						return HttpServletResponse.SC_BAD_REQUEST;
					default:
						break;
				}
			}
		}
		final boolean isError = payload != null && payload.getUcp() != null
			&& "error".equals(payload.getUcp().getStatus());
		return created && !isError ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK;
	}

	/**
	 * Client protocol bug → HTTP 400 with a UCP error envelope
	 * ({@code invalid_request} / {@code unrecoverable}) so REST clients get a
	 * consistent UCP-shaped body on every response.
	 */
	protected String badRequest(final String content, final HttpServletResponse response) throws IOException
	{
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setContentType("application/json");

		final UcpEnvelope envelope = new UcpEnvelope(pinnedUcpVersion());
		envelope.setStatus("error");

		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("ucp", envelope);
		body.put("messages", List.of(new UcpMessage("error", "invalid_request",
			UcpMessage.SEVERITY_UNRECOVERABLE, content)));
		return objectMapper.writeValueAsString(body);
	}

	/**
	 * The pinned UCP spec version for 400-body envelopes. Falls back to the
	 * shipped default when no platform Config is available (unit tests).
	 */
	protected String pinnedUcpVersion()
	{
		try
		{
			return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY,
				UcpcommerceConstants.UCP_VERSION_DEFAULT);
		}
		catch (final Exception | NoClassDefFoundError e)
		{
			return UcpcommerceConstants.UCP_VERSION_DEFAULT;
		}
	}

	/**
	 * Version negotiation (protocol.md): the {@code UCP-Agent} header may pin
	 * the protocol version the agent speaks
	 * ({@code profile="…"; version="YYYY-MM-DD"}). A version this server does
	 * not implement is rejected with HTTP 422 and a UCP error envelope; an
	 * absent header or version attribute passes.
	 *
	 * @return the 422 response body, or null when the version is acceptable
	 */
	protected String rejectUnsupportedAgentVersion(final String ucpAgentHeader,
		final HttpServletResponse response) throws IOException
	{
		if (ucpAgentHeader == null)
		{
			return null;
		}
		final java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("version\\s*=\\s*\"([^\"]+)\"").matcher(ucpAgentHeader);
		if (!matcher.find())
		{
			return null;
		}
		final String requested = matcher.group(1);
		final String pinned = pinnedUcpVersion();
		if (pinned.equals(requested))
		{
			return null;
		}
		response.setStatus(422);
		response.setContentType("application/json");
		final UcpEnvelope envelope = new UcpEnvelope(pinned);
		envelope.setStatus("error");
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("ucp", envelope);
		body.put("messages", List.of(new UcpMessage("error", "version_unsupported",
			UcpMessage.SEVERITY_UNRECOVERABLE,
			"UCP version " + requested + " is not supported; this server implements " + pinned)));
		return objectMapper.writeValueAsString(body);
	}

	/**
	 * Parse an optional integer query parameter; a malformed value is a client
	 * protocol bug (→ 400), never a 500.
	 */
	protected int intParam(final String value, final int defaultValue, final String name)
	{
		if (value == null || value.isBlank())
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (final NumberFormatException e)
		{
			throw new IllegalArgumentException(name + " must be an integer");
		}
	}

	/** Split a comma-separated query parameter; null/blank → null (absent). */
	protected List<String> csvParam(final String value)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		final List<String> parts = Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
		return parts.isEmpty() ? null : parts;
	}
}
