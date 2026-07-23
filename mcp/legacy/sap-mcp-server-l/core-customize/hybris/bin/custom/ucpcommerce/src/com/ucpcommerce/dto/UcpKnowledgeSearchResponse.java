package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response payload for the custom {@code com.thinkshop.knowledge} capability's
 * {@code search_knowledge} tool: {@code ucp} envelope + knowledge entries as
 * indexed in Solr (uid, category, title, summary, body, tags, …) via
 * coremcp's {@code KnowledgeSearchService}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpKnowledgeSearchResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("results")
	private List<Map<String, Object>> results;

	@JsonProperty("count")
	private Integer count;

	@JsonProperty("messages")
	private List<UcpMessage> messages;

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public List<Map<String, Object>> getResults()
	{
		return results;
	}

	public void setResults(final List<Map<String, Object>> results)
	{
		this.results = results;
	}

	public Integer getCount()
	{
		return count;
	}

	public void setCount(final Integer count)
	{
		this.count = count;
	}

	public List<UcpMessage> getMessages()
	{
		return messages;
	}

	public void setMessages(final List<UcpMessage> messages)
	{
		this.messages = messages;
	}
}
