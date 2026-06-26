const API_BASE = '/api';

export class ApiError extends Error {
  constructor(response, data) {
    super(data?.error || `Request failed with status ${response.status}`);
    this.name = 'ApiError';
    this.status = response.status;
    this.data = data;
  }
}

export async function request(path, options) {
  return fetch(`${API_BASE}${path}`, options);
}

export async function requestJson(path, options) {
  const response = await request(path, options);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response, data);
  return data;
}

export async function requestOk(path, options) {
  const response = await request(path, options);
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new ApiError(response, data);
  }
  return response;
}

export async function requestResult(path, options) {
  const response = await request(path, options);
  const data = await response.json().catch(() => null);
  return {
    ok: response.ok,
    status: response.status,
    data
  };
}

export function jsonOptions(method, body) {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  };
}

export function errorMessage(error, fallback, translations = {}) {
  const serverMessage = error?.data?.error || error?.message;
  return translations[serverMessage] || serverMessage || fallback;
}
