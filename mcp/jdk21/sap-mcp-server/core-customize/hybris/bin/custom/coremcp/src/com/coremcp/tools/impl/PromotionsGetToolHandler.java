package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.PromotionQueryService;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool that returns active promotions and coupons.
 * Delegates to {@link PromotionService} for all data access.
 */
public class PromotionsGetToolHandler implements McpToolHandler
{
	private PromotionQueryService promotionQueryService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "promotions_get";
	}

	@Override
	public String getDescription()
	{
		return "List active promotions and coupons. Use this when the user asks 'what promotions are running', " +
			"'any deals', 'active discounts', 'current offers', 'do you have free shipping', or 'coupon codes'. " +
			"Returns promotion rules with their codes, names, status, start/end dates, and any active coupons. " +
			"Does not require customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"activeOnly", Map.of("type", "boolean",
				"description", "If true, only return promotions that are currently active (published, within date range). Defaults to true.",
				"default", true),
			"includeCoupons", Map.of("type", "boolean",
				"description", "If true, also return active coupons. Defaults to true.",
				"default", true)
		));
		schema.put("required", Collections.emptyList());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final boolean activeOnly = args.containsKey("activeOnly") ? Boolean.TRUE.equals(args.get("activeOnly")) : true;
			final boolean includeCoupons = args.containsKey("includeCoupons") ? Boolean.TRUE.equals(args.get("includeCoupons")) : true;

			final Map<String, Object> result = new LinkedHashMap<>();
			result.put("promotions", promotionQueryService.getPromotions(activeOnly));

			if (includeCoupons)
			{
				result.put("coupons", promotionQueryService.getCoupons(activeOnly));
			}

			return McpToolResult.success(objectMapper.writeValueAsString(result));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to get promotions: " + e.getMessage());
		}
	}

	public void setPromotionQueryService(final PromotionQueryService promotionQueryService)
	{
		this.promotionQueryService = promotionQueryService;
	}
}
