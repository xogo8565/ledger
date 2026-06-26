import * as managementApi from '../api/managementApi';
import { errorMessage } from '../api/http';

const memberErrorTranslations = {
  'Member is used by an asset': '이 명의를 사용하는 자산이 있습니다. 자산 명의를 먼저 변경해 주세요.',
  'Member is used by a personal expense': '이 명의를 사용하는 개인 지출이 있습니다.',
  'Owner member cannot be deleted': '기본 명의는 삭제할 수 없습니다.',
  'Member name already exists': '같은 이름의 명의가 이미 있습니다.'
};

export function useManagementMutations({
  assetForm,
  editingAsset,
  categoryForm,
  categoryType,
  editingCategory,
  setEditingCategory,
  setCategoryForm,
  consumerMigration,
  setConsumerMigration,
  memberForm,
  editingMember,
  setEditingMember,
  setMemberForm,
  budgetSettings,
  setBudgetSettings,
  reload,
  reloadMembers,
  closePanel,
  emptyCategoryForm
}) {
  async function run(action, fallback, translations) {
    try {
      return await action();
    } catch (error) {
      console.error(error);
      window.alert(errorMessage(error, fallback, translations));
      return null;
    }
  }

  async function saveAsset(event) {
    event.preventDefault();
    const payload = {
      ...assetForm,
      balance: Number(assetForm.balance || 0),
      paymentAccountId: assetForm.paymentAccountId ? Number(assetForm.paymentAccountId) : null,
      statementClosingDay: Number(assetForm.statementClosingDay || 1),
      paymentDay: Number(assetForm.paymentDay || 1),
      autoPayment: Boolean(assetForm.autoPayment)
    };
    const result = await run(
      () => managementApi.saveAsset({
        assetId: editingAsset?.id,
        isCard: assetForm.type === 'CARD',
        payload
      }),
      '자산 저장에 실패했습니다.'
    );
    if (!result) return;
    closePanel();
    await reload();
  }

  async function deleteAsset(asset) {
    const result = await run(
      () => managementApi.deleteAsset(asset.id),
      '자산 삭제에 실패했습니다.'
    );
    if (result) await reload();
  }

  async function saveCategory(event) {
    event.preventDefault();
    const result = await run(
      () => managementApi.saveCategory(editingCategory?.id, { ...categoryForm, type: categoryType }),
      '카테고리 저장에 실패했습니다.'
    );
    if (!result) return;
    setEditingCategory(null);
    setCategoryForm(emptyCategoryForm(categoryType));
    await reload();
  }

  async function deleteCategory(category) {
    const result = await run(
      () => managementApi.deleteCategory(category.id),
      '카테고리 삭제에 실패했습니다.'
    );
    if (result) await reload();
  }

  async function loadConsumerMigration() {
    const result = await managementApi.getConsumerMigration();
    if (result.ok) setConsumerMigration(result.data);
  }

  async function migrateUnassignedPersonalExpenses() {
    if (!consumerMigration?.eligibleCount) return;
    const confirmed = window.confirm(
      `명의가 없는 개인 지출 ${consumerMigration.eligibleCount}건을 ${consumerMigration.ownerMemberName} 명의로 연결할까요?`
    );
    if (!confirmed) return;
    const result = await managementApi.migrateConsumers();
    if (!result.ok) {
      window.alert(errorMessage({ data: result.data }, '기존 개인 지출 명의 연결에 실패했습니다.'));
      return;
    }
    window.alert(`${result.data.migratedCount}건을 ${result.data.ownerMemberName} 명의로 연결했습니다.`);
    await Promise.all([loadConsumerMigration(), reload()]);
  }

  async function saveMember(event) {
    event.preventDefault();
    const name = memberForm.name.trim();
    if (!name) return;
    const result = await managementApi.saveMember(editingMember?.id, name);
    if (!result.ok) {
      window.alert(errorMessage(
        { data: result.data },
        '명의 저장에 실패했습니다.',
        memberErrorTranslations
      ));
      return;
    }
    setEditingMember(null);
    setMemberForm({ name: '' });
    await Promise.all([reloadMembers(), reload()]);
  }

  async function deleteMember(member) {
    if (!window.confirm(`${member.name} 명의를 삭제할까요?`)) return;
    const result = await managementApi.deleteMember(member.id);
    if (!result.ok) {
      window.alert(errorMessage(
        { data: result.data },
        '명의 삭제에 실패했습니다.',
        memberErrorTranslations
      ));
      return;
    }
    await reloadMembers();
  }

  async function saveBudget(event) {
    event.preventDefault();
    if (!budgetSettings) return;
    const result = await run(
      () => managementApi.saveBudgetSettings({
        month: budgetSettings.month,
        totalAmount: Number(budgetSettings.totalAmount || 0),
        categories: budgetSettings.categories.map((item) => ({
          categoryId: item.categoryId,
          amount: Number(item.amount || 0)
        }))
      }),
      '예산 저장에 실패했습니다.'
    );
    if (!result) return;
    closePanel();
    await reload();
  }

  async function copyPreviousBudget() {
    if (!budgetSettings?.month) return;
    const result = await managementApi.copyPreviousBudget(budgetSettings.month);
    if (!result.ok) {
      window.alert(errorMessage({ data: result.data }, '전월 예산을 찾을 수 없습니다.'));
      return;
    }
    setBudgetSettings(result.data);
    await reload();
  }

  return {
    copyPreviousBudget,
    deleteAsset,
    deleteCategory,
    deleteMember,
    loadConsumerMigration,
    migrateUnassignedPersonalExpenses,
    saveAsset,
    saveBudget,
    saveCategory,
    saveMember
  };
}
