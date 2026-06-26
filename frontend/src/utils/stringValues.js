export function emptyIfNull(value) {
  return value === null || value === undefined ? '' : String(value);
}

export function trimToEmpty(value) {
  return emptyIfNull(value).trim();
}

export function normalizeWhitespace(value) {
  return trimToEmpty(value).replace(/\s+/g, ' ');
}

export function normalizeSearchKey(value) {
  return normalizeWhitespace(value).toLowerCase();
}

export function firstNonBlank(...values) {
  for (const value of values) {
    const normalized = trimToEmpty(value);
    if (normalized) return normalized;
  }
  return null;
}

export function containsAny(text, words = []) {
  const source = emptyIfNull(text);
  return words.some((word) => word !== null && word !== undefined && source.includes(String(word)));
}

export function uniqueNonBlank(values) {
  return [...new Set((values || [])
    .map((value) => trimToEmpty(value))
    .filter(Boolean))];
}

export function sortedByLengthDesc(values) {
  return [...(values || [])].sort((a, b) => String(b).length - String(a).length);
}
