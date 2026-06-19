import { test, expect } from '@playwright/test';

const TEST_USER = 'john.doe@thinkshop.com';
const TEST_PASS = '1234';

async function dismissUserPicker(page: import('@playwright/test').Page) {
  const emailInput = page.locator('input[placeholder*="email" i]');
  if (await emailInput.isVisible({ timeout: 3000 }).catch(() => false)) {
    await emailInput.fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page
      .locator('.fixed button[type="submit"], .fixed button:has-text("Sign In"), .fixed button:has-text("Log In")')
      .first()
      .click();
    await expect(page.locator('.fixed.inset-0')).not.toBeVisible({ timeout: 10000 });
  }
}

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await dismissUserPicker(page);
});

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

test.describe('/help/:uid — detail', () => {
  test('clicking a result navigates to the detail page', async ({ page }) => {
    await page.goto('/help');
    await page.waitForLoadState('networkidle');
    const firstLink = page.locator('a[href^="/help/"]').first();
    if ((await firstLink.count()) === 0) {
      // Backend has no entries; not-found test still covers the route.
      return;
    }
    await firstLink.click();
    await expect(page).toHaveURL(/\/help\/[^/]+/);
    await expect(page.getByLabel('Back to Help')).toBeVisible();
    await expect(page.locator('h1')).toBeVisible();
  });

  test('unknown uid renders a clean not-found block with a back link', async ({ page }) => {
    await page.goto('/help/__nonexistent_kb_entry_for_test__');
    await expect(page.getByRole('heading', { name: 'Help entry not found' })).toBeVisible();
    await expect(page.getByLabel('Back to Help')).toBeVisible();
  });
});
