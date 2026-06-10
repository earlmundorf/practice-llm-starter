package com.coremcp.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.AgentTurnContext;
import com.coremcp.services.EntityRefCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Default {@link EntityRefCollector}. Caps refs per tool call so a wide search
 * doesn't explode the chat with dozens of chips.
 */
public class DefaultEntityRefCollector implements EntityRefCollector
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultEntityRefCollector.class);

	private static final int MAX_PRODUCT_REFS_PER_CALL = 5;
	private static final int MAX_ORDER_REFS_PER_CALL = 5;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void collect(final String toolName, final Map<String, Object> toolArgs, final String toolResultJson,
		final AgentTurnContext context)
	{
		try
		{
			switch (toolName)
			{
				case "product_get":
				{
					final Object code = toolArgs.get("code");
					if (code instanceof String s)
					{
						context.addEntityRef("product", s);
					}
					break;
				}
				case "order_get":
				{
					final Object code = toolArgs.get("code");
					if (code instanceof String s)
					{
						context.addEntityRef("order", s);
					}
					break;
				}
				case "order_history":
				{
					context.addEntityRef("orderHistory", null);
					final JsonNode root = objectMapper.readTree(toolResultJson);
					final JsonNode results = root.get("orders") != null ? root.get("orders") : root.get("results");
					if (results != null && results.isArray())
					{
						int n = 0;
						for (final JsonNode order : results)
						{
							if (n++ >= MAX_ORDER_REFS_PER_CALL)
							{
								break;
							}
							if (order.has("code"))
							{
								context.addEntityRef("order", order.get("code").asText());
							}
						}
					}
					break;
				}
				case "product_search":
				{
					final JsonNode root = objectMapper.readTree(toolResultJson);
					final JsonNode results = root.get("results");
					if (results != null && results.isArray())
					{
						int n = 0;
						for (final JsonNode product : results)
						{
							if (n++ >= MAX_PRODUCT_REFS_PER_CALL)
							{
								break;
							}
							if (product.has("code"))
							{
								context.addEntityRef("product", product.get("code").asText());
							}
						}
					}
					break;
				}
				default:
					// Other tools (cart, checkout, customer, ui_action) don't produce chips.
			}
		}
		catch (final Exception e)
		{
			LOG.debug("collectEntityRefs failed for {}: {}", toolName, e.getMessage());
		}
	}
}
