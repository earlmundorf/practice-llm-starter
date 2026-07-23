package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response payload for {@code get_product}: {@code ucp} envelope + a single
 * {@code product}, or on business failure {@code ucp.status="error"} +
 * {@code messages[]} (never a 500 / transport error).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpProductResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("product")
	private UcpProduct product;

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

	public UcpProduct getProduct()
	{
		return product;
	}

	public void setProduct(final UcpProduct product)
	{
		this.product = product;
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
