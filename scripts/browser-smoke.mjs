import { chromium, request } from 'playwright';

const frontendUrl = process.env.FRONTEND_URL || 'http://host.docker.internal:8081';
const backendUrl = process.env.BACKEND_URL || 'http://host.docker.internal:8080';
const screenshotPath = process.env.SCREENSHOT_PATH || '/work/browser-smoke-home.png';
const screenshotDir = process.env.SCREENSHOT_DIR || screenshotPath.replace(/\/[^/]+$/, '');

function assert(condition, message) {
  if (!condition) {
    throw new Error(`Browser smoke test failed: ${message}`);
  }
}

async function assertVisible(page, selector, message) {
  await page.waitForSelector(selector, { state: 'visible', timeout: 15000 });
  const count = await page.locator(selector).count();
  assert(count > 0, message);
}

async function clickBottomTab(page, index, selector) {
  await page.locator('.bottom-nav button').nth(index).click();
  await assertVisible(page, selector, `${selector} did not open`);
}

async function assertNoHorizontalOverflow(page, label) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }));
  assert(
    metrics.scrollWidth <= metrics.clientWidth + 1,
    `${label} has horizontal overflow: ${metrics.scrollWidth}px > ${metrics.clientWidth}px`
  );
}

function firstByType(items, type) {
  return items.find((item) => item.type === type);
}

async function selectFirstOption(locator, value) {
  await locator.selectOption(String(value));
}

async function assertResponseOk(response, label) {
  if (!response.ok()) {
    throw new Error(`Browser smoke test failed: ${label} returned HTTP ${response.status()} from ${response.url()}: ${await response.text()}`);
  }
}

const api = await request.newContext({ baseURL: backendUrl });
const cleanup = {
  assetIds: [],
  categoryIds: [],
  memberIds: [],
  cardScheduleIds: [],
  recurringRuleIds: [],
  transactionIds: [],
  installmentGroupIds: [],
  originalBudgetSettings: null
};
const bootstrapResponse = await api.get('/api/bootstrap');
assert(bootstrapResponse.ok(), `bootstrap API returned HTTP ${bootstrapResponse.status()}`);

let bootstrap = await bootstrapResponse.json();

if (!Array.isArray(bootstrap.assets) || bootstrap.assets.length === 0) {
  const seedAssetResponse = await api.post('/api/assets', {
    data: {
      type: 'CASH',
      name: `browser-smoke-seed-asset-${Date.now()}`,
      balance: 100000,
      groupName: '현금',
      ownerName: '',
      memo: ''
    }
  });
  await assertResponseOk(seedAssetResponse, 'seed asset creation');
  const seedAsset = await seedAssetResponse.json();
  cleanup.assetIds.push(seedAsset.id);
}

if (!Array.isArray(bootstrap.categories) || !bootstrap.categories.some((item) => item.type === 'EXPENSE')) {
  const seedCategoryResponse = await api.post('/api/categories', {
    data: {
      type: 'EXPENSE',
      name: `browser-smoke-seed-category-${Date.now()}`,
      icon: '•',
      color: '#609249'
    }
  });
  await assertResponseOk(seedCategoryResponse, 'seed category creation');
  const seedCategory = await seedCategoryResponse.json();
  cleanup.categoryIds.push(seedCategory.id);
}

if (cleanup.assetIds.length || cleanup.categoryIds.length) {
  const seededBootstrapResponse = await api.get('/api/bootstrap');
  assert(seededBootstrapResponse.ok(), `seeded bootstrap API returned HTTP ${seededBootstrapResponse.status()}`);
  bootstrap = await seededBootstrapResponse.json();
}

assert(Array.isArray(bootstrap.assets) && bootstrap.assets.length > 0, 'bootstrap did not return assets');
assert(Array.isArray(bootstrap.categories) && bootstrap.categories.length > 0, 'bootstrap did not return categories');

const expenseCategory = firstByType(bootstrap.categories, 'EXPENSE');
const cashAsset = bootstrap.assets.find((asset) => asset.type !== 'CARD' && asset.type !== 'DEBT');
const cardAsset = firstByType(bootstrap.assets, 'CARD');

