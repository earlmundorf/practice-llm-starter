package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The UCP fulfillment {@code destination} block — the shipping address a
 * checkout is delivered to (runbook §3.5: {@code context.address_country} /
 * destination drives tax and delivery modes). Accepted on create/update and
 * echoed back from the cart's applied delivery address.
 *
 * Shape is a judgment call consistent with the Phase 2/3 wire-format
 * decisions (provisional until verified against the pinned schema repo):
 * snake_case field names, ISO country code, optional region.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpDestination
{
	@JsonProperty("first_name")
	private String firstName;

	@JsonProperty("last_name")
	private String lastName;

	@JsonProperty("line1")
	private String line1;

	@JsonProperty("line2")
	private String line2;

	@JsonProperty("city")
	private String city;

	@JsonProperty("region")
	private String region;

	@JsonProperty("postal_code")
	private String postalCode;

	@JsonProperty("country")
	private String country;

	@JsonProperty("phone_number")
	private String phoneNumber;

	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName(final String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}

	public String getLine1()
	{
		return line1;
	}

	public void setLine1(final String line1)
	{
		this.line1 = line1;
	}

	public String getLine2()
	{
		return line2;
	}

	public void setLine2(final String line2)
	{
		this.line2 = line2;
	}

	public String getCity()
	{
		return city;
	}

	public void setCity(final String city)
	{
		this.city = city;
	}

	public String getRegion()
	{
		return region;
	}

	public void setRegion(final String region)
	{
		this.region = region;
	}

	public String getPostalCode()
	{
		return postalCode;
	}

	public void setPostalCode(final String postalCode)
	{
		this.postalCode = postalCode;
	}

	public String getCountry()
	{
		return country;
	}

	public void setCountry(final String country)
	{
		this.country = country;
	}

	public String getPhoneNumber()
	{
		return phoneNumber;
	}

	public void setPhoneNumber(final String phoneNumber)
	{
		this.phoneNumber = phoneNumber;
	}
}
