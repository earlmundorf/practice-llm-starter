package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response payload for the custom {@code com.thinkshop.knowledge} capability's
 * {@code get_knowledge} tool: {@code ucp} envelope + a single {@code entry},
 * or on an unknown uid {@code ucp.status="error"} + an {@code unrecoverable}
 * {@code not_found} message (never a 500 / transport error).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpKnowledgeEntryResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("entry")
	private Map<String, Object> entry;

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

	public Map<String, Object> getEntry()
	{
		return entry;
	}

	public void setEntry(final Map<String, Object> entry)
	{
		this.entry = entry;
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
