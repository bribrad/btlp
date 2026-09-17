import { test, expect } from '@playwright/test'

/** Form flows against a running backend: inline validation, save, and keyboard operation. */
test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username').fill('dispatcher')
  await page.getByLabel('Password').fill('dispatcher-pass')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/loads/)
})

test('creating a load shows inline errors, then saves and appears in the list', async ({
  page,
}) => {
  await page.getByRole('link', { name: 'New load' }).click()
  await expect(page).toHaveURL(/\/loads\/new/)

  // Required fields are caught before any request goes out.
  await page.getByRole('button', { name: 'Create load' }).click()
  await expect(page.getByText('Origin is required')).toBeVisible()
  await expect(page.getByText('Destination is required')).toBeVisible()

  const stamp = `E2E-${Date.now()}`
  await page.getByLabel(/Origin/).fill(`${stamp} Origin`)
  await page.getByLabel(/Destination/).fill(`${stamp} Destination`)
  await page.getByLabel(/Customer/).fill('E2ECO')

  // Timeline errors land on the later field.
  await page.getByLabel('Pickup window start').fill('2026-03-04T10:00')
  await page.getByLabel('Pickup window end').fill('2026-03-03T10:00')
  await page.getByRole('button', { name: 'Create load' }).click()
  await expect(page.getByText('Must be at or after the pickup window start')).toBeVisible()

  await page.getByLabel('Pickup window end').fill('2026-03-05T10:00')
  await page.getByRole('button', { name: 'Create load' }).click()

  await expect(page).toHaveURL(/\/loads\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: `${stamp} Origin → ${stamp} Destination` }))
    .toBeVisible()

  // The list reflects the save.
  await page.getByRole('link', { name: 'Back to loads' }).click()
  await page.getByLabel('Search loads').fill(stamp)
  await expect(page.getByRole('table').getByRole('link', { name: new RegExp(stamp) })).toBeVisible()
})

test('editing a load persists the change to the detail view', async ({ page }) => {
  await page.getByRole('table').getByRole('link').first().click()
  await page.getByRole('link', { name: 'Edit' }).click()
  await expect(page).toHaveURL(/\/edit$/)

  const notes = `Updated ${Date.now()}`
  await page.getByLabel('Notes').fill(notes)
  await page.getByRole('button', { name: 'Save changes' }).click()

  await expect(page).toHaveURL(/\/loads\/[0-9a-f-]{36}$/)
  await expect(page.getByText(notes)).toBeVisible()
})

test('adding a job from a load preselects that load', async ({ page }) => {
  await page.getByRole('table').getByRole('link').first().click()
  const loadUrl = page.url()
  const loadId = loadUrl.split('/').pop()!

  await page.getByRole('link', { name: 'Add job' }).click()
  await expect(page).toHaveURL(new RegExp(`/jobs/new\\?loadId=${loadId}`))
  await expect(page.getByLabel(/Load/)).toHaveValue(loadId)

  await page.getByRole('button', { name: 'Create job' }).click()
  await expect(page).toHaveURL(/\/jobs\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: /Leg \d+/ })).toBeVisible()
})

test('the load form is operable from the keyboard alone', async ({ page }) => {
  await page.goto('/loads/new')

  // Origin is focused on arrival; Tab walks the fields in order.
  await expect(page.getByLabel(/Origin/)).toBeFocused()
  const stamp = `KEY-${Date.now()}`
  await page.keyboard.type(`${stamp} Origin`)
  await page.keyboard.press('Tab')
  await expect(page.getByLabel(/Destination/)).toBeFocused()
  await page.keyboard.type(`${stamp} Destination`)

  // Enter submits from a text field.
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/loads\/[0-9a-f-]{36}$/)
})

test('Escape backs out of a form', async ({ page }) => {
  await page.goto('/loads/new')
  await page.keyboard.press('Escape')
  await expect(page).toHaveURL(/\/loads$/)
})
