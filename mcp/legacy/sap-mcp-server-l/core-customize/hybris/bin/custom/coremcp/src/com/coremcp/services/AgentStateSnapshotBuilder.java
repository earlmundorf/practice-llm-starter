package com.coremcp.services;

/**
 * Builds the per-turn "CURRENT STATE" system message (customer + cart snapshot)
 * that lets the agent answer basics without burning tool round-trips.
 */
public interface AgentStateSnapshotBuilder
{
	String buildStateSnapshotMessage();
}
