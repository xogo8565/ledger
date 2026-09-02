import { errorMessage } from '../api/http';
import * as scheduleApi from '../api/scheduleApi';
import { toNumber } from '../utils/numberValues';

export function useScheduleMutations({
  recurringForm,
  editingRecurringRule,
  setRecurringRules,
  setEditingRecurringRule,
  setRecurringForm,
  emptyRecurringForm,
  selectedCard,
  cardScheduleForm,
  setCardScheduleForm,
  emptyCardScheduleForm,
  setCardDetail,
  setCardSchedules,
  reload
}) {
  async function run(action, fallback) {
    try {
      return await action();
    } catch (error) {
      console.error(error);
      window.alert(errorMessage(error, fallback));
      return null;
    }
  }

  async function loadRecurringRules() {
    const rules = await run(
      () => scheduleApi.getRecurringRules(),
      '반복 거래 목록을 불러오지 못했습니다.'
    );
    if (rules) setRecurringRules(rules);
  }

  async function saveRecurringRule(event) {
    event.preventDefault();
    const amount = toNumber(recurringForm.amount);
    if (!amount || amount <= 0 || !recurringForm.startDate || !recurringForm.nextRunDate) return;
    const nextRunDate = recurringForm.nextRunDate || recurringForm.startDate;
    if (recurringForm.endDate && recurringForm.endDate < nextRunDate) {
      window.alert('종료일은 다음 실행일 이후여야 합니다.');
      return;
    }
    const payload = {
      ...recurringForm,
      amount,
      categoryId: recurringForm.categoryId ? toNumber(recurringForm.categoryId) : null,
      assetId: recurringForm.assetId ? toNumber(recurringForm.assetId) : null,
      fromAssetId: recurringForm.fromAssetId ? toNumber(recurringForm.fromAssetId) : null,
      toAssetId: recurringForm.toAssetId ? toNumber(recurringForm.toAssetId) : null,
      intervalValue: toNumber(recurringForm.intervalValue, 1),
      installmentMonths: toNumber(recurringForm.installmentMonths),
      endDate: recurringForm.endDate || null,
      nextRunDate
    };
    const savedRule = await run(
      () => scheduleApi.saveRecurringRule(editingRecurringRule?.id, payload),
      '반복 거래 저장에 실패했습니다.'
    );
    if (!savedRule) return;
    setEditingRecurringRule(null);
    setRecurringForm(emptyRecurringForm());
    // 등록/수정 직후 대기 중인 회차(오늘까지 밀린 회차 포함)를 바로 생성해, 익월 등 다음
    // 회차가 반복 거래 목록과 가계부에 수동 조작 없이 곧바로 반영되도록 한다.
    const generated = await run(
      () => scheduleApi.generateRecurringDue(),
      '반복 거래를 자동으로 반영하지 못했습니다.'
    );
    await loadRecurringRules();
    if (generated) await reload();
  }

  async function deleteRecurringRule(rule) {
    const label = rule.title || rule.categoryName || '반복 거래';
    if (!window.confirm(`${label} 반복 거래를 삭제할까요? 이미 생성된 거래 내역은 그대로 유지됩니다.`)) return;
    const result = await run(
      () => scheduleApi.deleteRecurringRule(rule.id),
      '반복 거래 삭제에 실패했습니다.'
    );
    if (result) await loadRecurringRules();
  }

  async function generateRecurringDue() {
    const result = await run(
      () => scheduleApi.generateRecurringDue(),
      '반복 거래 생성에 실패했습니다.'
    );
    if (!result) return;
    await loadRecurringRules();
    await reload();
  }

  async function loadCardPaymentData(cardAssetId) {
    const data = await run(
      () => scheduleApi.getCardPaymentData(cardAssetId),
      '카드 결제 정보를 불러오지 못했습니다.'
    );
    if (!data) return;
    setCardDetail(data.detail);
    setCardSchedules(data.schedules);
  }

  async function saveCardSchedule(event) {
    event.preventDefault();
    if (!selectedCard) return;
    const amount = toNumber(cardScheduleForm.amount);
    if (!amount || amount <= 0 || !cardScheduleForm.scheduledDate) return;
    const result = await run(
      () => scheduleApi.createCardSchedule(selectedCard.id, {
        scheduledDate: cardScheduleForm.scheduledDate,
        amount
      }),
      '카드 결제 예약 저장에 실패했습니다.'
    );
    if (!result) return;
    setCardScheduleForm(emptyCardScheduleForm());
    await loadCardPaymentData(selectedCard.id);
  }

  async function updateCardSchedule(action, schedule, fallback, reloadLedger = false) {
    if (!selectedCard) return;
    const result = await run(() => action(schedule.id), fallback);
    if (!result) return;
    await loadCardPaymentData(selectedCard.id);
    if (reloadLedger) await reload();
  }

  return {
    cancelCardSchedule: (schedule) => updateCardSchedule(
      scheduleApi.cancelCardSchedule, schedule, '카드 결제 예약 취소에 실패했습니다.'),
    deleteRecurringRule,
    executeCardSchedule: (schedule) => updateCardSchedule(
      scheduleApi.executeCardSchedule, schedule, '카드 결제 실행에 실패했습니다.', true),
    generateRecurringDue,
    loadCardPaymentData,
    loadRecurringRules,
    rescheduleCardSchedule: (schedule) => updateCardSchedule(
      scheduleApi.rescheduleCardSchedule, schedule, '카드 결제 재예약에 실패했습니다.'),
    retryCardSchedule: (schedule) => updateCardSchedule(
      scheduleApi.retryCardSchedule, schedule, '카드 결제 재시도에 실패했습니다.', true),
    saveCardSchedule,
    saveRecurringRule
  };
}
