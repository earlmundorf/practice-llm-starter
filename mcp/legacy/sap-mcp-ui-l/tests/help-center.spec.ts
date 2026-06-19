import { test, expect } from '@playwright/test';

test.describe('/help — index', () => {
  test('renders search input and category chips', async ({ page }) => {
    await page.goto('/help');
    await expect(page.getByRole('heading', { name: 'Help Center' })).toBeVisible();
    await expect(page.getByLabel('Search help')).toBeVisible();
    await expect(page.locator('[aria-label="Filter by category: policy"]')).toBeVisible();
  });

  test('search input updates URL with ?q after debounce', async ({ page }) => {
    await page.goto('/help');
    await page.getByLabel('Search help').fill('return');
    await page.waitForFunction(
      () => new URL(window.location.href).searchParams.get('q') === 'return',
      null,
      { timeout: 2000 }
    );
    expect(page.url()).toContain('q=return');
  });

  test('clicking a category chip updates ?category, clicking again clears it', async ({ page }) => {
    await page.goto('/help');
    const chip = page.locator('[aria-label="Filter by category: guide"]');
    await chip.click();
    await expect(page).toHaveURL(/category=guide/);
    await chip.click();
    await expect(page).toHaveURL((url) => !url.searchParams.has('category'));
  });
});
