package com.coremcp.services;

import java.util.Map;

/**
 * Inspects a tool call's args + result and records entity references (product,
 * order, orderHistory) on the turn context so the chat UI can render clickable chips.
 */
public interface EntityRefCollector
{
	void collect(String toolName, Map<String, Object> toolArgs, String toolResultJson, AgentTurnContext context);
}
