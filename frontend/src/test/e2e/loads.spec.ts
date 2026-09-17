import { test, expect } from '@playwright/test'

/**
 * Smoke coverage for the load/job screens against a running backend.
 * Requires the API on :8080 with at least one load; skipped otherwise.
 */
test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username').fill('dispatcher')
  await page.getByLabel('Password').fill('dispatcher-pass')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/loads/)
})

test('loads list shows status and schedule, and search narrows it', async ({ page }) => {
  const table = page.getByRole('table')
  await expect(table).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Pickup window' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible()

  await page.getByLabel('Search loads').fill('chicago')
  await expect(page).toHaveURL(/q=chicago/)
  await expect(table.getByRole('link', { name: /Chicago/ }).first()).toBeVisible()
})

test('opening a load shows its full record and jobs', async ({ page }) => {
  await page.getByRole('table').getByRole('link').first().click()
  await expect(page).toHaveURL(/\/loads\/[0-9a-f-]{36}/)
  await expect(page.getByRole('heading', { level: 2, name: 'Schedule' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Commercial' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Jobs' })).toBeVisible()
})

test('jobs list filters by type', async ({ page }) => {
  await page.getByRole('link', { name: 'Jobs' }).click()
  await expect(page).toHaveURL(/\/jobs/)
  await page.getByLabel('Filter by type').selectOption('DROPOFF')
  await expect(page).toHaveURL(/jobType=DROPOFF/)
  await expect(page.getByRole('table').getByRole('link', { name: 'Dropoff' }).first()).toBeVisible()
})

test('an unknown load id shows a friendly error, not a blank page', async ({ page }) => {
  await page.goto('/loads/00000000-0000-0000-0000-000000000000')
  await expect(page.getByRole('alert')).toContainText('no longer exists')
})
