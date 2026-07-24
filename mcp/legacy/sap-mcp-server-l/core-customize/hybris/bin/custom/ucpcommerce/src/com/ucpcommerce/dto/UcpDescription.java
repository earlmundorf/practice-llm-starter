package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The official {@code description.json} content block — description text in
 * one or more formats ({@code plain} / {@code html} / {@code markdown}); at
 * least one must be provided. ThinkShop product data is plain text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpDescription
{
	@JsonProperty("plain")
	private String plain;

	@JsonProperty("html")
	private String html;

	@JsonProperty("markdown")
	private String markdown;

	public UcpDescription()
	{
		// for Jackson
	}

	public UcpDescription(final String plain)
	{
		this.plain = plain;
	}

	public String getPlain()
	{
		return plain;
	}

	public void setPlain(final String plain)
	{
		this.plain = plain;
	}

	public String getHtml()
	{
		return html;
	}

	public void setHtml(final String html)
	{
		this.html = html;
	}

	public String getMarkdown()
	{
		return markdown;
	}

	public void setMarkdown(final String markdown)
	{
		this.markdown = markdown;
	}
}
