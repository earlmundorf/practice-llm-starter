package com.coremcp.services;

import java.util.Map;

/**
 * Service for visual product search.
 * Sends an image to OpenAI GPT-4o Vision for product identification,
 * then searches the SAP Commerce catalog for matching products.
 */
public interface VisualSearchService
{
	/**
	 * Analyze a product image and search the catalog for matches.
	 *
	 * @param base64Image base64-encoded image data
	 * @param mimeType    image MIME type (e.g., "image/jpeg")
	 * @return response map containing "visionAnalysis" (String) and "products" (List of match maps)
	 */
	Map<String, Object> searchByImage(String base64Image, String mimeType);
}
