export function toNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function toPositiveNumber(value, fallback = 0) {
  const parsed = toNumber(value, fallback);
  return parsed > 0 ? parsed : fallback;
}

export function parseDecimal(value) {
  const normalized = String(value ?? '')
    .replaceAll(',', '')
    .replaceAll('원', '')
    .replaceAll('₩', '')
    .trim();
  if (!normalized || normalized === '-' || normalized === '.' || normalized === '-.') {
    return 0;
  }
  return toNumber(normalized, 0);
}

export function parseWonAmount(value) {
  return parseDecimal(value);
}

export function formatNumber(value, locale = 'ko-KR') {
  return toNumber(value).toLocaleString(locale);
}

export function formatWon(value) {
  return `${formatNumber(value)}원`;
}

export function percent(numerator, denominator, digits = 0) {
  const base = toNumber(denominator);
  if (!base) return 0;
  const value = (toNumber(numerator) / base) * 100;
  return Number(value.toFixed(digits));
}
