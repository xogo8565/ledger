export const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

export function money(value) {
  return `${Number(value || 0).toLocaleString('ko-KR')}원`;
}

export function numberOnly(value) {
  return Number(value || 0).toLocaleString('ko-KR');
}

export function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function monthLabel(month) {
  const [year, monthNumber] = month.split('-');
  return `${year}년 ${Number(monthNumber)}월`;
}

export function shiftMonth(month, amount) {
  const parsed = new Date(`${month}-01T00:00:00`);
  parsed.setMonth(parsed.getMonth() + amount);
  return formatDate(parsed).slice(0, 7);
}

export function sameMonth(date, month) {
  return date.slice(0, 7) === month;
}

export function transactionTone(type) {
  if (type === 'INCOME') return 'income';
  if (type === 'TRANSFER') return 'transfer';
  return 'expense';
}
