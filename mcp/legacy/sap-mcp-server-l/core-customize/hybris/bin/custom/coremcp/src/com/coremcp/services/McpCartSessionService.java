package com.coremcp.services;

/**
 * Manages cart session loading and persistence for MCP and Agent requests.
 * Encapsulates the CartLoaderStrategy/CartService interaction that both
 * controllers need, keeping cart-management logic out of the controller layer.
 */
public interface McpCartSessionService
{
	/**
	 * Loads a cart into the current session by code.
	 * If the cart code is null or the cart cannot be loaded, no cart is set.
	 *
	 * @param cartCode the cart code to load, or null to skip
	 */
	void loadCart(String cartCode);

	/**
	 * Loads a cart into the current session, falling back to "current" (most recently
	 * modified cart) if the given code is null or empty.
	 *
	 * @param cartCode explicit cart code from the client, or null/empty to use "current"
	 */
	void loadCartOrCurrent(String cartCode);

	/**
	 * Returns the current session cart code, or null if no cart is active.
	 */
	String getSessionCartCode();
}
