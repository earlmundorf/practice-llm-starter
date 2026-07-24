package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code item} reference inside a request line item —
 * {@code line_items[].item.id} is the product code/SKU (runbook §3.5).
 * Request-side counterpart of the response's full {@link UcpProduct}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpItemRef
{
	@JsonProperty("id")
	private String id;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}
}
