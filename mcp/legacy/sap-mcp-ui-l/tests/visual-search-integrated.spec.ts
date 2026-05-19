import { test, expect } from '@playwright/test';

const TEST_USER = 'john.doe@thinkshop.com';
const TEST_PASS = '1234';
const TEST_IMAGE = '/tmp/test-product.jpg';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/');
  const emailInput = page.locator('input[placeholder*="email" i]');
  if (await emailInput.isVisible({ timeout: 3000 }).catch(() => false)) {
    await emailInput.fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('.fixed button[type="submit"], .fixed button:has-text("Sign In"), .fixed button:has-text("Log In")').first().click();
    await expect(page.locator('.fixed.inset-0')).not.toBeVisible({ timeout: 10000 });
  }
}

test.describe('Visual Search — Integrated in Products Search Bar', () => {

  test('camera button is visible in the search bar', async ({ page }) => {
    await login(page);
    await page.goto('/products');

    // Camera button should be inside the search bar area
    await expect(page.locator('button[aria-label="Search by image"]')).toBeVisible();
  });

  test('clicking camera button and uploading image triggers visual search', async ({ page }) => {
    await login(page);
    await page.goto('/products');

    // Intercept backend call
    let backendHit = false;
    page.on('response', (res) => {
      if (res.url().includes('/agent/visual-search')) backendHit = true;
    });

    // Upload via the hidden file input associated with the camera button
    const fileInput = page.locator('input[type="file"][accept="image/*"]');
    await fileInput.setInputFiles(TEST_IMAGE);

    // Wait for results
    await expect(page.getByRole('heading', { name: 'Visual Search Results' })).toBeVisible({ timeout: 30000 });

    // Backend was called
    expect(backendHit).toBe(true);

    // Normal product grid should be hidden
    await expect(page.locator('text=Showing').locator('text=of').first()).not.toBeVisible();
  });

  test('back to text search dismisses visual results', async ({ page }) => {
    await login(page);
    await page.goto('/products');

    // Upload image
    const fileInput = page.locator('input[type="file"][accept="image/*"]');
    await fileInput.setInputFiles(TEST_IMAGE);

    // Wait for visual results
    await expect(page.getByRole('heading', { name: 'Visual Search Results' })).toBeVisible({ timeout: 30000 });

    // Click "Clear" to dismiss visual results
    await page.locator('button:has-text("Clear")').click();

    // Visual results should be gone
    await expect(page.locator('text=Visual Search Results')).not.toBeVisible();

    // Normal product grid should be back
    await expect(page.locator('text=Our Products')).toBeVisible();
  });

  test('visual search results link to product detail pages', async ({ page }) => {
    await login(page);
    await page.goto('/products');

    // Upload image
    const fileInput = page.locator('input[type="file"][accept="image/*"]');
    await fileInput.setInputFiles(TEST_IMAGE);

    // Wait for results
    await expect(page.getByRole('heading', { name: 'Visual Search Results' })).toBeVisible({ timeout: 30000 });

    // If there are product cards, they should link to product detail
    const matchCards = page.locator('a[href^="/products/"]');
    const count = await matchCards.count();
    if (count > 0) {
      const href = await matchCards.first().getAttribute('href');
      expect(href).toMatch(/^\/products\/[A-Z0-9_]+/i);
    }
  });
});
