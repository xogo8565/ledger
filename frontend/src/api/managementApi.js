import { jsonOptions, requestJson, requestOk, requestResult } from './http';

export function saveAsset({ assetId, isCard, payload }) {
  const path = assetId
    ? isCard ? `/assets/${assetId}/card` : `/assets/${assetId}`
    : isCard ? '/assets/card' : '/assets';
  return requestOk(path, jsonOptions(assetId ? 'PUT' : 'POST', payload));
}

export function deleteAsset(assetId) {
  return requestOk(`/assets/${assetId}`, { method: 'DELETE' });
}

export function saveCategory(categoryId, payload) {
  return requestOk(
    categoryId ? `/categories/${categoryId}` : '/categories',
    jsonOptions(categoryId ? 'PUT' : 'POST', payload)
  );
}

export function deleteCategory(categoryId) {
  return requestOk(`/categories/${categoryId}`, { method: 'DELETE' });
}

export function getMembers() {
  return requestJson('/members');
}

export function getConsumerMigration() {
  return requestResult('/members/consumer-migration');
}

export function migrateConsumers() {
  return requestResult('/members/consumer-migration', { method: 'POST' });
}

export function saveMember(memberId, name) {
  return requestResult(
    memberId ? `/members/${memberId}` : '/members',
    jsonOptions(memberId ? 'PUT' : 'POST', { name })
  );
}

export function deleteMember(memberId) {
  return requestResult(`/members/${memberId}`, { method: 'DELETE' });
}

export function getBudgetSettings(month) {
  return requestJson(`/budgets/settings?month=${month}`);
}

export function getYearlyBudgetSummary(year) {
  return requestJson(`/budgets/summary/yearly?year=${year}`);
}

export function saveBudgetSettings(settings) {
  return requestOk('/budgets/settings', jsonOptions('POST', settings));
}

export function copyPreviousBudget(month) {
  return requestResult(`/budgets/settings/copy-previous?month=${month}`, { method: 'POST' });
}
