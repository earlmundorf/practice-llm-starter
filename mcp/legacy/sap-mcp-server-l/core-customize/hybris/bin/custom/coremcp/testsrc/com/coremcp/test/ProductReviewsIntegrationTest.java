package com.coremcp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.product.data.ReviewData;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.site.BaseSiteService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;


/**
 * End-to-end check that {@code product_get}'s {@link ProductOption#REVIEW} path returns only
 * approved, non-blocked reviews (with a resolved reviewer name) and an empty list for products
 * without visible reviews. Exercises the same {@link ProductFacade} the MCP tool injects, so it
 * covers the {@code mcpProductReviewsPopulator} alias-swap wiring against a real DB.
 */
@IntegrationTest
public class ProductReviewsIntegrationTest extends ServicelayerTransactionalTest
{
	private static final String SITE_UID = "electronics";
	private static final String CATALOG_ID = "electronicsProductCatalog";
	private static final String CATALOG_VERSION_NAME = "Online";

	@Resource
	private ProductFacade productFacade;

	@Resource
	private BaseSiteService baseSiteService;

	@Resource
	private CatalogVersionService catalogVersionService;

	@Resource
	private CommonI18NService commonI18NService;

	@Before
	public void setUp() throws Exception
	{
		importCsv("/coremcp/test/testdata-thinkshop.impex", "utf-8");
		importCsv("/coremcp/test/testdata-reviews.impex", "utf-8");

		baseSiteService.setCurrentBaseSite(SITE_UID, true);
		final CatalogVersionModel online = catalogVersionService.getCatalogVersion(CATALOG_ID, CATALOG_VERSION_NAME);
		catalogVersionService.setSessionCatalogVersions(Collections.singletonList(online));

		commonI18NService.setCurrentCurrency(commonI18NService.getCurrency("USD"));
		commonI18NService.setCurrentLanguage(commonI18NService.getLanguage("en"));
	}

	private List<ReviewData> reviewsFor(final String code)
	{
		final ProductData data = productFacade.getProductForCodeAndOptions(code, EnumSet.of(ProductOption.REVIEW));
		assertNotNull("ProductData for " + code, data);
		final Collection<ReviewData> reviews = data.getReviews();
		assertNotNull("reviews collection is never null for " + code, reviews);
		return new ArrayList<>(reviews);
	}

	@Test
	public void returnsOnlyApprovedNonBlockedReviewsWithFields()
	{
		final List<ReviewData> reviews = reviewsFor("LAPTOP_PRO_15");

		// John (approved) + Jane (approved); Bob is blocked and must be excluded.
		assertEquals("only approved, non-blocked reviews are visible", 2, reviews.size());
		for (final ReviewData r : reviews)
		{
			assertNotNull("rating present", r.getRating());
			assertNotNull("headline present", r.getHeadline());
			assertNotNull("comment present", r.getComment());
			assertNotNull("reviewer display name present", r.getAlias());
			assertFalse("blocked review leaked", "Hidden".equals(r.getHeadline()));
		}
	}

	@Test
	public void resolvesReviewerNameFromAliasThenUserName()
	{
		final List<ReviewData> reviews = reviewsFor("LAPTOP_PRO_15");

		boolean sawExplicitAlias = false;
		boolean sawNameFallback = false;
		for (final ReviewData r : reviews)
		{
			if ("John D.".equals(r.getAlias()))
			{
				sawExplicitAlias = true;
			}
			// Jane's review has a blank alias, so it must fall back to the customer name.
			if ("Jane Smith".equals(r.getAlias()))
			{
				sawNameFallback = true;
			}
		}
		assertTrue("explicit alias used when present", sawExplicitAlias);
		assertTrue("falls back to user name when alias is blank", sawNameFallback);
	}

	@Test
	public void returnsEmptyListWhenAllReviewsFilteredOut()
	{
		// SMARTPHONE_X has one review but it is pending -> filtered out.
		assertTrue("pending-only product yields empty list", reviewsFor("SMARTPHONE_X").isEmpty());
	}

	@Test
	public void returnsEmptyListForProductWithNoReviews()
	{
		// HD_WEBCAM has no reviews -> empty list, not an error.
		assertTrue("product with no reviews yields empty list", reviewsFor("HD_WEBCAM").isEmpty());
	}
}
