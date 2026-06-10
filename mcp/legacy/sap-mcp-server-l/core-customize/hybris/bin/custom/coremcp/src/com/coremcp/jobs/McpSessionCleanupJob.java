package com.coremcp.jobs;

import com.coremcp.model.McpSessionEntryModel;

import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Removes expired MCP sessions from the DB-backed store. The persisted store
 * already evicts lazily on access; this job sweeps sessions that were simply
 * abandoned and never touched again. Scheduled via essentialdata-mcp-session-cleanup.impex.
 */
public class McpSessionCleanupJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(McpSessionCleanupJob.class);

	private static final String QUERY_EXPIRED =
		"SELECT {pk} FROM {McpSessionEntry} WHERE {lastAccessedAt} < ?cutoff";

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final int ttlMinutes = Config.getInt("coremcp.session.ttl.minutes", 30);
		final Date cutoff = Date.from(Instant.now().minus(Duration.ofMinutes(ttlMinutes)));

		final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY_EXPIRED);
		query.addQueryParameter("cutoff", cutoff);
		final SearchResult<McpSessionEntryModel> result = flexibleSearchService.search(query);
		final List<McpSessionEntryModel> expired = result.getResult();

		if (!expired.isEmpty())
		{
			modelService.removeAll(expired);
		}
		LOG.info("MCP session cleanup removed {} expired sessions (TTL {}m)", expired.size(), ttlMinutes);
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}
}
