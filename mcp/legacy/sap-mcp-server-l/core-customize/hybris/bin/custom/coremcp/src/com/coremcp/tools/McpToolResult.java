package com.coremcp.tools;

/**
 * Result wrapper for MCP tool executions. Contains JSON content
 * and an error flag per the MCP spec.
 */
public class McpToolResult
{
	private final String content;
	private final boolean isError;

	private McpToolResult(final String content, final boolean isError)
	{
		this.content = content;
		this.isError = isError;
	}

	public static McpToolResult success(final String content)
	{
		return new McpToolResult(content, false);
	}

	public static McpToolResult error(final String message)
	{
		return new McpToolResult(message, true);
	}

	public String getContent()
	{
		return content;
	}

	public boolean isError()
	{
		return isError;
	}
}
