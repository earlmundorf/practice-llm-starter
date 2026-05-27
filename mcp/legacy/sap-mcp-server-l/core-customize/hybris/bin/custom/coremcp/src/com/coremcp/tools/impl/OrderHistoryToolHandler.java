package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.coremcp.services.DeepLinkBuilder;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.core.enums.OrderStatus;

import org.springframework.beans.factory.annotation.Required;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OrderHistoryToolHandler implements McpToolHandler
{
	private OrderFacade orderFacade;
	private DeepLinkBuilder deepLinkBuilder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "order_history";
	}

	@Override
	public String getDescription()
	{
		return "List the customer's past orders with pagination. Use this when the user asks " +
			"'show my orders', 'order history', 'what have I bought', or 'my recent purchases'. " +
			"Returns order codes, dates, statuses, and totals. Use order_get with a specific code " +
			"for full details on a single order. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"statuses", Map.of("type", "array",
				"items", Map.of("type", "string", "enum", List.of("CREATED", "CHECKED_VALID", "PAYMENT_AUTHORIZED", "PAYMENT_CAPTURED", "READY", "COMPLETED", "CANCELLED")),
				"description", "Filter by order statuses. Returns all statuses if omitted."),
			"currentPage", Map.of("type", "integer", "description", "Page number (0-based)", "default", 0),
			"pageSize", Map.of("type", "integer", "description", "Number of orders per page", "default", 20),
			"sort", Map.of("type", "string", "description", "Sort field (e.g., 'byDate', 'byOrderNumber')")
		));
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	@SuppressWarnings("unchecked")
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final int currentPage = args.containsKey("currentPage") ? ((Number) args.get("currentPage")).intValue() : 0;
			final int pageSize = args.containsKey("pageSize") ? ((Number) args.get("pageSize")).intValue() : 20;
			final String sort = (String) args.getOrDefault("sort", "byDate");

			final PageableData pageableData = new PageableData();
			pageableData.setCurrentPage(currentPage);
			pageableData.setPageSize(pageSize);
			pageableData.setSort(sort);

			Set<OrderStatus> statusSet = null;
			if (args.containsKey("statuses") && args.get("statuses") instanceof List)
			{
				final List<String> statusNames = (List<String>) args.get("statuses");
				statusSet = statusNames.stream()
					.map(OrderStatus::valueOf)
					.collect(Collectors.toSet());
			}

			final OrderStatus[] statusArray = (statusSet != null && !statusSet.isEmpty())
				? statusSet.toArray(new OrderStatus[0])
				: null;

			final Object result = orderFacade.getPagedOrderHistoryForStatuses(pageableData, statusArray);
			final ObjectNode tree = objectMapper.valueToTree(result);
			tree.put("url", deepLinkBuilder.orderHistoryUrl());
			if (tree.get("results") instanceof ArrayNode results)
			{
				for (int i = 0; i < results.size(); i++)
				{
					if (results.get(i) instanceof ObjectNode order && order.has("code"))
					{
						order.put("url", deepLinkBuilder.orderUrl(order.get("code").asText()));
					}
				}
			}
			return McpToolResult.success(objectMapper.writeValueAsString(tree));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to get order history: " + e.getMessage());
		}
	}

	@Required
	public void setOrderFacade(final OrderFacade orderFacade)
	{
		this.orderFacade = orderFacade;
	}

	@Required
	public void setDeepLinkBuilder(final DeepLinkBuilder deepLinkBuilder)
	{
		this.deepLinkBuilder = deepLinkBuilder;
	}
}
