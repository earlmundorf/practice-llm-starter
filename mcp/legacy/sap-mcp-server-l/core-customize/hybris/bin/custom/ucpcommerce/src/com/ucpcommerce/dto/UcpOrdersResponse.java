package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for {@code list_orders} (order history): {@code ucp}
 * envelope + {@code orders[]} summaries + {@code pagination}, scoped to the
 * authenticated customer by the standard {@code OrderFacade} contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpOrdersResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("orders")
	private List<UcpOrder> orders = new ArrayList<>();

	@JsonProperty("pagination")
	private UcpPagination pagination;

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

	public List<UcpOrder> getOrders()
	{
		return orders;
	}

	public void setOrders(final List<UcpOrder> orders)
	{
		this.orders = orders;
	}

	public UcpPagination getPagination()
	{
		return pagination;
	}

	public void setPagination(final UcpPagination pagination)
	{
		this.pagination = pagination;
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
