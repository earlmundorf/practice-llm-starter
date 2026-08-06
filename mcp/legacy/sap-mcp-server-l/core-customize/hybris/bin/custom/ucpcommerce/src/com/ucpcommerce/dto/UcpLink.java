package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a checkout's {@code links[]} (python-sdk {@code Link}):
 * well-known types include {@code privacy_policy}, {@code terms_of_service},
 * {@code refund_policy}. The base checkout schema REQUIRES the {@code links}
 * array; ThinkShop currently emits an empty one (as the sample server does)
 * — this DTO exists for the day real policy URLs are configured.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpLink
{
	@JsonProperty("type")
	private String type;

	@JsonProperty("url")
	private String url;

	@JsonProperty("title")
	private String title;

	public UcpLink()
	{
		// for Jackson
	}

	public UcpLink(final String type, final String url)
	{
		this.type = type;
		this.url = url;
	}

	public String getType()
	{
		return type;
	}

	public void setType(final String type)
	{
		this.type = type;
	}

	public String getUrl()
	{
		return url;
	}

	public void setUrl(final String url)
	{
		this.url = url;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(final String title)
	{
		this.title = title;
	}
}
