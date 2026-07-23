package com.ucpcommerce.tools.impl;

import com.coremcp.services.KnowledgeSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpKnowledgeEntryResponse;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import de.hybris.platform.solrfacetsearch.search.Document;
import de.hybris.platform.util.Config;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Custom capability {@code com.thinkshop.knowledge} (design R7):
 * {@code get_knowledge} — one knowledge-base entry by uid. Unknown uid →
 * payload with {@code ucp.status="error"} and an {@code unrecoverable}
 * {@code not_found} message (never a transport error).
 */
public class GetKnowledgeTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private KnowledgeSearchService knowledgeSearchService;

	@Override
	public String getName()
	{
		return "get_knowledge";
	}

	@Override
	public String getDescription()
	{
		return "Fetch one knowledge-base entry by its uid (custom capability com.thinkshop.knowledge), " +
			"e.g. 'returns-policy'. Returns the full entry: uid, category, title, summary, body and tags. " +
			"Use search_knowledge first when the uid is unknown.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"uid", Map.of("type", "string", "description", "Knowledge entry uid, e.g. 'returns-policy'")
		));
		schema.put("required", List.of("uid"));
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		if (!(args.get("uid") instanceof String) || ((String) args.get("uid")).isBlank())
		{
			throw new IllegalArgumentException("uid is required");
		}
		final String uid = (String) args.get("uid");

		final UcpKnowledgeEntryResponse response = new UcpKnowledgeEntryResponse();
		final Optional<Document> document = knowledgeSearchService.getByUid(uid);
		if (document.isPresent())
		{
			response.setUcp(envelope("success"));
			response.setEntry(knowledgeSearchService.toJson(document.get()));
		}
		else
		{
			response.setUcp(envelope("error"));
			response.setMessages(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "No knowledge entry found for uid: " + uid)));
		}
		return objectMapper.writeValueAsString(response);
	}

	protected UcpEnvelope envelope(final String status)
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus(status);
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setKnowledgeSearchService(final KnowledgeSearchService knowledgeSearchService)
	{
		this.knowledgeSearchService = knowledgeSearchService;
	}
}
