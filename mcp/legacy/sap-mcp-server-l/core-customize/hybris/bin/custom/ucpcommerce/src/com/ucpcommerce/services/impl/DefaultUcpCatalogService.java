package com.ucpcommerce.services.impl;

import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpCatalogResponse;
import com.ucpcommerce.dto.UcpDescription;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpMessage;
import com.ucpcommerce.dto.UcpPagination;
import com.ucpcommerce.dto.UcpPrice;
import com.ucpcommerce.dto.UcpPriceRange;
import com.ucpcommerce.dto.UcpProduct;
import com.ucpcommerce.dto.UcpProductResponse;
import com.ucpcommerce.dto.UcpVariant;
import com.ucpcommerce.services.UcpCatalogService;

import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.search.ProductSearchFacade;
import de.hybris.platform.commercefacades.search.data.SearchQueryData;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.facetdata.ProductSearchPageData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.PaginationData;
import de.hybris.platform.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog capability over the standard platform facades — the same calls the
 * proprietary {@code product_search}/{@code product_get} tools make, but
 * marshalled to UCP product payloads with integer minor-unit money via the
 * centralized {@link UcpMoneyConverter}.
 */
public class DefaultUcpCatalogService implements UcpCatalogService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpCatalogService.class);

	private static final String SORT_RELEVANCE = "relevance";
	private static final int MIN_PAGE_SIZE = 1;
	private static final int MAX_PAGE_SIZE = 50;

	private static final List<ProductOption> PRODUCT_OPTIONS =
		List.of(ProductOption.BASIC, ProductOption.PRICE, ProductOption.STOCK, ProductOption.DESCRIPTION);

	private ProductSearchFacade<ProductData> productSearchFacade;
	private ProductFacade productFacade;
	private UcpMoneyConverter ucpMoneyConverter;

	@Override
	public UcpCatalogResponse search(final String query, final int page, final int pageSize)
	{
		// Hybris search-state encodes the sort as "<query>:<sort>" (the
		// ProductSearchToolHandler idiom); empty query browses everything.
		final SearchStateData searchState = new SearchStateData();
		final SearchQueryData queryData = new SearchQueryData();
		queryData.setValue((query == null ? "" : query) + ":" + SORT_RELEVANCE);
		searchState.setQuery(queryData);

		final PageableData pageableData = new PageableData();
		pageableData.setCurrentPage(Math.max(0, page));
		pageableData.setPageSize(Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, pageSize)));

		final ProductSearchPageData<SearchStateData, ProductData> searchPage =
			productSearchFacade.textSearch(searchState, pageableData);

		final UcpCatalogResponse response = new UcpCatalogResponse();
		response.setUcp(successEnvelope());
		if (searchPage != null && searchPage.getResults() != null)
		{
			final List<UcpProduct> products = new ArrayList<>();
			for (final ProductData productData : searchPage.getResults())
			{
				products.add(toUcpProduct(productData));
			}
			response.setProducts(products);
		}
		if (searchPage != null && searchPage.getPagination() != null)
		{
			response.setPagination(toUcpPagination(searchPage.getPagination()));
		}
		return response;
	}

	@Override
	public UcpCatalogResponse lookup(final List<String> ids)
	{
		final UcpCatalogResponse response = new UcpCatalogResponse();
		response.setUcp(successEnvelope());
		final List<UcpProduct> products = new ArrayList<>();
		final List<UcpMessage> messages = new ArrayList<>();
		for (final String id : ids)
		{
			try
			{
				final UcpProduct product = toUcpProduct(productFacade.getProductForCodeAndOptions(id, PRODUCT_OPTIONS));
				// Lookup responses require the input correlation on each
				// variant (catalog_lookup.json#lookup_variant): the requested
				// id IS the variant id on this variantless catalog — exact.
				for (final UcpVariant variant : product.getVariants())
				{
					variant.setInputs(List.of(new UcpVariant.InputCorrelation(id,
						UcpVariant.InputCorrelation.MATCH_EXACT)));
				}
				products.add(product);
			}
			catch (final Exception e)
			{
				// Per-id miss is recoverable — the rest of the batch still resolves.
				LOG.debug("lookup_catalog miss for id {}: {}", id, e.getMessage());
				messages.add(new UcpMessage("error", "not_found", UcpMessage.SEVERITY_RECOVERABLE,
					"Unknown product id: " + id));
			}
		}
		response.setProducts(products);
		if (!messages.isEmpty())
		{
			response.setMessages(messages);
		}
		return response;
	}

	@Override
	public UcpProductResponse getProduct(final String id)
	{
		final UcpProductResponse response = new UcpProductResponse();
		try
		{
			final ProductData productData = productFacade.getProductForCodeAndOptions(id, PRODUCT_OPTIONS);
			response.setUcp(successEnvelope());
			response.setProduct(toUcpProduct(productData));
		}
		catch (final Exception e)
		{
			// Business error, not a transport error: HTTP 200 / non-isError
			// payload with ucp.status="error" + messages[] (runbook §2.2).
			LOG.debug("get_product miss for id {}: {}", id, e.getMessage());
			response.setUcp(errorEnvelope());
			response.setMessages(List.of(new UcpMessage("error", "not_found",
				UcpMessage.SEVERITY_UNRECOVERABLE, "Product not found: " + id)));
		}
		return response;
	}

	/**
	 * Official {@code product.json} shape: description as a formats object,
	 * required {@code price_range}, and a single required variant mirroring
	 * the (variantless) ThinkShop product — its id is the same id checkout
	 * accepts. The flat {@code price}/{@code currency}/{@code availability}
	 * convenience fields ride along as schema-tolerated extras.
	 */
	protected UcpProduct toUcpProduct(final ProductData productData)
	{
		final UcpProduct product = new UcpProduct();
		product.setId(productData.getCode());
		product.setTitle(productData.getName());
		final String descriptionText = productData.getSummary() != null
			? productData.getSummary() : productData.getDescription();
		final UcpDescription description = new UcpDescription(descriptionText != null ? descriptionText : "");
		product.setDescription(description);

		UcpPrice price = null;
		if (productData.getPrice() != null)
		{
			// The one place catalog money crosses the major→minor boundary.
			product.setPrice(ucpMoneyConverter.toMinorUnits(
				productData.getPrice().getValue(), productData.getPrice().getCurrencyIso()));
			product.setCurrency(productData.getPrice().getCurrencyIso());
			price = new UcpPrice(product.getPrice(), product.getCurrency());
			product.setPriceRange(new UcpPriceRange(price, price));
		}

		final UcpVariant variant = new UcpVariant();
		variant.setId(productData.getCode());
		variant.setSku(productData.getCode());
		variant.setTitle(productData.getName());
		variant.setDescription(description);
		variant.setPrice(price);
		if (productData.getStock() != null && productData.getStock().getStockLevelStatus() != null)
		{
			final String availability = mapAvailability(productData.getStock().getStockLevelStatus().getCode());
			product.setAvailability(availability);
			variant.setAvailability(new UcpVariant.Availability(
				!"out_of_stock".equals(availability), availability));
		}
		product.setVariants(List.of(variant));
		return product;
	}

	protected String mapAvailability(final String stockLevelStatusCode)
	{
		switch (stockLevelStatusCode)
		{
			case "inStock":
				return "in_stock";
			case "lowStock":
				return "low_stock";
			case "outOfStock":
				return "out_of_stock";
			default:
				return stockLevelStatusCode;
		}
	}

	protected UcpPagination toUcpPagination(final PaginationData paginationData)
	{
		final UcpPagination pagination = new UcpPagination();
		pagination.setCurrentPage(paginationData.getCurrentPage());
		pagination.setPageSize(paginationData.getPageSize());
		pagination.setTotalResults(paginationData.getTotalNumberOfResults());
		pagination.setTotalPages(paginationData.getNumberOfPages());
		// Official response pagination (pagination.json): has_next_page is
		// required; the cursor (required when true) is the next page number.
		final boolean hasNext = paginationData.getCurrentPage() + 1 < paginationData.getNumberOfPages();
		pagination.setHasNextPage(hasNext);
		if (hasNext)
		{
			pagination.setCursor(String.valueOf(paginationData.getCurrentPage() + 1));
		}
		pagination.setTotalCount(paginationData.getTotalNumberOfResults());
		return pagination;
	}

	protected UcpEnvelope successEnvelope()
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus("success");
		return envelope;
	}

	protected UcpEnvelope errorEnvelope()
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus("error");
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setProductSearchFacade(final ProductSearchFacade<ProductData> productSearchFacade)
	{
		this.productSearchFacade = productSearchFacade;
	}

	@Required
	public void setProductFacade(final ProductFacade productFacade)
	{
		this.productFacade = productFacade;
	}

	@Required
	public void setUcpMoneyConverter(final UcpMoneyConverter ucpMoneyConverter)
	{
		this.ucpMoneyConverter = ucpMoneyConverter;
	}
}
