package com.coremcp.product.populator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.product.data.ReviewData;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.customerreview.CustomerReviewService;
import de.hybris.platform.customerreview.enums.CustomerReviewApprovalType;
import de.hybris.platform.customerreview.model.CustomerReviewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@UnitTest
public class McpProductReviewsPopulatorTest
{
	@Mock
	private CustomerReviewService customerReviewService;

	private McpProductReviewsPopulator populator;
	private ProductModel product;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
		populator = new McpProductReviewsPopulator();
		populator.setCustomerReviewService(customerReviewService);
		product = mock(ProductModel.class);
	}

	private CustomerReviewModel review(final CustomerReviewApprovalType status, final Boolean blocked,
			final Double rating, final String headline, final String comment, final String alias, final UserModel user)
	{
		final CustomerReviewModel r = mock(CustomerReviewModel.class);
		when(r.getApprovalStatus()).thenReturn(status);
		when(r.getBlocked()).thenReturn(blocked);
		when(r.getRating()).thenReturn(rating);
		when(r.getHeadline()).thenReturn(headline);
		when(r.getComment()).thenReturn(comment);
		when(r.getAlias()).thenReturn(alias);
		when(r.getUser()).thenReturn(user);
		return r;
	}

	private static List<ReviewData> reviewsOf(final ProductData data)
	{
		return new ArrayList<>(data.getReviews());
	}

	@Test
	public void includesApprovedNonBlockedAndCopiesFields() throws Exception
	{
		final CustomerReviewModel r = review(CustomerReviewApprovalType.APPROVED, Boolean.FALSE,
				Double.valueOf(5.0d), "Great laptop", "Fast and light", "John D.", null);
		when(customerReviewService.getReviewsForProduct(product)).thenReturn(Collections.singletonList(r));

		final ProductData data = new ProductData();
		populator.populate(product, data);

		final List<ReviewData> reviews = reviewsOf(data);
		assertEquals(1, reviews.size());
		final ReviewData rd = reviews.get(0);
		assertEquals(Double.valueOf(5.0d), rd.getRating());
		assertEquals("Great laptop", rd.getHeadline());
		assertEquals("Fast and light", rd.getComment());
		assertEquals("John D.", rd.getAlias());
		assertEquals(Integer.valueOf(1), data.getNumberOfReviews());
	}

	@Test
	public void excludesPendingRejectedAndBlocked() throws Exception
	{
		final CustomerReviewModel pending = review(CustomerReviewApprovalType.PENDING, Boolean.FALSE,
				Double.valueOf(4.0d), "h", "c", "a", null);
		final CustomerReviewModel rejected = review(CustomerReviewApprovalType.REJECTED, Boolean.FALSE,
				Double.valueOf(4.0d), "h", "c", "a", null);
		final CustomerReviewModel blocked = review(CustomerReviewApprovalType.APPROVED, Boolean.TRUE,
				Double.valueOf(4.0d), "h", "c", "a", null);
		when(customerReviewService.getReviewsForProduct(product))
				.thenReturn(Arrays.asList(pending, rejected, blocked));

		final ProductData data = new ProductData();
		populator.populate(product, data);

		assertTrue(reviewsOf(data).isEmpty());
		assertEquals(Integer.valueOf(0), data.getNumberOfReviews());
	}

	@Test
	public void aliasFallsBackToUserNameThenUid() throws Exception
	{
		final UserModel named = mock(UserModel.class);
		when(named.getName()).thenReturn("Jane Smith");
		when(named.getUid()).thenReturn("jane.smith@thinkshop.com");
		final UserModel uidOnly = mock(UserModel.class);
		when(uidOnly.getName()).thenReturn(null);
		when(uidOnly.getUid()).thenReturn("bob.wilson@thinkshop.com");

		final CustomerReviewModel noAlias = review(CustomerReviewApprovalType.APPROVED, Boolean.FALSE,
				Double.valueOf(4.0d), "h", "c", null, named);
		final CustomerReviewModel blankAlias = review(CustomerReviewApprovalType.APPROVED, Boolean.FALSE,
				Double.valueOf(3.0d), "h", "c", "   ", uidOnly);
		when(customerReviewService.getReviewsForProduct(product))
				.thenReturn(Arrays.asList(noAlias, blankAlias));

		final ProductData data = new ProductData();
		populator.populate(product, data);

		final List<ReviewData> reviews = reviewsOf(data);
		assertEquals("Jane Smith", reviews.get(0).getAlias());
		assertEquals("bob.wilson@thinkshop.com", reviews.get(1).getAlias());
	}

	@Test
	public void emptyListWhenNoReviews() throws Exception
	{
		when(customerReviewService.getReviewsForProduct(product)).thenReturn(Collections.emptyList());

		final ProductData data = new ProductData();
		populator.populate(product, data);

		assertNotNull(data.getReviews());
		assertTrue(data.getReviews().isEmpty());
		assertEquals(Integer.valueOf(0), data.getNumberOfReviews());
	}

	@Test
	public void emptyListWhenServiceReturnsNull() throws Exception
	{
		when(customerReviewService.getReviewsForProduct(product)).thenReturn(null);

		final ProductData data = new ProductData();
		populator.populate(product, data);

		assertNotNull(data.getReviews());
		assertTrue(data.getReviews().isEmpty());
	}
}
