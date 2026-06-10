package com.coremcp.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable state accumulated over one agent turn (one user message, possibly many
 * LLM/tool iterations): duplicate-invocation tracking, entity references for UI
 * chips, and a captured ui_action. Created per request — never shared.
 */
public class AgentTurnContext
{
	private final Set<String> seenInvocations = new HashSet<>();
	private final List<Map<String, String>> entityRefs = new ArrayList<>();
	private final Set<String> entityRefKeys = new HashSet<>();
	private String uiAction;

	/** @return true if this invocation key was already seen this turn (duplicate). */
	public boolean markInvocation(final String invocationKey)
	{
		return !seenInvocations.add(invocationKey);
	}

	/** Append an entity reference for the UI, deduplicated by (type, code). */
	public void addEntityRef(final String type, final String code)
	{
		final String key = type + "|" + (code == null ? "" : code);
		if (!entityRefKeys.add(key))
		{
			return;
		}
		final Map<String, String> ref = new LinkedHashMap<>();
		ref.put("type", type);
		if (code != null)
		{
			ref.put("code", code);
		}
		entityRefs.add(ref);
	}

	public List<Map<String, String>> getEntityRefs()
	{
		return entityRefs;
	}

	public String getUiAction()
	{
		return uiAction;
	}

	public void setUiAction(final String uiAction)
	{
		this.uiAction = uiAction;
	}
}
