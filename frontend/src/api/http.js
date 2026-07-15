const API_BASE = '/api';
const LOG_RESPONSE_LIMIT = 2000;

export class ApiError extends Error {
  constructor(response, data) {
    super(apiErrorMessage(data) || `Request failed with status ${response.status}`);
    this.name = 'ApiError';
    this.status = response.status;
    this.data = data;
  }
}

export async function request(path, options) {
  const url = `${API_BASE}${path}`;
  const method = options?.method || 'GET';
  const startedAt = performance.now();
  try {
    const response = await fetch(url, options);
    logApiResult({ method, url, options, response: response.clone(), startedAt });
    return response;
  } catch (error) {
    logApiFailure({ method, url, options, error, startedAt });
    throw error;
  }
}

export async function requestJson(path, options) {
  const response = await request(path, options);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response, data);
  return unwrapApiResponse(data);
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
    data: response.ok ? unwrapApiResponse(data) : data
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
  const serverMessage = apiErrorMessage(error?.data) || error?.message;
  return translations[serverMessage] || serverMessage || fallback;
}

export function unwrapApiResponse(data) {
  if (data && typeof data === 'object' && 'success' in data && 'data' in data && 'error' in data) {
    return data.data;
  }
  return data;
}

function apiErrorMessage(data) {
  if (!data) return '';
  if (typeof data.error === 'string') return data.error;
  return data.error?.message || '';
}

async function logApiResult({ method, url, options, response, startedAt }) {
  const elapsedMs = Math.round(performance.now() - startedAt);
  const body = await readResponseBody(response).catch((error) => ({ parseError: error.message }));
  const level = response.ok ? 'log' : 'warn';
  console.groupCollapsed(`[API] ${method} ${url} -> ${response.status} (${elapsedMs}ms)`);
  console[level]('request', sanitizeRequest(options));
  console[level]('response', {
    ok: response.ok,
    status: response.status,
    statusText: response.statusText,
    body: sanitizeValue(body)
  });
  console.groupEnd();
}

function logApiFailure({ method, url, options, error, startedAt }) {
  const elapsedMs = Math.round(performance.now() - startedAt);
  console.groupCollapsed(`[API] ${method} ${url} -> network error (${elapsedMs}ms)`);
  console.error('request', sanitizeRequest(options));
  console.error('error', error);
  console.groupEnd();
}

async function readResponseBody(response) {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) return response.json();
  const text = await response.text();
  return text.length > LOG_RESPONSE_LIMIT ? `${text.slice(0, LOG_RESPONSE_LIMIT)}...` : text;
}

function sanitizeRequest(options) {
  if (!options) return { method: 'GET' };
  return {
    method: options.method || 'GET',
    headers: sanitizeValue(options.headers || {}),
    body: sanitizeBody(options.body)
  };
}

function sanitizeBody(body) {
  if (!body) return undefined;
  if (body instanceof FormData) return '[FormData]';
  if (typeof body !== 'string') return '[Body]';
  try {
    return sanitizeValue(JSON.parse(body));
  } catch {
    return body.length > LOG_RESPONSE_LIMIT ? `${body.slice(0, LOG_RESPONSE_LIMIT)}...` : body;
  }
}

function sanitizeValue(value) {
  if (Array.isArray(value)) return value.map(sanitizeValue);
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      isSensitiveKey(key) ? '***REDACTED***' : sanitizeValue(item)
    ])
  );
}

function isSensitiveKey(key) {
  return /password|secret|token|authorization|api[-_]?key|private[-_]?key/i.test(key);
}
