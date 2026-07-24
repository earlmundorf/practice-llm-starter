package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One handler entry in the profile's {@code payment_handlers} registry (a
 * map keyed by the handler's reverse-domain namespace whose values are LISTS
 * of these, per the official discovery schema — ADR 0003). The reference
 * client flattens all registry values and matches on {@code id}. Phase 5
 * declares the single mock handler ({@code thinkshop_mock_card}) —
 * complete_checkout accepts any credential token for that id and runs the
 * existing mock-Visa flow (design R9).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPaymentHandler
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("name")
	private String name;

	@JsonProperty("version")
	private String version;

	@JsonProperty("spec")
	private String spec;

	/** Handler-specific configuration; the mock handler declares none ({@code {}}). */
	@JsonProperty("config")
	private Map<String, Object> config = new LinkedHashMap<>();

	public UcpPaymentHandler()
	{
		// for Jackson
	}

	public UcpPaymentHandler(final String id, final String name)
	{
		this.id = id;
		this.name = name;
	}

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(final String version)
	{
		this.version = version;
	}

	public String getSpec()
	{
		return spec;
	}

	public void setSpec(final String spec)
	{
		this.spec = spec;
	}

	public Map<String, Object> getConfig()
	{
		return config;
	}

	public void setConfig(final Map<String, Object> config)
	{
		this.config = config;
	}
}
