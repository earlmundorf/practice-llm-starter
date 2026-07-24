package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in {@code fulfillment.methods[].destinations[]} — the spec's
 * {@code ShippingDestination}: an {@code id} plus PostalAddress fields
 * (python-sdk {@code postal_address.py}: {@code street_address},
 * {@code address_locality}, {@code address_region}, {@code address_country},
 * {@code postal_code}, contact name/phone). Offered from the customer's
 * saved addresses; the agent selects one by id.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpShippingDestination
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("street_address")
	private String streetAddress;

	@JsonProperty("extended_address")
	private String extendedAddress;

	@JsonProperty("address_locality")
	private String addressLocality;

	@JsonProperty("address_region")
	private String addressRegion;

	@JsonProperty("address_country")
	private String addressCountry;

	@JsonProperty("postal_code")
	private String postalCode;

	@JsonProperty("first_name")
	private String firstName;

	@JsonProperty("last_name")
	private String lastName;

	@JsonProperty("phone_number")
	private String phoneNumber;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getStreetAddress()
	{
		return streetAddress;
	}

	public void setStreetAddress(final String streetAddress)
	{
		this.streetAddress = streetAddress;
	}

	public String getExtendedAddress()
	{
		return extendedAddress;
	}

	public void setExtendedAddress(final String extendedAddress)
	{
		this.extendedAddress = extendedAddress;
	}

	public String getAddressLocality()
	{
		return addressLocality;
	}

	public void setAddressLocality(final String addressLocality)
	{
		this.addressLocality = addressLocality;
	}

	public String getAddressRegion()
	{
		return addressRegion;
	}

	public void setAddressRegion(final String addressRegion)
	{
		this.addressRegion = addressRegion;
	}

	public String getAddressCountry()
	{
		return addressCountry;
	}

	public void setAddressCountry(final String addressCountry)
	{
		this.addressCountry = addressCountry;
	}

	public String getPostalCode()
	{
		return postalCode;
	}

	public void setPostalCode(final String postalCode)
	{
		this.postalCode = postalCode;
	}

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

	public String getPhoneNumber()
	{
		return phoneNumber;
	}

	public void setPhoneNumber(final String phoneNumber)
	{
		this.phoneNumber = phoneNumber;
	}
}
