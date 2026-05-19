package com.coremcp.tools;

import java.util.Map;

/**
 * Strategy interface for MCP tool handlers. Each implementation wraps
 * an existing commerce facade and exposes it as an MCP tool.
 */
public interface McpToolHandler
{
	String getName();

	String getDescription();

	Map<String, Object> getInputSchema();

	McpToolResult execute(Map<String, Object> args);

	default Map<String, Object> getDefinition()
	{
		return Map.of(
			"name", getName(),
			"description", getDescription(),
			"inputSchema", getInputSchema()
		);
	}
}
