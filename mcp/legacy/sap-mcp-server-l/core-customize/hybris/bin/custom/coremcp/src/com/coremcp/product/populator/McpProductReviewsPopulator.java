package com.coremcp.product.populator;

import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.product.data.ReviewData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.customerreview.CustomerReviewService;
import de.hybris.platform.customerreview.enums.CustomerReviewApprovalType;
import de.hybris.platform.customerreview.model.CustomerReviewModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * Populates {@link ProductData#getReviews()} for
 * {@link de.hybris.platform.commercefacades.product.ProductOption#REVIEW}, exposing only the
 * reviews a shopper should see: approved and not blocked. Each {@link ReviewData} carries the
 * rating, headline, comment and a reviewer display name that resolves to the review alias,
 * falling back to the author's name and then uid.
 *
 * <p>Registered in {@code coremcp-spring.xml} under the {@code productReviewsPopulator} alias so
 * it replaces the OOTB commercefacades populator in the product-option populator chain used by
 * the {@code product_get} MCP tool. Reviews are read from the DB via {@link CustomerReviewService}
 * (they are not indexed in Solr in this project).
 */
public class McpProductReviewsPopulator implements Populator<ProductModel, ProductData>
{
	private CustomerReviewService customerReviewService;

	@Override
	public void populate(final ProductModel source, final ProductData target) throws ConversionException
	{
		final List<CustomerReviewModel> reviews = getCustomerReviewService().getReviewsForProduct(source);
		final List<ReviewData> visible = new ArrayList<>();
		if (reviews != null)
		{
			for (final CustomerReviewModel review : reviews)
			{
				if (isVisible(review))
				{
					visible.add(toReviewData(review));
				}
			}
		}
		// Always a list — never null — so a product with no visible reviews serialises as [].
		target.setReviews(visible);
		target.setNumberOfReviews(Integer.valueOf(visible.size()));
	}

	/**
	 * A review is visible to shoppers when it is approved and not blocked.
	 */
	protected boolean isVisible(final CustomerReviewModel review)
	{
		return CustomerReviewApprovalType.APPROVED.equals(review.getApprovalStatus())
				&& !Boolean.TRUE.equals(review.getBlocked());
	}

	protected ReviewData toReviewData(final CustomerReviewModel review)
	{
		final ReviewData data = new ReviewData();
		data.setRating(review.getRating());
		data.setHeadline(review.getHeadline());
		data.setComment(review.getComment());
		data.setAlias(resolveDisplayName(review));
		return data;
	}

	/**
	 * Reviewer display name: review alias, else the author's name, else the author's uid.
	 */
	protected String resolveDisplayName(final CustomerReviewModel review)
	{
		if (isNotBlank(review.getAlias()))
		{
			return review.getAlias();
		}
		final UserModel user = review.getUser();
		if (user != null)
		{
			return isNotBlank(user.getName()) ? user.getName() : user.getUid();
		}
		return null;
	}

	private static boolean isNotBlank(final String value)
	{
		return value != null && !value.trim().isEmpty();
	}

	protected CustomerReviewService getCustomerReviewService()
	{
		return customerReviewService;
	}

	@Required
	public void setCustomerReviewService(final CustomerReviewService customerReviewService)
	{
		this.customerReviewService = customerReviewService;
	}
}
