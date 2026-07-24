package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code discounts} block of a checkout payload — {@code codes[]} carries
 * merchant discount (voucher) codes. On a request the list is DECLARATIVE
 * like {@code line_items}: codes not yet applied are applied, applied codes
 * absent from the list are released, and an absent block leaves the applied
 * codes unchanged. On a response it echoes the currently applied codes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpDiscounts
{
	@JsonProperty("codes")
	private List<String> codes;

	public UcpDiscounts()
	{
		// for Jackson
	}

	public UcpDiscounts(final List<String> codes)
	{
		this.codes = codes;
	}

	public List<String> getCodes()
	{
		return codes;
	}

	public void setCodes(final List<String> codes)
	{
		this.codes = codes;
	}
}
