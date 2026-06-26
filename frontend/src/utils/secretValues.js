const MASK = '********';

export function maskSecret(value) {
  const normalized = String(value ?? '').trim();
  if (!normalized) return '';
  if (normalized.length <= 4) return MASK;
  return `${normalized.slice(0, 2)}${MASK}${normalized.slice(-2)}`;
}

export function maskAll(value) {
  return String(value ?? '').trim() ? MASK : '';
}
