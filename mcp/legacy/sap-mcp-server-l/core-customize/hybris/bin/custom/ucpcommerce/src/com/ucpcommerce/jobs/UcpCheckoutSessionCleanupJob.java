package com.ucpcommerce.jobs;

import com.ucpcommerce.model.UcpCheckoutSessionEntryModel;

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
 * Removes expired UCP checkout-session entries from the DB-backed store. The
 * persisted store already evicts lazily on access; this job sweeps checkouts
 * that were simply abandoned and never touched again (the backing hybris cart
 * is left to the platform's own cart cleanup). Scheduled via
 * essentialdata-ucp-session-cleanup.impex.
 */
public class UcpCheckoutSessionCleanupJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(UcpCheckoutSessionCleanupJob.class);

	private static final String QUERY_EXPIRED =
		"SELECT {pk} FROM {UcpCheckoutSessionEntry} WHERE {lastAccessedAt} < ?cutoff";

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final int ttlMinutes = Config.getInt("ucpcommerce.checkout.session.ttl.minutes", 30);
		final Date cutoff = Date.from(Instant.now().minus(Duration.ofMinutes(ttlMinutes)));

		final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY_EXPIRED);
		query.addQueryParameter("cutoff", cutoff);
		final SearchResult<UcpCheckoutSessionEntryModel> result = flexibleSearchService.search(query);
		final List<UcpCheckoutSessionEntryModel> expired = result.getResult();

		if (!expired.isEmpty())
		{
			modelService.removeAll(expired);
		}
		LOG.info("UCP checkout-session cleanup removed {} expired entries (TTL {}m)", expired.size(), ttlMinutes);
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}
}