assert(expenseCategory, 'bootstrap did not return an expense category');
assert(cashAsset, 'bootstrap did not return a cash/bank asset');

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  permissions: ['clipboard-read', 'clipboard-write']
});
await context.addInitScript(() => {
  let clipboardText = '';
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: {
      readText: async () => clipboardText,
      writeText: async (text) => {
        clipboardText = String(text || '');
      }
    }
  });
});
const page = await context.newPage();
const browserErrors = [];
const mutationRequests = [];

page.on('pageerror', (error) => browserErrors.push(error.message));
page.on('console', (message) => {
  if (message.type() === 'error') {
    browserErrors.push(message.text());
  }
});
page.on('request', (requestInfo) => {
  if (requestInfo.method() === 'POST' || requestInfo.method() === 'PUT' || requestInfo.method() === 'DELETE') {
    mutationRequests.push(`${requestInfo.method()} ${requestInfo.url()}`);
  }
});

try {
  await page.goto(frontendUrl, { waitUntil: 'networkidle', timeout: 30000 });
  await assertVisible(page, '.phone-shell', 'phone shell is not visible');
  await assertVisible(page, '.ledger-screen', 'ledger screen is not visible');
  await assertVisible(page, '.month-totals', 'month totals are not visible');
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await assertNoHorizontalOverflow(page, 'mobile ledger screen');

  await clickBottomTab(page, 2, '.assets-screen');
  await assertVisible(page, '.owner-asset-summary', 'owner asset summary did not render');
  const assetRows = await page.locator('.asset-row').count();
  assert(assetRows > 0, 'assets screen did not render any asset rows');
  await page.screenshot({ path: `${screenshotDir}/browser-smoke-assets-mobile.png`, fullPage: true });

  const invalidOwnerResponse = await api.post('/api/assets', {
    data: {
      type: 'CASH',
      name: `invalid-owner-asset-${Date.now()}`,
      balance: 1,
      groupName: '현금',
      ownerName: 'not-registered-owner',
      memo: ''
    }
  });
  assert(invalidOwnerResponse.status() === 400, `unregistered asset owner was accepted: ${invalidOwnerResponse.status()}`);

  const assetName = `browser-smoke-asset-${Date.now()}`;
  const assetUpdatedName = `${assetName}-updated`;
  await page.locator('.asset-actions .top-icon-button').last().click();
  await assertVisible(page, '.simple-edit-screen', 'asset form did not open');
  await page.locator('.simple-edit-screen .line-field').nth(1).locator('input').fill(assetName);
  const ownerSelect = page.locator('.simple-edit-screen .line-field').nth(2).locator('select');
  await ownerSelect.selectOption({ index: 1 });
  const assetOwnerName = await ownerSelect.locator('option:checked').textContent();
  const normalizedAssetOwnerName = assetOwnerName.replace(' · 기본', '');
  await page.locator('.simple-edit-screen .line-field').nth(3).locator('input').fill('12345');
  const [assetCreateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/assets')),
    page.locator('.simple-edit-screen .wide-save-button').click()
  ]);
  await assertResponseOk(assetCreateResponse, 'asset UI creation');
  const createdAsset = await assetCreateResponse.json();
  assert(createdAsset.ownerName === normalizedAssetOwnerName, `asset owner selection was not saved: ${createdAsset.ownerName}`);
  cleanup.assetIds.push(createdAsset.id);
  await page.waitForSelector(`.asset-row:has-text("${assetName}")`, { timeout: 15000 });
  await page.waitForSelector(`.owner-summary-row:has-text("${normalizedAssetOwnerName}")`, { timeout: 15000 });

  await page.locator('.asset-row', { hasText: assetName }).locator('button').first().click();
  await assertVisible(page, '.simple-edit-screen', 'asset edit form did not open');
  await page.locator('.simple-edit-screen .line-field').nth(1).locator('input').fill(assetUpdatedName);
  const [assetUpdateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes(`/api/assets/${createdAsset.id}`)),
    page.locator('.simple-edit-screen .wide-save-button').click()
  ]);
  await assertResponseOk(assetUpdateResponse, 'asset UI update');
  await page.waitForSelector(`.asset-row:has-text("${assetUpdatedName}")`, { timeout: 15000 });

  const cardButtons = await page.locator('.card-pay-button').count();
  if (cardButtons > 0) {
    await page.locator('.card-pay-button').first().click();
    await assertVisible(page, '.card-payment-manager', 'card payment manager did not open');
    await page.locator('.card-payment-form .wide-save-button').click();
    assert(
      mutationRequests.filter((requestItem) => requestItem.includes('/api/cards/') && requestItem.includes('/payment-schedules')).length === 0,
      'empty card payment form submitted a schedule mutation'
    );

    if (cardAsset) {
      await page.locator('.card-payment-form input[type="number"]').fill('1357');
      const [scheduleResponse] = await Promise.all([
        page.waitForResponse((response) => (
          response.request().method() === 'POST'
          && response.url().includes('/api/cards/')
          && response.url().includes('/payment-schedules')
        )),
        page.locator('.card-payment-form .wide-save-button').click()
      ]);
      await assertResponseOk(scheduleResponse, 'card schedule UI creation');
      const schedule = await scheduleResponse.json();
      cleanup.cardScheduleIds.push(schedule.id);
      await page.waitForSelector(`.card-payment-row:has-text("${schedule.scheduledDate}")`, { timeout: 15000 });
    }

    await page.locator('.full-panel .back-button').first().click();
    await assertVisible(page, '.assets-screen', 'assets screen did not return after closing card payment manager');
  }

  await clickBottomTab(page, 3, '.more-screen');
  await page.locator('.more-list button').nth(0).click();
  await assertVisible(page, '.category-manager', 'category manager did not open');
  const categoryName = `browser-smoke-category-${Date.now()}`;
  const categoryUpdatedName = `${categoryName}-updated`;
  await page.locator('.category-inline-form input').nth(1).fill(categoryName);
  const [categoryCreateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/categories')),
    page.locator('.category-inline-form button[type="submit"]').click()
  ]);
  await assertResponseOk(categoryCreateResponse, 'category UI creation');
  const createdCategory = await categoryCreateResponse.json();
  cleanup.categoryIds.push(createdCategory.id);
  await page.waitForSelector(`.category-admin-row:has-text("${categoryName}")`, { timeout: 15000 });

  await page.locator('.category-admin-row', { hasText: categoryName }).locator('button').nth(1).click();
  await page.locator('.category-inline-form input').nth(1).fill(categoryUpdatedName);
  const [categoryUpdateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes(`/api/categories/${createdCategory.id}`)),
    page.locator('.category-inline-form button[type="submit"]').click()
  ]);
  await assertResponseOk(categoryUpdateResponse, 'category UI update');
  await page.waitForSelector(`.category-admin-row:has-text("${categoryUpdatedName}")`, { timeout: 15000 });
  await page.locator('.full-panel .back-button').first().click();
  await assertVisible(page, '.more-screen', 'more screen did not return after closing category manager');

  await page.locator('.more-list button').nth(1).click();
  await assertVisible(page, '.member-manager', 'member manager did not open');
  await assertVisible(page, '.consumer-migration-card', 'consumer migration status did not render');
  const migrationStatusResponse = await api.get('/api/members/consumer-migration');
  await assertResponseOk(migrationStatusResponse, 'consumer migration status');
  const migrationStatus = await migrationStatusResponse.json();
  assert(typeof migrationStatus.eligibleCount === 'number', 'consumer migration status did not return eligibleCount');
  const ownerDeleteButton = page.locator('.member-row', { hasText: '기본 명의' }).getByRole('button', { name: '삭제' });
  assert(await ownerDeleteButton.isDisabled(), 'owner member delete button was not disabled');
  const memberName = `browser-smoke-member-${Date.now()}`;
  const memberUpdatedName = `${memberName}-updated`;
  await page.locator('.member-inline-form input').fill(memberName);
  const [memberCreateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/members')),
    page.locator('.member-inline-form button[type="submit"]').click()
  ]);
  await assertResponseOk(memberCreateResponse, 'member UI creation');
  const createdMember = await memberCreateResponse.json();
  cleanup.memberIds.push(createdMember.id);
  await page.waitForSelector(`.member-row:has-text("${memberName}")`, { timeout: 15000 });

  await page.locator('.member-row', { hasText: memberName }).getByRole('button', { name: '수정' }).click();
  await page.locator('.member-inline-form input').fill(memberUpdatedName);
  const [memberUpdateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes(`/api/members/${createdMember.id}`)),
    page.locator('.member-inline-form button[type="submit"]').click()
  ]);
  await assertResponseOk(memberUpdateResponse, 'member UI update');
  await page.waitForSelector(`.member-row:has-text("${memberUpdatedName}")`, { timeout: 15000 });
  page.once('dialog', (dialog) => dialog.accept());
  const [memberDeleteResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'DELETE' && response.url().includes(`/api/members/${createdMember.id}`)),
    page.locator('.member-row', { hasText: memberUpdatedName }).getByRole('button', { name: '삭제' }).click()
  ]);
  await assertResponseOk(memberDeleteResponse, 'member UI delete');
  cleanup.memberIds = cleanup.memberIds.filter((id) => id !== createdMember.id);
  await page.waitForSelector(`.member-row:has-text("${memberUpdatedName}")`, { state: 'detached', timeout: 15000 });
  await page.locator('.full-panel .back-button').first().click();
  await assertVisible(page, '.more-screen', 'more screen did not return after closing member manager');

  await page.locator('.more-list button').nth(2).click();
  await assertVisible(page, '.recurring-manager', 'recurring transaction manager did not open');
  await page.locator('.recurring-form .wide-save-button').click();
  assert(
    mutationRequests.filter((requestItem) => requestItem.includes('/api/recurring-transactions')).length === 0,
    'empty recurring form submitted a mutation'
  );

  const recurringTitle = `browser-smoke-recurring-${Date.now()}`;
  await page.locator('.recurring-form .line-field').nth(0).locator('input').fill('2468');
  await selectFirstOption(page.locator('.recurring-form .line-field').nth(1).locator('select'), expenseCategory.id);
  await selectFirstOption(page.locator('.recurring-form .line-field').nth(2).locator('select'), cashAsset.id);
  await page.locator('.recurring-form .line-field').nth(3).locator('input').fill(recurringTitle);
  const [recurringCreateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/recurring-transactions')),
    page.locator('.recurring-form .wide-save-button').click()
  ]);
  await assertResponseOk(recurringCreateResponse, 'recurring UI creation');
  const recurringRule = await recurringCreateResponse.json();
  assert(recurringRule.title === recurringTitle, `recurring title was not saved: ${recurringRule.title}`);
  cleanup.recurringRuleIds.push(recurringRule.id);
  await page.waitForSelector(`.recurring-row:has-text("${recurringTitle}")`, { timeout: 15000 });

  await page.locator('.recurring-row', { hasText: recurringTitle }).locator('button').first().click();
  await page.locator('.recurring-form .line-field').nth(0).locator('input').fill('3579');
  const [recurringUpdateResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes(`/api/recurring-transactions/${recurringRule.id}`)),
    page.locator('.recurring-form .wide-save-button').click()
  ]);
  await assertResponseOk(recurringUpdateResponse, 'recurring UI update');

  await page.locator('.full-panel .back-button').first().click();
  await assertVisible(page, '.more-screen', 'more screen did not return after closing recurring manager');

  await clickBottomTab(page, 0, '.ledger-screen');
  await page.locator('.fab').click();
  await assertVisible(page, '.entry-choice-sheet', 'transaction entry choice did not open');
  await page.getByRole('button', { name: '직접 입력' }).click();
  await assertVisible(page, '.entry-screen-form', 'transaction entry panel did not open');
  await page.locator('.entry-screen-form button[type="submit"]').click();
  assert(
    mutationRequests.filter((requestItem) => requestItem.includes('/api/transactions')).length === 0,
    'empty transaction entry form submitted a mutation'
  );

  const transactionTitle = `browser-smoke-transaction-${Date.now()}`;
  await page.locator('.entry-fields .line-field').nth(1).locator('input').fill('3690');
  await selectFirstOption(page.locator('.entry-fields .line-field').nth(2).locator('select'), expenseCategory.id);
  await selectFirstOption(page.locator('.entry-fields .line-field').nth(3).locator('select'), cashAsset.id);
  await selectFirstOption(page.locator('.entry-fields .line-field').nth(4).locator('select'), 3);
  await page.locator('.entry-fields .line-field').nth(5).locator('input').fill(transactionTitle);
  const consumerSelect = page.locator('.entry-fields .line-field').nth(8).locator('select');
  await consumerSelect.selectOption({ index: 1 });
  const selectedConsumerName = (await consumerSelect.locator('option:checked').textContent()).replace(' · 기본', '');
  const [transactionResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/transactions')),
    page.locator('.entry-screen-form button[type="submit"]').click()
  ]);
  await assertResponseOk(transactionResponse, 'transaction UI creation');
  const transaction = await transactionResponse.json();
  assert(transaction.consumptionScope === 'PERSONAL', `transaction consumption scope was not saved: ${transaction.consumptionScope}`);
  assert(transaction.consumerMemberName === selectedConsumerName, `transaction consumer was not saved: ${transaction.consumerMemberName}`);
  cleanup.transactionIds.push(transaction.id);
  if (transaction.installmentGroupId) {
    cleanup.installmentGroupIds.push(transaction.installmentGroupId);
  }
  const sharedTransactionResponse = await api.post('/api/transactions', {
    data: {
      type: 'EXPENSE',
      transactionDate: transaction.transactionDate,
      amount: 1,
      categoryId: expenseCategory.id,
      assetId: cashAsset.id,
      title: `${transactionTitle}-shared`,
      consumptionScope: 'SHARED',
      consumerMemberId: transaction.consumerMemberId,
      installmentMonths: 0
    }
  });
  await assertResponseOk(sharedTransactionResponse, 'shared transaction consumer normalization');
  const sharedTransaction = await sharedTransactionResponse.json();
  assert(sharedTransaction.consumerMemberId === null, `shared transaction kept a consumer: ${sharedTransaction.consumerMemberId}`);
  cleanup.transactionIds.push(sharedTransaction.id);
  const expectedFilteredExpense = Number(transaction.amount) + Number(sharedTransaction.amount);
  const scopeSummaryResponse = await api.get(`/api/summary/monthly?month=${transaction.transactionDate.slice(0, 7)}`);
  await assertResponseOk(scopeSummaryResponse, 'monthly consumption scope summary');
  const scopeSummary = await scopeSummaryResponse.json();
  assert(
    scopeSummary.scopeSpends.some((item) => item.scope === 'PERSONAL'),
    'monthly summary did not include personal consumption'
  );
  assert(
    scopeSummary.scopeSpends.some((item) => item.scope === 'SHARED'),
    'monthly summary did not include shared consumption'
  );
  assert(
    scopeSummary.memberSpends.some((item) => item.memberName === selectedConsumerName),
    'monthly summary did not include selected consumer member consumption'
  );
  const rangeSummaryResponse = await api.get(`/api/summary/range?startDate=${transaction.transactionDate.slice(0, 7)}-01&endDate=${transaction.transactionDate}`);
  await assertResponseOk(rangeSummaryResponse, 'custom range summary');
  const rangeSummary = await rangeSummaryResponse.json();
  assert(
    rangeSummary.expense >= expectedFilteredExpense,
    `range summary expense did not include created transactions: ${rangeSummary.expense}`
  );
  assert(
    rangeSummary.categorySpends.some((item) => item.categoryName === expenseCategory.name),
    'range summary did not include expense category breakdown'
  );
  await page.reload({ waitUntil: 'networkidle' });
  await assertVisible(page, '.ledger-screen', 'ledger screen did not return after creating transaction');
  await page.getByLabel('거래 검색').fill(transactionTitle);
  await page.waitForTimeout(150);
  const filteredExpenseText = await page.locator('.month-totals .mini-metric.expense strong').textContent();
  assert(
    filteredExpenseText?.replaceAll(',', '').includes(String(expectedFilteredExpense)),
    `filtered transaction total did not match created transaction: ${filteredExpenseText}`
  );
  await page.locator('.transaction-row.has-installment', { hasText: transactionTitle }).locator('.installment-chip').first().click();
  await assertVisible(page, '.installment-manager', 'installment manager did not open for receipt edit');
  await page.locator('.installment-manager .text-action', { hasText: '수정' }).click();
  await assertVisible(page, '.entry-screen-form', 'installment group edit form did not open');
  await page.locator('.line-field', { hasText: '첨부 회차' }).locator('select').selectOption('2');
  await page.locator('.receipt-compact input[type="file"]').setInputFiles({
    name: 'browser-smoke-installment-receipt.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=', 'base64')
  });
  const [installmentUpdateResponse, installmentReceiptUploadResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes('/api/transactions/installments/')),
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().includes('/receipts/batch')),
    page.locator('.entry-screen-form button[type="submit"]').click()
  ]);
  await assertResponseOk(installmentUpdateResponse, 'installment group edit with receipt');
  await assertResponseOk(installmentReceiptUploadResponse, 'installment group receipt upload');
  const updatedInstallments = await installmentUpdateResponse.json();
  const receiptTarget = updatedInstallments.find((item) => item.installmentIndex === 2);
  assert(receiptTarget, 'updated installment group did not include target receipt installment');
  const installmentReceiptsResponse = await api.get(`/api/transactions/${receiptTarget.id}/receipts`);
  await assertResponseOk(installmentReceiptsResponse, 'installment receipt list');
  const installmentReceipts = await installmentReceiptsResponse.json();
  const installmentReceipt = installmentReceipts.find((item) => item.originalFilename === 'browser-smoke-installment-receipt.png');
  assert(installmentReceipt, 'installment receipt was not attached to selected installment');
  await api.delete(`/api/transactions/${receiptTarget.id}/receipts/${installmentReceipt.id}`).catch(() => {});
  await assertVisible(page, '.ledger-screen', 'ledger screen did not return after installment group receipt edit');
  await page.locator('.ledger-filters button', { hasText: '초기화' }).click();
  await page.locator('.fab').click();
  await assertVisible(page, '.entry-choice-sheet', 'transaction entry choice did not reopen after create');
  await page.getByRole('button', { name: '직접 입력' }).click();
  await assertVisible(page, '.entry-screen-form', 'transaction entry panel did not reopen after create');
  await page.locator('.full-panel .back-button').first().click();
  await assertVisible(page, '.ledger-screen', 'ledger screen did not return after closing entry panel');

  await page.evaluate(async () => navigator.clipboard.writeText(''));
  await page.locator('.fab').click();
  await assertVisible(page, '.entry-choice-sheet', 'empty clipboard entry choice did not open');
  let emptyClipboardMessage = '';
  page.once('dialog', async (dialog) => {
    emptyClipboardMessage = dialog.message();
    await dialog.accept();
  });
  await page.getByRole('button', { name: '문자 자동 입력' }).click();
  assert(emptyClipboardMessage.includes('문자 내용이 없습니다'), `unexpected empty clipboard alert: ${emptyClipboardMessage}`);
  await assertVisible(page, '.entry-choice-sheet', 'entry choice closed after empty clipboard alert');

  await page.evaluate(async () => navigator.clipboard.writeText('[신한카드] 06/24 스타벅스 8,900원'));
  const [textParseResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/import/text/parse')),
    page.getByRole('button', { name: '문자 자동 입력' }).click()
  ]);
  await assertResponseOk(textParseResponse, 'clipboard text parse');
  await assertVisible(page, '.entry-screen-form', 'clipboard text did not open transaction entry');
  const clipboardAmount = await page.locator('.entry-fields .line-field').nth(1).locator('input').inputValue();
  assert(clipboardAmount === '8900', `clipboard amount was not populated: ${clipboardAmount}`);
  await page.locator('.full-panel .back-button').first().click();
  await assertVisible(page, '.ledger-screen', 'ledger screen did not return after clipboard entry');

  const advancedSearchResponse = await api.get(
    `/api/transactions/search?startDate=${transaction.transactionDate}&endDate=${transaction.transactionDate}`
    + `&type=EXPENSE&assetId=${cashAsset.id}&minAmount=${transaction.amount}&maxAmount=${transaction.amount}`
    + `&query=${encodeURIComponent(transactionTitle)}`
  );
  await assertResponseOk(advancedSearchResponse, 'advanced transaction search');
  const advancedSearchRows = await advancedSearchResponse.json();
  assert(advancedSearchRows.some((item) => item.id === transaction.id), 'advanced search did not return matching transaction');

  const [advancedFilterResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/transactions/search?')),
    page.getByLabel('자산 필터').selectOption(String(cashAsset.id))
  ]);
  await assertResponseOk(advancedFilterResponse, 'asset filter search');
  await page.getByLabel('최소 금액').fill(String(transaction.amount));
  await page.getByLabel('최대 금액').fill(String(transaction.amount));
  await page.waitForResponse((response) => response.url().includes('/api/transactions/search?') && response.url().includes('minAmount='));
  await page.waitForSelector(`.transaction-row:has-text("${transactionTitle}")`, { timeout: 15000 });
  await page.locator('.ledger-filters button', { hasText: '초기화' }).click();

  await clickBottomTab(page, 1, '.stats-screen');
  await page.getByRole('button', { name: '기간' }).click();
  await assertVisible(page, '.stats-range-filter', 'range stats filter did not render');
  await page.getByLabel('통계 시작일').fill(`${transaction.transactionDate.slice(0, 7)}-01`);
  await page.getByLabel('통계 종료일').fill(transaction.transactionDate);
  await page.waitForSelector(`.period-stats:has-text("${expenseCategory.name}")`, { timeout: 15000 });
  await page.getByRole('button', { name: '월별' }).click();
  await page.getByRole('button', { name: '개인/공동' }).click();
  await page.waitForSelector('.scope-ranking-row:has-text("개인")', { timeout: 15000 });
  await page.waitForSelector('.scope-ranking-row:has-text("공동")', { timeout: 15000 });
  await page.locator('.scope-ranking-row', { hasText: '공동' }).click();
  await assertVisible(page, '.ledger-screen', 'scope statistics did not open ledger');
  const selectedScopeFilter = await page.getByLabel('소비 구분 필터').inputValue();
  assert(selectedScopeFilter === 'SHARED', `shared scope filter was not selected: ${selectedScopeFilter}`);
  await page.waitForSelector(`.transaction-row:has-text("${transactionTitle}-shared")`, { timeout: 15000 });
  await clickBottomTab(page, 1, '.stats-screen');
  await page.getByRole('button', { name: '명의별' }).click();
  await page.waitForSelector(`.member-ranking-row:has-text("${selectedConsumerName}")`, { timeout: 15000 });
  await page.locator('.member-ranking-row', { hasText: selectedConsumerName }).click();
  await assertVisible(page, '.ledger-screen', 'member statistics did not open ledger');
  const selectedConsumerFilter = await page.getByLabel('소비 명의 필터').inputValue();
  assert(String(selectedConsumerFilter) === String(transaction.consumerMemberId), `consumer member filter was not selected: ${selectedConsumerFilter}`);
  await page.waitForSelector(`.transaction-row:has-text("${transactionTitle}")`, { timeout: 15000 });
  await clickBottomTab(page, 1, '.stats-screen');
  await page.locator('.segmented-tabs button').nth(1).click();
  await assertVisible(page, '.budget-screen', 'budget stats screen did not open');
  await assertVisible(page, '.weekly-budget-stats', 'weekly budget statistics did not render');
  const yearlyBudgetResponse = await api.get(`/api/budgets/summary/yearly?year=${new Date().getFullYear()}`);
  await assertResponseOk(yearlyBudgetResponse, 'yearly budget summary');
  const yearlyBudget = await yearlyBudgetResponse.json();
  assert(Array.isArray(yearlyBudget.monthlyUsages) && yearlyBudget.monthlyUsages.length === 12, 'yearly budget summary did not return 12 months');
  await page.getByRole('button', { name: '연간' }).click();
  await assertVisible(page, '.yearly-budget-screen', 'yearly budget screen did not render');
  await assertVisible(page, '.year-nav', 'year navigation did not render for yearly budget');
  assert(await page.locator('.yearly-budget-row').count() === 12, 'yearly budget screen did not render 12 months');
  await page.getByRole('button', { name: '월별' }).click();
  await assertVisible(page, '.weekly-budget-stats', 'monthly budget screen did not return from yearly budget');
  const budgetSettingsResponse = await api.get(`/api/budgets/settings?month=${new Date().toISOString().slice(0, 7)}`);
  await assertResponseOk(budgetSettingsResponse, 'budget settings snapshot');
  cleanup.originalBudgetSettings = await budgetSettingsResponse.json();
  await page.locator('.budget-headline button').click();
  await assertVisible(page, '.budget-settings-screen', 'budget settings screen did not open');
  const originalBudgetTotal = Number(cleanup.originalBudgetSettings.totalAmount || 0);
  await page.locator('.budget-total-edit input').fill(String(originalBudgetTotal + 1111));
  const [budgetSaveResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/api/budgets/settings')),
    page.locator('.budget-settings-screen .wide-save-button').click()
  ]);
  await assertResponseOk(budgetSaveResponse, 'budget UI save');
  await assertVisible(page, '.stats-screen', 'stats screen did not return after saving budget settings');

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(frontendUrl, { waitUntil: 'networkidle', timeout: 30000 });
  await assertVisible(page, '.ledger-screen', 'desktop ledger screen is not visible');
  await assertNoHorizontalOverflow(page, 'desktop ledger screen');
  await page.screenshot({ path: `${screenshotDir}/browser-smoke-ledger-desktop.png`, fullPage: true });

  assert(browserErrors.length === 0, browserErrors.join('\n'));

  console.log(JSON.stringify({
    frontendUrl,
    backendUrl,
    bootstrapAssets: bootstrap.assets.length,
    bootstrapCategories: bootstrap.categories.length,
    assetRows,
    cardPaymentPanelChecked: cardButtons > 0,
    recurringPanelChecked: true,
    entryPanelChecked: true,
    emptyFormMutationGuardChecked: true,
    successfulUiMutationChecked: true,
    assetCategoryBudgetUiChecked: true,
    screenshotPaths: [
      screenshotPath,
      `${screenshotDir}/browser-smoke-assets-mobile.png`,
      `${screenshotDir}/browser-smoke-ledger-desktop.png`
    ]
  }, null, 2));
} finally {
  if (cleanup.originalBudgetSettings) {
    await api.post('/api/budgets/settings', {
      data: {
        month: cleanup.originalBudgetSettings.month,
        totalAmount: Number(cleanup.originalBudgetSettings.totalAmount || 0),
        categories: cleanup.originalBudgetSettings.categories.map((item) => ({
          categoryId: item.categoryId,
          amount: Number(item.amount || 0)
        }))
      }
    }).catch(() => {});
  }
  for (const groupId of cleanup.installmentGroupIds) {
    const response = await api.get(`/api/transactions/installments/${groupId}`);
    if (response.ok()) {
      const rows = await response.json();
      for (const row of rows) {
        await api.delete(`/api/transactions/${row.id}`);
      }
    }
  }
  for (const transactionId of cleanup.transactionIds) {
    await api.delete(`/api/transactions/${transactionId}`).catch(() => {});
  }
  for (const recurringRuleId of cleanup.recurringRuleIds) {
    await api.delete(`/api/recurring-transactions/${recurringRuleId}`).catch(() => {});
  }
  for (const scheduleId of cleanup.cardScheduleIds) {
    await api.delete(`/api/cards/payment-schedules/${scheduleId}`).catch(() => {});
  }
  for (const categoryId of cleanup.categoryIds) {
    await api.delete(`/api/categories/${categoryId}`).catch(() => {});
  }
  for (const memberId of cleanup.memberIds) {
    await api.delete(`/api/members/${memberId}`).catch(() => {});
  }
  for (const assetId of cleanup.assetIds) {
    await api.delete(`/api/assets/${assetId}`).catch(() => {});
  }
  await browser.close();
  await api.dispose();
}
