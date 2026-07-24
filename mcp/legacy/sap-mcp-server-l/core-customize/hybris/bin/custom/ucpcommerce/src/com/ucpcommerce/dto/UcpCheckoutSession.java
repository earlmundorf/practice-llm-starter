package com.ucpcommerce.dto;

import java.time.Instant;

/**
 * Internal (never on the wire) view of one {@code UcpCheckoutSessionEntry}
 * item: the opaque checkout id → hybris cart code mapping plus the UCP-side
 * protocol state (design R5). The coremcp {@code McpSession} idiom — a plain
 * DTO so callers never touch models directly.
 */
public class UcpCheckoutSession
{
	private String checkoutId;
	private String cartCode;
	/** UCP status code string, e.g. {@link UcpCheckout#STATUS_INCOMPLETE}. */
	private String status;
	private String buyerJson;
	private String idempotencyKey;
	private String completionResponseJson;
	private String orderCode;
	private Instant createdAt;
	private Instant lastAccessedAt;

	public String getCheckoutId()
	{
		return checkoutId;
	}

	public void setCheckoutId(final String checkoutId)
	{
		this.checkoutId = checkoutId;
	}

	public String getCartCode()
	{
		return cartCode;
	}

	public void setCartCode(final String cartCode)
	{
		this.cartCode = cartCode;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}

	public String getBuyerJson()
	{
		return buyerJson;
	}

	public void setBuyerJson(final String buyerJson)
	{
		this.buyerJson = buyerJson;
	}

	public String getIdempotencyKey()
	{
		return idempotencyKey;
	}

	public void setIdempotencyKey(final String idempotencyKey)
	{
		this.idempotencyKey = idempotencyKey;
	}

	public String getCompletionResponseJson()
	{
		return completionResponseJson;
	}

	public void setCompletionResponseJson(final String completionResponseJson)
	{
		this.completionResponseJson = completionResponseJson;
	}

	public String getOrderCode()
	{
		return orderCode;
	}

	public void setOrderCode(final String orderCode)
	{
		this.orderCode = orderCode;
	}

	public Instant getCreatedAt()
	{
		return createdAt;
	}

	public void setCreatedAt(final Instant createdAt)
	{
		this.createdAt = createdAt;
	}

	public Instant getLastAccessedAt()
	{
		return lastAccessedAt;
	}

	public void setLastAccessedAt(final Instant lastAccessedAt)
	{
		this.lastAccessedAt = lastAccessedAt;
	}
}
