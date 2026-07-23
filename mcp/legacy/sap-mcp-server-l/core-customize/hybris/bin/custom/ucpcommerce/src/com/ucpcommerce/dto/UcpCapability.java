package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in the profile's {@code capabilities} array — a reverse-domain
 * capability name (standard {@code dev.ucp.*} or custom {@code com.thinkshop.*})
 * plus its dated version and spec/schema URLs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpCapability
{
	@JsonProperty("name")
	private String name;

	@JsonProperty("version")
	private String version;

	@JsonProperty("spec")
	private String spec;

	@JsonProperty("schema")
	private String schema;

	public UcpCapability()
	{
		// for Jackson
	}

	public UcpCapability(final String name, final String version)
	{
		this.name = name;
		this.version = version;
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

	public String getSchema()
	{
		return schema;
	}

	public void setSchema(final String schema)
	{
		this.schema = schema;
	}
}
