import { errorMessage } from '../api/http';
import * as ledgerApi from '../api/ledgerApi';
import * as scheduleApi from '../api/scheduleApi';
import { toNumber } from '../utils/numberValues';

export function useTransactionMutations({
  form,
  editingTransaction,
  editingInstallmentGroup,
  receiptFiles,
  installmentReceiptTargetIndex,
  selectedTransaction,
  selectedInstallment,
  setTransactionReceipts,
  setForm,
  setEntryExpression,
  setReceiptFiles,
  setInstallmentReceiptTargetIndex,
  setEditingTransaction,
  setEditingInstallmentGroup,
  emptyTransactionForm,
  closePanel,
  reload,
  transactionLabel
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

  async function deleteReceipt(receipt) {
    if (!selectedTransaction) return;
    if (!window.confirm(`${receipt.originalFilename || '영수증'} 파일을 삭제할까요?`)) return;
    const result = await run(
      () => ledgerApi.deleteTransactionReceipt(selectedTransaction.id, receipt.id),
      '영수증 삭제에 실패했습니다.'
    );
    if (!result) return;
    const receipts = await run(
      () => ledgerApi.getTransactionReceipts(selectedTransaction.id),
      '영수증 목록을 다시 불러오지 못했습니다.'
    );
    if (receipts) setTransactionReceipts(receipts);
  }

  async function submitTransaction(event) {
    event.preventDefault();
    const amount = toNumber(form.amount);
    if (!amount) return;
    const payload = {
      ...form,
      amount,
      categoryId: form.categoryId ? toNumber(form.categoryId) : null,
      assetId: form.assetId ? toNumber(form.assetId) : null,
      fromAssetId: form.fromAssetId ? toNumber(form.fromAssetId) : null,
      toAssetId: form.toAssetId ? toNumber(form.toAssetId) : null,
      consumerMemberId: form.consumerMemberId ? toNumber(form.consumerMemberId) : null,
      installmentMonths: toNumber(form.installmentMonths)
    };
    const created = await run(
      () => ledgerApi.saveTransaction({
        transactionId: editingTransaction?.id,
        installmentGroupId: editingInstallmentGroup,
        payload
      }),
      '거래 저장에 실패했습니다.'
    );
    if (!created) return;
    if (!await attachReceipts(created)) return;
    if (!await createTransferFee()) return;
    resetEntry();
    closePanel();
    await reload();
  }

  async function attachReceipts(created) {
    if (!receiptFiles.length) return true;
    const updatedInstallments = Array.isArray(created) ? created : [];
    const requestedTargetIndex = Math.min(
      Math.max(toNumber(installmentReceiptTargetIndex, 1), 1),
      Math.max(toNumber(form.installmentMonths, 1), 1)
    );
    const receiptTransactionId = editingInstallmentGroup
      ? updatedInstallments.find((item) => toNumber(item.installmentIndex) === requestedTargetIndex)?.id
        || updatedInstallments.at(-1)?.id
      : created.id || editingTransaction?.id;
    if (!receiptTransactionId) {
      window.alert('영수증을 첨부할 거래를 찾지 못했습니다.');
      return false;
    }
    return Boolean(await run(
      () => ledgerApi.uploadTransactionReceipts(receiptTransactionId, receiptFiles),
      '영수증 일괄 첨부에 실패했습니다. 파일 형식과 개수를 확인해 주세요.'
    ));
  }

  async function createTransferFee() {
    const fee = toNumber(form.fee);
    if (editingTransaction || editingInstallmentGroup
      || form.type !== 'TRANSFER' || fee <= 0 || !form.fromAssetId) return true;
    return Boolean(await run(
      () => ledgerApi.createTransaction({
        type: 'EXPENSE',
        transactionDate: form.transactionDate,
        amount: fee,
        assetId: toNumber(form.fromAssetId),
        title: '이체 수수료',
        memo: form.memo || ''
      }),
      '이체 수수료 거래 생성에 실패했습니다.'
    ));
  }

  async function deleteTransaction(transaction) {
    const label = transactionLabel(transaction);
    if (!window.confirm(`${label} 거래를 삭제할까요? 삭제하면 자산 잔액도 다시 계산됩니다.`)) return;
    const result = await run(
      () => ledgerApi.deleteTransaction(transaction.id),
      '거래 삭제에 실패했습니다.'
    );
    if (!result) return;
    closePanel();
    await reload();
  }

  async function deleteInstallmentGroup() {
    if (!selectedInstallment?.installmentGroupId) return;
    const label = selectedInstallment.title || selectedInstallment.categoryName || '할부 거래';
    if (!window.confirm(`${label} 할부 전체를 삭제할까요? 모든 회차의 자산 잔액도 다시 계산됩니다.`)) return;
    const result = await run(
      () => scheduleApi.deleteInstallmentGroup(selectedInstallment.installmentGroupId),
      '할부 거래 삭제에 실패했습니다.'
    );
    if (!result) return;
    closePanel();
    await reload();
  }

  function resetEntry() {
    setForm(emptyTransactionForm());
    setEntryExpression('');
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
  }

  return {
    deleteInstallmentGroup,
    deleteReceipt,
    deleteTransaction,
    submitTransaction
  };
}
