package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response payload for {@code get_order}: {@code ucp} envelope + a single
 * full {@code order}, or on business failure {@code ucp.status="error"} +
 * {@code messages[]} (never a 500 / transport error).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpOrderResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("order")
	private UcpOrder order;

	@JsonProperty("messages")
	private List<UcpMessage> messages;

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public UcpOrder getOrder()
	{
		return order;
	}

	public void setOrder(final UcpOrder order)
	{
		this.order = order;
	}

	public List<UcpMessage> getMessages()
	{
		return messages;
	}

	public void setMessages(final List<UcpMessage> messages)
	{
		this.messages = messages;
	}
}
