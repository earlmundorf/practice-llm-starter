package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code payment} block of a checkout payload — carries the
 * {@code instruments[]} referenced by {@code complete_checkout} (design R9:
 * one declared mock handler; unknown handler ids are rejected with an
 * unrecoverable message).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpPayment
{
	@JsonProperty("instruments")
	private List<UcpPaymentInstrument> instruments;

	public UcpPayment()
	{
		// for Jackson
	}

	public UcpPayment(final List<UcpPaymentInstrument> instruments)
	{
		this.instruments = instruments;
	}

	public List<UcpPaymentInstrument> getInstruments()
	{
		return instruments;
	}

	public void setInstruments(final List<UcpPaymentInstrument> instruments)
	{
		this.instruments = instruments;
	}
}
