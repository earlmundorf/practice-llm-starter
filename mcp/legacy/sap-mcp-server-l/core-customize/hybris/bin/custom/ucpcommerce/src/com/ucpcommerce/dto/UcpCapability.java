package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One version entry in the profile's {@code capabilities} registry. The
 * capability NAME is the registry map key (per the official discovery schema
 * — ADR 0003), so the entry itself carries only version/spec/schema plus the
 * optional {@code extends} pointer for extension capabilities (e.g.
 * {@code dev.ucp.shopping.fulfillment} extends {@code …checkout}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpCapability
{
	@JsonProperty("version")
	private String version;

	@JsonProperty("spec")
	private String spec;

	@JsonProperty("schema")
	private String schema;

	@JsonProperty("extends")
	private String extendsCapability;

	public UcpCapability()
	{
		// for Jackson
	}

	public UcpCapability(final String version)
	{
		this.version = version;
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

	public String getSchema()
	{
		return schema;
	}

	public void setSchema(final String schema)
	{
		this.schema = schema;
	}

	public String getExtendsCapability()
	{
		return extendsCapability;
	}

	public void setExtendsCapability(final String extendsCapability)
	{
		this.extendsCapability = extendsCapability;
	}
}
