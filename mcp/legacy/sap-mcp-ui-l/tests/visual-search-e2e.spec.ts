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

test.describe('Visual Search — End-to-End Backend Verification', () => {

  test('upload image via search bar, verify backend call, and confirm response renders', async ({ page }) => {
    await login(page);

    // Intercept the backend call to capture request and response
    let backendRequest: { url: string; method: string; postData: string | null } | null = null;
    let backendResponse: { status: number; body: string } | null = null;

    page.on('request', (req) => {
      if (req.url().includes('/agent/visual-search')) {
        backendRequest = {
          url: req.url(),
          method: req.method(),
          postData: req.postData(),
        };
      }
    });

    page.on('response', async (res) => {
      if (res.url().includes('/agent/visual-search')) {
        backendResponse = {
          status: res.status(),
          body: await res.text(),
        };
      }
    });

    await page.goto('/products');

    // Upload image via the search bar camera button
    const fileInput = page.locator('input[type="file"][accept="image/*"]');
    await fileInput.setInputFiles(TEST_IMAGE);

    // Wait for AI Analysis to appear (backend responded)
    await expect(page.locator('text=AI Analysis')).toBeVisible({ timeout: 30000 });

    // ── Verify the backend request was made correctly ──
    expect(backendRequest).not.toBeNull();
    expect(backendRequest!.method).toBe('POST');
    expect(backendRequest!.url).toContain('/agent/visual-search');

    // Verify request body contains base64 image and mimeType
    const requestBody = JSON.parse(backendRequest!.postData!);
    expect(requestBody.image).toBeTruthy();
    expect(requestBody.image.length).toBeGreaterThan(10);
    expect(requestBody.mimeType).toBe('image/jpeg');

    // ── Verify the backend response ──
    expect(backendResponse).not.toBeNull();
    expect(backendResponse!.status).toBe(200);

    const responseBody = JSON.parse(backendResponse!.body);
    expect(responseBody.visionAnalysis).toBeTruthy();
    expect(typeof responseBody.visionAnalysis).toBe('string');
    expect(responseBody.visionAnalysis.length).toBeGreaterThan(5);
    expect(responseBody).toHaveProperty('products');
    expect(Array.isArray(responseBody.products)).toBe(true);

    // ── Verify the response rendered in the UI ──
    const analysisText = responseBody.visionAnalysis;
    await expect(page.locator(`text=${analysisText.substring(0, 30)}`)).toBeVisible();

    if (responseBody.products.length > 0) {
      const firstProduct = responseBody.products[0];
      // Product name should be visible (rendered by ProductCard)
      await expect(page.locator(`text=${firstProduct.product.name}`).first()).toBeVisible();
      // Match type badge
      const matchLabel = firstProduct.matchType === 'bestMatch' ? 'Best Match'
        : firstProduct.matchType === 'similar' ? 'Similar' : 'You Might Like';
      await expect(page.locator(`text=${matchLabel}`).first()).toBeVisible();
      // Match count
      await expect(page.locator(`text=Found ${responseBody.products.length} catalog match`)).toBeVisible();
      // AI reasoning should include search terms if present
      if (responseBody.aiDetail?.searchTerms?.length > 0) {
        await expect(page.locator('text=Searched for')).toBeVisible();
      }
    } else {
      await expect(page.locator('text=No matching products found')).toBeVisible();
    }

    // Log the full round-trip
    console.log('\n── Backend Round-Trip Verified ──');
    console.log(`Request:  POST ${backendRequest!.url}`);
    console.log(`Image:    ${requestBody.image.length} chars base64, ${requestBody.mimeType}`);
    console.log(`Response: HTTP ${backendResponse!.status}`);
    console.log(`Analysis: "${responseBody.visionAnalysis}"`);
    console.log(`Products: ${responseBody.products.length} matches`);
    responseBody.products.forEach((p: any, i: number) => {
      console.log(`  [${i}] ${p.matchType} (${Math.round(p.confidence * 100)}%) — ${p.product.name} (${p.product.code})`);
    });
  });
});
