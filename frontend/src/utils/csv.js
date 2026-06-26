export function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

export function downloadTextFile(filename, contents, type) {
  const blob = new Blob([contents], { type });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export function filteredFileLabel(filters, month) {
  return `${month}-filtered`;
}
