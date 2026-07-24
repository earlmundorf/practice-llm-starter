package com.ucpcommerce.jobs;

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
 *
 * Sweep rules (review finding — the age-only sweep must not eat completion
 * state):
 * <ul>
 *   <li>{@code incomplete} / {@code ready_for_complete} / {@code canceled}:
 *       swept after the session TTL;</li>
 *   <li>{@code completed}: kept for the (longer) completed-retention window —
 *       the stored completion response is the idempotent-replay source;</li>
 *   <li>{@code complete_in_progress}: NEVER swept here — a stuck completion
 *       is an operational signal (an order may exist), not garbage.</li>
 * </ul>
 * Idempotency records ({@code UcpIdempotencyRecord}) are swept after the
 * completed-retention window too.
 */
public class UcpCheckoutSessionCleanupJob extends AbstractJobPerformable<CronJobModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(UcpCheckoutSessionCleanupJob.class);

	private static final String QUERY_EXPIRED =
		"SELECT {pk} FROM {UcpCheckoutSessionEntry} WHERE {lastAccessedAt} < ?cutoff "
			+ "AND {status} NOT IN "
			+ "({{SELECT {es.pk} FROM {UcpCheckoutStatus AS es} WHERE {es.code} IN ('completed', 'complete_in_progress')}})";

	private static final String QUERY_EXPIRED_COMPLETED =
		"SELECT {pk} FROM {UcpCheckoutSessionEntry} WHERE {lastAccessedAt} < ?cutoff "
			+ "AND {status} IN "
			+ "({{SELECT {es.pk} FROM {UcpCheckoutStatus AS es} WHERE {es.code} = 'completed'}})";

	private static final String QUERY_EXPIRED_IDEMPOTENCY =
		"SELECT {pk} FROM {UcpIdempotencyRecord} WHERE {creationtime} < ?cutoff";

	@Override
	public PerformResult perform(final CronJobModel cronJob)
	{
		final int ttlMinutes = Config.getInt("ucpcommerce.checkout.session.ttl.minutes", 30);
		final int completedRetentionMinutes =
			Config.getInt("ucpcommerce.checkout.completed.retention.minutes", 10080);

		final int abandoned = sweep(QUERY_EXPIRED,
			Date.from(Instant.now().minus(Duration.ofMinutes(ttlMinutes))));
		final Date completedCutoff =
			Date.from(Instant.now().minus(Duration.ofMinutes(completedRetentionMinutes)));
		final int completed = sweep(QUERY_EXPIRED_COMPLETED, completedCutoff);
		final int idempotency = sweep(QUERY_EXPIRED_IDEMPOTENCY, completedCutoff);

		LOG.info("UCP checkout-session cleanup: {} abandoned (TTL {}m), {} completed (retention {}m), "
			+ "{} idempotency records", abandoned, ttlMinutes, completed, completedRetentionMinutes, idempotency);
		return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
	}

	private int sweep(final String queryString, final Date cutoff)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(queryString);
		query.addQueryParameter("cutoff", cutoff);
		final SearchResult<Object> result = flexibleSearchService.search(query);
		final List<Object> expired = result.getResult();
		if (!expired.isEmpty())
		{
			modelService.removeAll(expired);
		}
		return expired.size();
	}
}
