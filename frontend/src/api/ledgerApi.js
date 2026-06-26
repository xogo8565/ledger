import { jsonOptions, request, requestJson, requestOk, requestResult } from './http';

export async function getDashboard(month) {
  const [bootstrap, assetSummary, members] = await Promise.all([
    requestJson(`/bootstrap?month=${month}`),
    requestJson('/assets/summary'),
    requestJson('/members')
  ]);
  return { bootstrap, assetSummary, members };
}

export function searchTransactions(params) {
  return requestResult(`/transactions/search?${params}`);
}

export function getYearlySummary(year) {
  return requestJson(`/summary/yearly?year=${year}`);
}

export function getRangeSummary(startDate, endDate) {
  return requestJson(`/summary/range?startDate=${startDate}&endDate=${endDate}`);
}

export function getTransactionReceipts(transactionId) {
  return requestJson(`/transactions/${transactionId}/receipts`);
}

export function deleteTransactionReceipt(transactionId, receiptId) {
  return requestOk(`/transactions/${transactionId}/receipts/${receiptId}`, { method: 'DELETE' });
}

export function saveTransaction({ transactionId, installmentGroupId, payload }) {
  if (installmentGroupId) {
    return requestJson(`/transactions/installments/${installmentGroupId}`, jsonOptions('PUT', payload));
  }
  if (transactionId) {
    return requestJson(`/transactions/${transactionId}`, jsonOptions('PUT', payload));
  }
  return requestJson('/transactions', jsonOptions('POST', payload));
}

export function createTransaction(payload) {
  return requestJson('/transactions', jsonOptions('POST', payload));
}

export function deleteTransaction(transactionId) {
  return requestOk(`/transactions/${transactionId}`, { method: 'DELETE' });
}

export function uploadTransactionReceipts(transactionId, files) {
  const body = new FormData();
  files.forEach((file) => body.append('files', file));
  return requestOk(`/transactions/${transactionId}/receipts/batch`, { method: 'POST', body });
}

export function exportTransactions(month) {
  return request(`/export/transactions.csv?month=${month}`);
}

export function parseTransactionText(rawText) {
  return requestResult('/import/text/parse', jsonOptions('POST', { rawText }));
}

export function previewReceiptOcr(file) {
  const body = new FormData();
  body.append('file', file);
  return requestResult('/receipts/ocr', { method: 'POST', body });
}
