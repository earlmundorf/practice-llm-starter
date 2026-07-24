package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for the catalog {@code search_catalog} / {@code
 * lookup_catalog} operations: {@code ucp} envelope + {@code products[]}, with
 * {@code pagination} on search and per-id {@code messages[]} on lookup misses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpCatalogResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("products")
	private List<UcpProduct> products = new ArrayList<>();

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

	public List<UcpProduct> getProducts()
	{
		return products;
	}

	public void setProducts(final List<UcpProduct> products)
	{
		this.products = products;
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
