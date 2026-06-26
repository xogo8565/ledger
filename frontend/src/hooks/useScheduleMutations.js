import { errorMessage } from '../api/http';
import * as scheduleApi from '../api/scheduleApi';

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
    const amount = Number(recurringForm.amount || 0);
    if (!amount || amount <= 0 || !recurringForm.startDate || !recurringForm.nextRunDate) return;
    const payload = {
      ...recurringForm,
      amount,
      categoryId: recurringForm.categoryId ? Number(recurringForm.categoryId) : null,
      assetId: recurringForm.assetId ? Number(recurringForm.assetId) : null,
      fromAssetId: recurringForm.fromAssetId ? Number(recurringForm.fromAssetId) : null,
      toAssetId: recurringForm.toAssetId ? Number(recurringForm.toAssetId) : null,
      intervalValue: Number(recurringForm.intervalValue || 1),
      installmentMonths: Number(recurringForm.installmentMonths || 0),
      endDate: recurringForm.endDate || null,
      nextRunDate: recurringForm.nextRunDate || recurringForm.startDate
    };
    const savedRule = await run(
      () => scheduleApi.saveRecurringRule(editingRecurringRule?.id, payload),
      '반복 거래 저장에 실패했습니다.'
    );
    if (!savedRule) return;
    setRecurringRules((current) => {
      const next = editingRecurringRule
        ? current.map((rule) => rule.id === savedRule.id ? savedRule : rule)
        : [...current.filter((rule) => rule.id !== savedRule.id), savedRule];
      return next.sort((a, b) => a.nextRunDate.localeCompare(b.nextRunDate) || Number(a.id) - Number(b.id));
    });
    setEditingRecurringRule(null);
    setRecurringForm(emptyRecurringForm());
  }

  async function deleteRecurringRule(rule) {
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
    const amount = Number(cardScheduleForm.amount || 0);
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
