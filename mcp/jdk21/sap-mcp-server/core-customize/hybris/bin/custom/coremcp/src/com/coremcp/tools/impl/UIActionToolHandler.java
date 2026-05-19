package com.coremcp.tools.impl;

import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that signals a UI navigation or action to the customer's browser.
 * The agent calls this when the customer wants to proceed to checkout,
 * browse products, view orders, etc. The action is passed through to
 * the frontend in the response.
 */
public class UIActionToolHandler implements McpToolHandler
{
	@Override
	public String getName()
	{
		return "ui_action";
	}

	@Override
	public String getDescription()
	{
		return "Navigate the customer's browser to a specific page. Currently supports 'checkout' " +
			"to open the checkout flow in the UI. Use this when the user says 'let me check out', " +
			"'I'm ready to pay', or 'proceed to checkout'. This triggers a frontend navigation — " +
			"it does not perform the checkout API steps (use checkout_set_* tools for that).";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"action", Map.of(
				"type", "string",
				"description", "The UI action to trigger",
				"enum", List.of("checkout")
			)
		));
		schema.put("required", List.of("action"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		final String action = (String) args.get("action");
		return McpToolResult.success("{\"action\":\"" + action + "\",\"status\":\"triggered\"}");
	}
}
