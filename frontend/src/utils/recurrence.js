import { formatDate } from './format';

// 이미 기록된 거래를 반복 거래로 등록할 때 사용한다. 그 거래가 기록된 날짜에는 이미 실제
// 거래가 존재하므로, 반복 규칙의 최초 다음 실행일은 그 날짜가 아니라 한 주기 뒤로 미뤄서
// 계산해야 같은 날짜에 중복 거래가 생성되지 않는다. 매월/매년 반복은 백엔드의
// RecurringTransactionService와 동일하게 java.time.LocalDate.plusMonths처럼 말일을 넘기지
// 않도록 날짜를 클램프한다(예: 1월 31일 + 1개월 -> 2월 28일).
export function nextRecurrenceDate(dateString, frequency, intervalValue = 1) {
  const interval = Math.max(1, Number(intervalValue) || 1);
  const [year, month, day] = String(dateString || '').split('-').map(Number);
  if (!year || !month || !day) return dateString;

  if (frequency === 'DAILY') return formatDate(addDays(year, month, day, interval));
  if (frequency === 'WEEKLY') return formatDate(addDays(year, month, day, interval * 7));
  if (frequency === 'YEARLY') return formatDate(addMonthsClamped(year, month, day, interval * 12));
  return formatDate(addMonthsClamped(year, month, day, interval)); // MONTHLY(기본값)
}

function addDays(year, month, day, days) {
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + days);
  return date;
}

function addMonthsClamped(year, month, day, monthsToAdd) {
  const totalMonths = (year * 12 + (month - 1)) + monthsToAdd;
  const targetYear = Math.floor(totalMonths / 12);
  const targetMonthIndex = ((totalMonths % 12) + 12) % 12;
  const daysInTargetMonth = new Date(targetYear, targetMonthIndex + 1, 0).getDate();
  return new Date(targetYear, targetMonthIndex, Math.min(day, daysInTargetMonth));
}
