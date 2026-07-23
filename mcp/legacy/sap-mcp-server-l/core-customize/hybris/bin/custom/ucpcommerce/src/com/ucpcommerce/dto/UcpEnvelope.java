package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code ucp} metadata block that leads every UCP document — the profile
 * carries {@code version} only; capability responses (later phases) add
 * {@code status} ("success" / "error").
 *
 * Hand-written Jackson class, never a generated WsDTO (coremcp ADR 0005 idiom).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpEnvelope
{
	@JsonProperty("version")
	private String version;

	@JsonProperty("status")
	private String status;

	public UcpEnvelope()
	{
		// for Jackson
	}

	public UcpEnvelope(final String version)
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

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}
}
