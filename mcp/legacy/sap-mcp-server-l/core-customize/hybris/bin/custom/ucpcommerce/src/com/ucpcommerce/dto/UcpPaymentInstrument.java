package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * One entry in the complete_checkout payload's
 * {@code payment.instruments[]} (runbook §2.2 complete request):
 * {@code {handler_id, type, credential}}. The {@code handler_id} must match a
 * handler declared in the profile's {@code payment_handlers}; for the mock
 * handler ({@code thinkshop_mock_card}, design R9) any credential token is
 * accepted and the credential content is deliberately never inspected, logged
 * or stored (runbook §5/§8: no raw payment credentials in logs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpPaymentInstrument
{
	@JsonProperty("handler_id")
	private String handlerId;

	@JsonProperty("type")
	private String type;

	@JsonProperty("credential")
	private Map<String, Object> credential;

	public String getHandlerId()
	{
		return handlerId;
	}

	public void setHandlerId(final String handlerId)
	{
		this.handlerId = handlerId;
	}

	public String getType()
	{
		return type;
	}

	public void setType(final String type)
	{
		this.type = type;
	}

	public Map<String, Object> getCredential()
	{
		return credential;
	}

	public void setCredential(final Map<String, Object> credential)
	{
		this.credential = credential;
	}
}
