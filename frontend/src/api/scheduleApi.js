import { jsonOptions, requestJson, requestOk } from './http';

export function getInstallmentSchedule(groupId) {
  return requestJson(`/transactions/installments/${groupId}`);
}

export function deleteInstallmentGroup(groupId) {
  return requestOk(`/transactions/installments/${groupId}`, { method: 'DELETE' });
}

export function getRecurringRules() {
  return requestJson('/recurring-transactions');
}

export function saveRecurringRule(ruleId, payload) {
  return requestJson(
    ruleId ? `/recurring-transactions/${ruleId}` : '/recurring-transactions',
    jsonOptions(ruleId ? 'PUT' : 'POST', payload)
  );
}

export function deleteRecurringRule(ruleId) {
  return requestOk(`/recurring-transactions/${ruleId}`, { method: 'DELETE' });
}

export function generateRecurringDue() {
  return requestOk('/recurring-transactions/generate-due', { method: 'POST' });
}

export async function getCardPaymentData(cardAssetId) {
  const [detail, schedules] = await Promise.all([
    requestJson(`/cards/${cardAssetId}`),
    requestJson(`/cards/${cardAssetId}/payment-schedules`)
  ]);
  return { detail, schedules };
}

export function createCardSchedule(cardAssetId, payload) {
  return requestOk(`/cards/${cardAssetId}/payment-schedules`, jsonOptions('POST', payload));
}

export function executeCardSchedule(scheduleId) {
  return requestOk('/cards/payment-schedules/execute', jsonOptions('POST', { scheduleId }));
}

export function retryCardSchedule(scheduleId) {
  return requestOk(`/cards/payment-schedules/${scheduleId}/retry`, { method: 'POST' });
}

export function rescheduleCardSchedule(scheduleId) {
  return requestOk(`/cards/payment-schedules/${scheduleId}/reschedule`, { method: 'POST' });
}

export function cancelCardSchedule(scheduleId) {
  return requestOk(`/cards/payment-schedules/${scheduleId}`, { method: 'DELETE' });
}
