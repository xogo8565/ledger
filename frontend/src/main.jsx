import React, { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import * as ledgerApi from './api/ledgerApi';
import * as managementApi from './api/managementApi';
import * as scheduleApi from './api/scheduleApi';
import { AppShell } from './components/AppShell';
import { useLedgerData } from './hooks/useLedgerData';
import { useManagementMutations } from './hooks/useManagementMutations';
import { useScheduleMutations } from './hooks/useScheduleMutations';
import { useTransactionMutations } from './hooks/useTransactionMutations';
import {
  assetTypeLabels,
  AssetFormScreen,
  AssetsScreen,
  CardPaymentManagerScreen
} from './screens/AssetsScreen';
import {
  emptyLedgerFilters,
  filteredPeriodLabel,
  LedgerScreen
} from './screens/LedgerScreen';
import { BudgetSettingsScreen } from './screens/BudgetSettingsScreen';
import { CategoryManagerScreen, MemberManagerScreen, MoreScreen } from './screens/MoreScreens';
import {
  emptyRecurringForm,
  InstallmentScheduleScreen,
  RecurringManagerScreen
} from './screens/ScheduleScreens';
import { StatsScreen } from './screens/StatsScreen';
import { EntryChoiceSheet, EntryScreen, ManualTextImportScreen, ReceiptOcrScreen, TransactionDetailScreen } from './screens/TransactionScreens';
import { csvCell, downloadTextFile, filteredFileLabel } from './utils/csv';
import { formatDate } from './utils/format';

const today = formatDate(new Date());
const currentMonth = today.slice(0, 7);
const typeLabels = {
  INCOME: '수입',
  EXPENSE: '지출',
  TRANSFER: '이체'
};

const consumptionScopeLabels = {
  PERSONAL: '개인',
  SHARED: '공동'
};

function isLikelyIosBrowser() {
  if (typeof navigator === 'undefined') return false;
  const userAgent = navigator.userAgent || '';
  const platform = navigator.platform || '';
  const touchPoints = navigator.maxTouchPoints || 0;
  return /iPad|iPhone|iPod/.test(userAgent) || (platform === 'MacIntel' && touchPoints > 1);
}

function App() {
  const [mainTab, setMainTab] = useState('ledger');
  const [ledgerMode, setLedgerMode] = useState('daily');
  const [ledgerFilters, setLedgerFilters] = useState(emptyLedgerFilters());
  const [statsMode, setStatsMode] = useState('stats');
  const [statsPeriod, setStatsPeriod] = useState('monthly');
  const [budgetPeriod, setBudgetPeriod] = useState('monthly');
  const [statsBreakdown, setStatsBreakdown] = useState('category');
  const [month, setMonth] = useState(currentMonth);
  const [statsRange, setStatsRange] = useState({ startDate: `${currentMonth}-01`, endDate: today });
  const [panel, setPanel] = useState(null);
  const [editingAsset, setEditingAsset] = useState(null);
  const [editingCategory, setEditingCategory] = useState(null);
  const [editingTransaction, setEditingTransaction] = useState(null);
  const [editingInstallmentGroup, setEditingInstallmentGroup] = useState(null);
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [transactionReceipts, setTransactionReceipts] = useState([]);
  const [entryExpression, setEntryExpression] = useState('');
  const [form, setForm] = useState(emptyTransactionForm());
  const [assetForm, setAssetForm] = useState(emptyAssetForm());
  const [categoryForm, setCategoryForm] = useState(emptyCategoryForm());
  const [categoryType, setCategoryType] = useState('EXPENSE');
  const [memberForm, setMemberForm] = useState({ name: '' });
  const [editingMember, setEditingMember] = useState(null);
  const [consumerMigration, setConsumerMigration] = useState(null);
  const [budgetSettings, setBudgetSettings] = useState(null);
  const [recurringRules, setRecurringRules] = useState([]);
  const [recurringForm, setRecurringForm] = useState(emptyRecurringForm());
  const [editingRecurringRule, setEditingRecurringRule] = useState(null);
  const [selectedCard, setSelectedCard] = useState(null);
  const [cardDetail, setCardDetail] = useState(null);
  const [cardSchedules, setCardSchedules] = useState([]);
  const [cardScheduleForm, setCardScheduleForm] = useState(emptyCardScheduleForm());
  const [installmentSchedule, setInstallmentSchedule] = useState([]);
  const [selectedInstallment, setSelectedInstallment] = useState(null);
  const [receiptFiles, setReceiptFiles] = useState([]);
  const [installmentReceiptTargetIndex, setInstallmentReceiptTargetIndex] = useState(1);
  const {
    data,
    loading,
    members,
    rangeSummary,
    reload: load,
    reloadMembers: loadMembers,
    searchResult,
    searchTransactions,
    yearlyBudgetSummary,
    yearlySummary
  } = useLedgerData({ month, ledgerFilters, statsRange });
  const {
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
  } = useManagementMutations({
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
    reload: load,
    reloadMembers: loadMembers,
    closePanel,
    emptyCategoryForm
  });
  const {
    cancelCardSchedule,
    deleteRecurringRule,
    executeCardSchedule,
    generateRecurringDue,
    loadCardPaymentData,
    loadRecurringRules,
    rescheduleCardSchedule,
    retryCardSchedule,
    saveCardSchedule,
    saveRecurringRule
  } = useScheduleMutations({
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
    reload: load
  });
  const {
    deleteInstallmentGroup,
    deleteReceipt,
    deleteTransaction,
    submitTransaction
  } = useTransactionMutations({
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
    reload: load,
    transactionLabel: (transaction) => (
      transaction.title || transaction.categoryName || typeLabels[transaction.type] || '거래'
    )
  });

  async function openBudgetSettings() {
    setBudgetSettings(await managementApi.getBudgetSettings(month));
    setPanel('budget');
  }

  function openEntry(type = 'EXPENSE') {
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    const nextForm = {
      ...emptyTransactionForm(),
      type,
      consumerMemberId: type === 'EXPENSE' ? defaultConsumerMemberId(members) : ''
    };
    setForm(nextForm);
    setEntryExpression('');
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setPanel('entry');
  }

  function openEntryChoice() {
    setPanel('entryChoice');
  }

  function openReceiptOcr() {
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setPanel('receiptOcr');
  }

  function openManualTextImport() {
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setPanel('textImport');
  }

  async function openTransactionDetail(transaction) {
    setSelectedTransaction(transaction);
    setTransactionReceipts(await ledgerApi.getTransactionReceipts(transaction.id));
    setPanel('transactionDetail');
  }

  function editTransaction(transaction) {
    setEditingTransaction(transaction);
    setEditingInstallmentGroup(null);
    setSelectedTransaction(null);
    setTransactionReceipts([]);
    setForm(transactionToForm(transaction));
    setEntryExpression(String(Number(transaction.amount || 0) || ''));
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setPanel('entry');
  }

  function openAssetForm(asset = null) {
    setEditingAsset(asset);
    setAssetForm(asset ? {
      type: asset.type,
      groupName: asset.groupName || assetTypeLabels[asset.type] || '기타',
      ownerName: asset.ownerName || '',
      name: asset.name || '',
      balance: String(Number(asset.balance || 0)),
      memo: asset.memo || '',
      paymentAccountId: asset.card?.paymentAccountId ? String(asset.card.paymentAccountId) : '',
      statementClosingDay: asset.card?.statementClosingDay || 1,
      paymentDay: asset.card?.paymentDay || 1,
      autoPayment: asset.card?.autoPayment ?? true
    } : emptyAssetForm());
    setPanel('assetForm');
  }

  function openCategoryManager() {
    setEditingCategory(null);
    setCategoryForm(emptyCategoryForm(categoryType));
    setPanel('categories');
  }

  async function openMemberManager() {
    setEditingMember(null);
    setMemberForm({ name: '' });
    await Promise.all([loadMembers(), loadConsumerMigration()]);
    setPanel('members');
  }

  async function openRecurringManager() {
    setEditingRecurringRule(null);
    setRecurringForm(emptyRecurringForm());
    await loadRecurringRules();
    setPanel('recurring');
  }

  async function openCardPaymentManager(cardAsset) {
    setSelectedCard(cardAsset);
    setCardScheduleForm(emptyCardScheduleForm());
    await loadCardPaymentData(cardAsset.id);
    setPanel('cardPayments');
  }

  async function openInstallmentSchedule(transaction) {
    if (!transaction.installmentGroupId) return;
    setSelectedInstallment(transaction);
    setInstallmentSchedule(await scheduleApi.getInstallmentSchedule(transaction.installmentGroupId));
    setPanel('installments');
  }

  function editInstallmentGroup() {
    if (!selectedInstallment?.installmentGroupId || !installmentSchedule.length) return;
    const first = installmentSchedule[0];
    const total = installmentSchedule.reduce((sum, item) => sum + Number(item.amount || 0), 0);
    const nextForm = {
      ...transactionToForm(first),
      transactionDate: first.transactionDate,
      amount: total,
      installmentMonths: installmentSchedule.length
    };
    setEditingTransaction(null);
    setEditingInstallmentGroup(selectedInstallment.installmentGroupId);
    setInstallmentReceiptTargetIndex(selectedInstallment.installmentIndex || 1);
    setForm(nextForm);
    setEntryExpression(String(total || ''));
    setReceiptFiles([]);
    setPanel('entry');
  }

  function closePanel() {
    setPanel(null);
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setSelectedTransaction(null);
    setEditingAsset(null);
    setEditingCategory(null);
    setEditingMember(null);
    setEditingRecurringRule(null);
    setSelectedCard(null);
    setCardDetail(null);
    setCardSchedules([]);
    setSelectedInstallment(null);
    setInstallmentSchedule([]);
    setInstallmentReceiptTargetIndex(1);
  }

  function updateForm(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function openLedgerCategory(category) {
    const categoryId = category?.categoryId || category?.id;
    setLedgerFilters({
      ...emptyLedgerFilters(),
      type: 'EXPENSE',
      categoryId: categoryId ? String(categoryId) : ''
    });
    setLedgerMode('daily');
    setMainTab('ledger');
  }

  function openLedgerTag(tag) {
    setLedgerFilters({
      ...emptyLedgerFilters(),
      query: tag?.tagName || '',
      type: 'EXPENSE'
    });
    setLedgerMode('daily');
    setMainTab('ledger');
  }

  function openLedgerScope(scopeSpend) {
    setLedgerFilters({
      ...emptyLedgerFilters(),
      type: 'EXPENSE',
      consumptionScope: scopeSpend?.scope || ''
    });
    setLedgerMode('daily');
    setMainTab('ledger');
  }

  function openLedgerMember(memberSpend) {
    setLedgerFilters({
      ...emptyLedgerFilters(),
      type: 'EXPENSE',
      consumptionScope: 'PERSONAL',
      consumerMemberId: memberSpend?.memberId ? String(memberSpend.memberId) : ''
    });
    setLedgerMode('daily');
    setMainTab('ledger');
  }

  async function exportMonthlyTransactions() {
    const response = await ledgerApi.exportTransactions(month);
    if (!response.ok) {
      window.alert('내보내기에 실패했습니다.');
      return;
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ledger-transactions-${month}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  }

  function exportFilteredTransactions(transactions, filters, searchResult = null) {
    const assetNames = new Map(data.assets.map((asset) => [String(asset.id), asset.name]));
    const period = filteredPeriodLabel(filters, month);
    const rows = [...transactions]
      .sort((a, b) => a.transactionDate.localeCompare(b.transactionDate) || Number(a.id) - Number(b.id))
      .map((item) => [
        period,
        item.transactionDate,
        typeLabels[item.type] || item.type,
        Number(item.amount || 0),
        item.categoryName || '',
        item.spendingTag || '',
        consumptionScopeLabels[item.consumptionScope] || '',
        item.consumerMemberName || '',
        item.assetName || '',
        assetNames.get(String(item.fromAssetId)) || '',
        assetNames.get(String(item.toAssetId)) || '',
        item.title || '',
        item.memo || '',
        item.installmentIndex || '',
        item.installmentMonths || ''
      ]);
    const csv = [
      ['기간', '거래일', '유형', '금액', '카테고리', '소비태그', '소비구분', '소비명의', '자산', '출금자산', '입금자산', '제목', '메모', '할부회차', '할부개월'],
      ...rows
    ].map((row) => row.map(csvCell).join(',')).join('\r\n');
    const pageLabel = searchResult ? `-page-${Number(searchResult.page || 0) + 1}-of-${Math.max(Number(searchResult.totalPages || 1), 1)}` : '';
    downloadTextFile(`ledger-transactions-${filteredFileLabel(filters, month)}${pageLabel}.csv`, `\uFEFF${csv}`, 'text/csv;charset=utf-8');
  }

  function editRecurringRule(rule) {
    setEditingRecurringRule(rule);
    setRecurringForm({
      type: rule.type,
      amount: String(Number(rule.amount || 0)),
      categoryId: rule.categoryId || '',
      assetId: rule.assetId || '',
      fromAssetId: rule.fromAssetId || '',
      toAssetId: rule.toAssetId || '',
      title: rule.title || '',
      memo: rule.memo || '',
      installmentMonths: rule.installmentMonths || 0,
      frequency: rule.frequency || 'MONTHLY',
      intervalValue: rule.intervalValue || 1,
      startDate: rule.startDate || today,
      endDate: rule.endDate || '',
      nextRunDate: rule.nextRunDate || rule.startDate || today
    });
  }

  async function openClipboardEntry() {
    if (isLikelyIosBrowser() || !navigator.clipboard?.readText) {
      openManualTextImport();
      return;
    }
    let clipboardText = '';
    try {
      clipboardText = await navigator.clipboard.readText();
    } catch (error) {
      console.error(error);
      openManualTextImport();
      return;
    }
    if (!clipboardText.trim()) {
      openManualTextImport();
      return;
    }
    const result = await ledgerApi.parseTransactionText(clipboardText);
    if (!result.ok) {
      window.alert('클립보드 문자 분석에 실패했습니다.');
      return;
    }
    const parsed = result.data;
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setForm({
      ...emptyTransactionForm(),
      type: parsed.type || 'EXPENSE',
      transactionDate: parsed.transactionDate || today,
      amount: parsed.amount || '',
      categoryId: parsed.recommendedCategoryId ? String(parsed.recommendedCategoryId) : '',
      title: parsed.merchant || '',
      memo: parsed.memo || '',
      consumerMemberId: parsed.type === 'EXPENSE' || !parsed.type ? defaultConsumerMemberId(members) : ''
    });
    setEntryExpression(String(parsed.amount || ''));
    setReceiptFiles([]);
    setPanel('entry');
  }

  function applyTextImportPreview(parsed) {
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setForm({
      ...emptyTransactionForm(),
      type: parsed.type || 'EXPENSE',
      transactionDate: parsed.transactionDate || today,
      amount: parsed.amount || '',
      categoryId: parsed.recommendedCategoryId ? String(parsed.recommendedCategoryId) : '',
      title: parsed.merchant || '',
      memo: parsed.memo || '',
      consumerMemberId: parsed.type === 'EXPENSE' || !parsed.type ? defaultConsumerMemberId(members) : ''
    });
    setEntryExpression(String(parsed.amount || ''));
    setReceiptFiles([]);
    setPanel('entry');
  }

  function applyReceiptOcrPreview(result, file) {
    const preview = result?.preview || {};
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setForm({
      ...emptyTransactionForm(),
      type: preview.type || 'EXPENSE',
      transactionDate: preview.transactionDate || today,
      amount: preview.amount ? String(Number(preview.amount || 0)) : '',
      title: preview.merchant || '',
      memo: preview.memo || result?.rawText || '',
      categoryId: preview.recommendedCategoryId ? String(preview.recommendedCategoryId) : '',
      consumptionScope: 'PERSONAL',
      consumerMemberId: defaultConsumerMemberId(members)
    });
    setEntryExpression(preview.amount ? String(Number(preview.amount || 0)) : '');
    setReceiptFiles(file ? [file] : []);
    setInstallmentReceiptTargetIndex(1);
    setPanel('entry');
  }

  const expenseCategories = data.categories.filter((category) => category.type === 'EXPENSE');
  const incomeCategories = data.categories.filter((category) => category.type === 'INCOME');
  const selectedCategories = form.type === 'INCOME' ? incomeCategories : expenseCategories;

  const categoryByName = useMemo(() => new Map(data.categories.map((category) => [category.name, category])), [data.categories]);
  const screenProps = {
    data,
    month,
    setMonth,
    loading,
    categoryByName
  };

  const mainContent = (
    <>
        {mainTab === 'ledger' && (
          <LedgerScreen
            {...screenProps}
            ledgerMode={ledgerMode}
            setLedgerMode={setLedgerMode}
            filters={ledgerFilters}
            setFilters={setLedgerFilters}
            searchResult={searchResult}
            searchTransactions={searchTransactions}
            exportMonthlyTransactions={exportMonthlyTransactions}
            exportFilteredTransactions={exportFilteredTransactions}
            openInstallmentSchedule={openInstallmentSchedule}
            openTransactionDetail={openTransactionDetail}
            members={members}
          />
        )}
        {mainTab === 'stats' && (
          <StatsScreen
            {...screenProps}
            statsMode={statsMode}
            setStatsMode={setStatsMode}
            statsPeriod={statsPeriod}
            setStatsPeriod={setStatsPeriod}
            budgetPeriod={budgetPeriod}
            setBudgetPeriod={setBudgetPeriod}
            statsBreakdown={statsBreakdown}
            setStatsBreakdown={setStatsBreakdown}
            yearlySummary={yearlySummary}
            yearlyBudgetSummary={yearlyBudgetSummary}
            rangeSummary={rangeSummary}
            statsRange={statsRange}
            setStatsRange={setStatsRange}
            openBudgetSettings={openBudgetSettings}
            openLedgerCategory={openLedgerCategory}
            openLedgerTag={openLedgerTag}
            openLedgerScope={openLedgerScope}
            openLedgerMember={openLedgerMember}
          />
        )}
        {mainTab === 'assets' && (
          <AssetsScreen
            {...screenProps}
            openAssetForm={openAssetForm}
            openCardPaymentManager={openCardPaymentManager}
            deleteAsset={deleteAsset}
          />
        )}
        {mainTab === 'more' && (
          <MoreScreen
            exportMonthlyTransactions={exportMonthlyTransactions}
            openCategoryManager={openCategoryManager}
            openMemberManager={openMemberManager}
          />
        )}
    </>
  );

  return (
    <AppShell
      activeTab={mainTab}
      onTabChange={setMainTab}
      onAdd={openEntryChoice}
      showAddAction={mainTab === 'ledger' || mainTab === 'stats'}
      content={mainContent}
    >
        {panel === 'entryChoice' && (
          <EntryChoiceSheet
            openEntry={openEntry}
            openClipboardEntry={openClipboardEntry}
            openReceiptOcr={openReceiptOcr}
            onClose={closePanel}
          />
        )}
        {panel === 'entry' && (
          <EntryScreen
            form={form}
            expression={entryExpression}
            assets={data.assets}
            categories={selectedCategories}
            members={members}
            receiptFiles={receiptFiles}
            updateForm={updateForm}
            setReceiptFiles={setReceiptFiles}
            setExpression={setEntryExpression}
            submitTransaction={submitTransaction}
            editingTransaction={editingTransaction}
            editingInstallmentGroup={editingInstallmentGroup}
            installmentReceiptTargetIndex={installmentReceiptTargetIndex}
            setInstallmentReceiptTargetIndex={setInstallmentReceiptTargetIndex}
            onClose={closePanel}
          />
        )}
        {panel === 'receiptOcr' && (
          <ReceiptOcrScreen
            previewReceiptOcr={ledgerApi.previewReceiptOcr}
            parseTransactionText={ledgerApi.parseTransactionText}
            applyReceiptOcrPreview={applyReceiptOcrPreview}
            onClose={closePanel}
          />
        )}
        {panel === 'textImport' && (
          <ManualTextImportScreen
            parseTransactionText={ledgerApi.parseTransactionText}
            applyTextImportPreview={applyTextImportPreview}
            onClose={closePanel}
          />
        )}
        {panel === 'transactionDetail' && (
          <TransactionDetailScreen
            transaction={selectedTransaction}
            receipts={transactionReceipts}
            editTransaction={editTransaction}
            deleteTransaction={deleteTransaction}
            deleteReceipt={deleteReceipt}
            openInstallmentSchedule={openInstallmentSchedule}
            onClose={closePanel}
          />
        )}
        {panel === 'assetForm' && (
          <AssetFormScreen
            form={assetForm}
            setForm={setAssetForm}
            assets={data.assets}
            members={members}
            editingAsset={editingAsset}
            saveAsset={saveAsset}
            onClose={closePanel}
          />
        )}
        {panel === 'categories' && (
          <CategoryManagerScreen
            categories={data.categories}
            categoryType={categoryType}
            setCategoryType={(type) => {
              setCategoryType(type);
              setEditingCategory(null);
              setCategoryForm(emptyCategoryForm(type));
            }}
            form={categoryForm}
            setForm={setCategoryForm}
            editingCategory={editingCategory}
            setEditingCategory={setEditingCategory}
            resetCategoryForm={() => setCategoryForm(emptyCategoryForm(categoryType))}
            saveCategory={saveCategory}
            deleteCategory={deleteCategory}
            onClose={closePanel}
          />
        )}
        {panel === 'members' && (
          <MemberManagerScreen
            members={members}
            form={memberForm}
            setForm={setMemberForm}
            editingMember={editingMember}
            setEditingMember={setEditingMember}
            saveMember={saveMember}
            deleteMember={deleteMember}
            consumerMigration={consumerMigration}
            migrateUnassignedPersonalExpenses={migrateUnassignedPersonalExpenses}
            onClose={closePanel}
          />
        )}
        {panel === 'budget' && (
          <BudgetSettingsScreen
            settings={budgetSettings}
            setSettings={setBudgetSettings}
            month={month}
            setMonth={setMonth}
            saveBudget={saveBudget}
            copyPreviousBudget={copyPreviousBudget}
            onClose={closePanel}
          />
        )}
        {panel === 'recurring' && (
          <RecurringManagerScreen
            rules={recurringRules}
            form={recurringForm}
            setForm={setRecurringForm}
            editingRule={editingRecurringRule}
            clearEditingRule={() => setEditingRecurringRule(null)}
            assets={data.assets}
            categories={data.categories}
            saveRule={saveRecurringRule}
            deleteRule={deleteRecurringRule}
            editRule={editRecurringRule}
            generateDue={generateRecurringDue}
            onClose={closePanel}
          />
        )}
        {panel === 'cardPayments' && (
          <CardPaymentManagerScreen
            card={selectedCard}
            detail={cardDetail}
            schedules={cardSchedules}
            form={cardScheduleForm}
            setForm={setCardScheduleForm}
            saveSchedule={saveCardSchedule}
            executeSchedule={executeCardSchedule}
            retrySchedule={retryCardSchedule}
            rescheduleSchedule={rescheduleCardSchedule}
            cancelSchedule={cancelCardSchedule}
            onClose={closePanel}
          />
        )}
        {panel === 'installments' && (
          <InstallmentScheduleScreen
            transaction={selectedInstallment}
            schedule={installmentSchedule}
            editGroup={editInstallmentGroup}
            deleteGroup={deleteInstallmentGroup}
            onClose={closePanel}
          />
        )}
    </AppShell>
  );
}

function emptyTransactionForm() {
  return {
    type: 'EXPENSE',
    transactionDate: today,
    amount: '',
    fee: '',
    categoryId: '',
    assetId: '',
    fromAssetId: '',
    toAssetId: '',
    title: '',
    memo: '',
    spendingTag: '',
    consumptionScope: 'PERSONAL',
    consumerMemberId: '',
    installmentMonths: 0
  };
}

function defaultConsumerMemberId(members) {
  const member = members.find((item) => item.role === 'OWNER') || members[0];
  return member ? String(member.id) : '';
}

function transactionToForm(transaction) {
  return {
    type: transaction.type || 'EXPENSE',
    transactionDate: transaction.transactionDate || today,
    amount: String(Number(transaction.amount || 0) || ''),
    fee: '',
    categoryId: transaction.categoryId ? String(transaction.categoryId) : '',
    assetId: transaction.assetId ? String(transaction.assetId) : '',
    fromAssetId: transaction.fromAssetId ? String(transaction.fromAssetId) : '',
    toAssetId: transaction.toAssetId ? String(transaction.toAssetId) : '',
    title: transaction.title || '',
    memo: transaction.memo || '',
    spendingTag: transaction.spendingTag || '',
    consumptionScope: transaction.consumptionScope || 'PERSONAL',
    consumerMemberId: transaction.consumerMemberId ? String(transaction.consumerMemberId) : '',
    installmentMonths: Number(transaction.installmentMonths || 0)
  };
}

function emptyAssetForm() {
  return {
    type: 'CASH',
    groupName: '현금',
    ownerName: '',
    name: '',
    balance: '',
    memo: '',
    paymentAccountId: '',
    statementClosingDay: 1,
    paymentDay: 1,
    autoPayment: true
  };
}

function emptyCategoryForm(type = 'EXPENSE') {
  return {
    type,
    name: '',
    icon: type === 'INCOME' ? '💰' : '•',
    color: type === 'INCOME' ? '#2189ff' : '#ff625c'
  };
}

function emptyCardScheduleForm() {
  return {
    scheduledDate: today,
    amount: ''
  };
}

createRoot(document.getElementById('root')).render(<App />);
