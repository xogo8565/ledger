import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import { AppHeader, BackButton } from './components/ui';
import {
  assetTypeLabels,
  AssetFormScreen,
  AssetsScreen,
  CardPaymentManagerScreen
} from './screens/AssetsScreen';
import {
  emptyLedgerFilters,
  filteredPeriodLabel,
  hasLedgerFilters,
  LedgerScreen,
  MonthNav
} from './screens/LedgerScreen';
import { CategoryManagerScreen, MemberManagerScreen, MoreScreen } from './screens/MoreScreens';
import {
  emptyRecurringForm,
  InstallmentScheduleScreen,
  RecurringManagerScreen
} from './screens/ScheduleScreens';
import { StatsScreen } from './screens/StatsScreen';
import { EntryChoiceSheet, EntryScreen, TransactionDetailScreen } from './screens/TransactionScreens';
import { csvCell, downloadTextFile, filteredFileLabel } from './utils/csv';
import { formatDate } from './utils/format';

const API = '/api';
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

function App() {
  const [mainTab, setMainTab] = useState('ledger');
  const [ledgerMode, setLedgerMode] = useState('daily');
  const [ledgerFilters, setLedgerFilters] = useState(emptyLedgerFilters());
  const [statsMode, setStatsMode] = useState('stats');
  const [statsPeriod, setStatsPeriod] = useState('monthly');
  const [budgetPeriod, setBudgetPeriod] = useState('monthly');
  const [statsBreakdown, setStatsBreakdown] = useState('category');
  const [month, setMonth] = useState(currentMonth);
  const [data, setData] = useState({ assets: [], categories: [], transactions: [], summary: null, assetSummary: null });
  const [searchTransactions, setSearchTransactions] = useState(null);
  const [yearlySummary, setYearlySummary] = useState(null);
  const [yearlyBudgetSummary, setYearlyBudgetSummary] = useState(null);
  const [statsRange, setStatsRange] = useState({ startDate: `${currentMonth}-01`, endDate: today });
  const [rangeSummary, setRangeSummary] = useState(null);
  const [loading, setLoading] = useState(true);
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
  const [members, setMembers] = useState([]);
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

  async function load() {
    setLoading(true);
    const [bootstrapResponse, assetSummaryResponse, membersResponse] = await Promise.all([
      fetch(`${API}/bootstrap?month=${month}`),
      fetch(`${API}/assets/summary`),
      fetch(`${API}/members`)
    ]);
    const bootstrap = await bootstrapResponse.json();
    const assetSummary = await assetSummaryResponse.json();
    setMembers(await membersResponse.json());
    setData({ ...bootstrap, assetSummary });
    setLoading(false);
  }

  useEffect(() => {
    load().catch((error) => {
      console.error(error);
      setLoading(false);
    });
  }, [month]);

  useEffect(() => {
    Promise.all([loadYearlySummary(), loadYearlyBudgetSummary()]).catch((error) => console.error(error));
  }, [month]);

  useEffect(() => {
    loadRangeSummary().catch((error) => console.error(error));
  }, [statsRange.startDate, statsRange.endDate]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      loadSearchTransactions().catch((error) => console.error(error));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [ledgerFilters, month]);

  async function loadSearchTransactions() {
    if (!hasLedgerFilters(ledgerFilters)) {
      setSearchTransactions(null);
      return;
    }
    if (ledgerFilters.startDate && ledgerFilters.endDate && ledgerFilters.endDate < ledgerFilters.startDate) {
      setSearchTransactions([]);
      return;
    }
    if (ledgerFilters.minAmount && ledgerFilters.maxAmount
      && Number(ledgerFilters.maxAmount) < Number(ledgerFilters.minAmount)) {
      setSearchTransactions([]);
      return;
    }
    const params = new URLSearchParams();
    params.set('startDate', ledgerFilters.startDate || `${month}-01`);
    params.set('endDate', ledgerFilters.endDate || formatDate(new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)));
    if (ledgerFilters.query.trim()) params.set('query', ledgerFilters.query.trim());
    if (ledgerFilters.type !== 'ALL') params.set('type', ledgerFilters.type);
    ['categoryId', 'consumptionScope', 'consumerMemberId', 'assetId', 'minAmount', 'maxAmount'].forEach((key) => {
      if (ledgerFilters[key] !== '') params.set(key, ledgerFilters[key]);
    });
    const response = await fetch(`${API}/transactions/search?${params}`);
    setSearchTransactions(response.ok ? await response.json() : []);
  }

  async function loadYearlySummary() {
    const year = Number(month.slice(0, 4));
    const response = await fetch(`${API}/summary/yearly?year=${year}`);
    setYearlySummary(await response.json());
  }

  async function loadYearlyBudgetSummary() {
    const year = Number(month.slice(0, 4));
    const response = await fetch(`${API}/budgets/summary/yearly?year=${year}`);
    setYearlyBudgetSummary(await response.json());
  }

  async function loadRangeSummary() {
    if (!statsRange.startDate || !statsRange.endDate || statsRange.endDate < statsRange.startDate) {
      setRangeSummary(null);
      return;
    }
    const response = await fetch(`${API}/summary/range?startDate=${statsRange.startDate}&endDate=${statsRange.endDate}`);
    setRangeSummary(await response.json());
  }

  async function openBudgetSettings() {
    const response = await fetch(`${API}/budgets/settings?month=${month}`);
    setBudgetSettings(await response.json());
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

  async function openTransactionDetail(transaction) {
    setSelectedTransaction(transaction);
    const response = await fetch(`${API}/transactions/${transaction.id}/receipts`);
    setTransactionReceipts(await response.json());
    setPanel('transactionDetail');
  }

  async function deleteReceipt(receipt) {
    if (!selectedTransaction) return;
    const confirmed = window.confirm(`${receipt.originalFilename || '영수증'} 파일을 삭제할까요?`);
    if (!confirmed) return;
    await fetch(`${API}/transactions/${selectedTransaction.id}/receipts/${receipt.id}`, { method: 'DELETE' });
    const response = await fetch(`${API}/transactions/${selectedTransaction.id}/receipts`);
    setTransactionReceipts(await response.json());
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
    const response = await fetch(`${API}/transactions/installments/${transaction.installmentGroupId}`);
    setInstallmentSchedule(await response.json());
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

  async function deleteInstallmentGroup() {
    if (!selectedInstallment?.installmentGroupId) return;
    const title = selectedInstallment.title || selectedInstallment.categoryName || '할부 거래';
    const confirmed = window.confirm(`${title} 할부 전체를 삭제할까요? 모든 회차의 자산 잔액도 다시 계산됩니다.`);
    if (!confirmed) return;
    await fetch(`${API}/transactions/installments/${selectedInstallment.installmentGroupId}`, { method: 'DELETE' });
    closePanel();
    await load();
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
    const response = await fetch(`${API}/export/transactions.csv?month=${month}`);
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

  function exportFilteredTransactions(transactions, filters) {
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
    downloadTextFile(`ledger-transactions-${filteredFileLabel(filters, month)}.csv`, `\uFEFF${csv}`, 'text/csv;charset=utf-8');
  }

  async function submitTransaction(event) {
    event.preventDefault();
    const amount = Number(form.amount);
    if (!amount) return;

    const payload = {
      ...form,
      amount,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      assetId: form.assetId ? Number(form.assetId) : null,
      fromAssetId: form.fromAssetId ? Number(form.fromAssetId) : null,
      toAssetId: form.toAssetId ? Number(form.toAssetId) : null,
      consumerMemberId: form.consumerMemberId ? Number(form.consumerMemberId) : null,
      installmentMonths: Number(form.installmentMonths || 0)
    };

    const url = editingInstallmentGroup
      ? `${API}/transactions/installments/${editingInstallmentGroup}`
      : editingTransaction ? `${API}/transactions/${editingTransaction.id}` : `${API}/transactions`;
    const method = editingTransaction || editingInstallmentGroup ? 'PUT' : 'POST';
    const response = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const created = await response.json();

    if (receiptFiles.length) {
      const updatedInstallments = Array.isArray(created) ? created : [];
      const requestedTargetIndex = Math.min(
        Math.max(Number(installmentReceiptTargetIndex || 1), 1),
        Math.max(Number(form.installmentMonths || 1), 1)
      );
      const receiptTransactionId = editingInstallmentGroup
        ? updatedInstallments.find((item) => Number(item.installmentIndex) === requestedTargetIndex)?.id
          || updatedInstallments.at(-1)?.id
        : created.id || editingTransaction?.id;
      if (!receiptTransactionId) {
        window.alert('영수증을 첨부할 거래를 찾지 못했습니다.');
        return;
      }
      const upload = new FormData();
      receiptFiles.forEach((file) => upload.append('files', file));
      const uploadResponse = await fetch(`${API}/transactions/${receiptTransactionId}/receipts/batch`, { method: 'POST', body: upload });
      if (!uploadResponse.ok) {
        window.alert('영수증 일괄 첨부에 실패했습니다. 파일 형식과 개수를 확인해 주세요.');
        return;
      }
    }

    const fee = Number(form.fee || 0);
    if (!editingTransaction && !editingInstallmentGroup && form.type === 'TRANSFER' && fee > 0 && form.fromAssetId) {
      await fetch(`${API}/transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'EXPENSE',
          transactionDate: form.transactionDate,
          amount: fee,
          assetId: Number(form.fromAssetId),
          title: '이체 수수료',
          memo: form.memo || ''
        })
      });
    }

    setForm(emptyTransactionForm());
    setEntryExpression('');
    setReceiptFiles([]);
    setInstallmentReceiptTargetIndex(1);
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    closePanel();
    await load();
  }

  async function deleteTransaction(transaction) {
    const label = transaction.title || transaction.categoryName || typeLabels[transaction.type] || '거래';
    const confirmed = window.confirm(`${label} 거래를 삭제할까요? 삭제하면 자산 잔액도 다시 계산됩니다.`);
    if (!confirmed) return;
    await fetch(`${API}/transactions/${transaction.id}`, { method: 'DELETE' });
    closePanel();
    await load();
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
    const isCardAsset = assetForm.type === 'CARD';
    const url = editingAsset
      ? isCardAsset ? `${API}/assets/${editingAsset.id}/card` : `${API}/assets/${editingAsset.id}`
      : isCardAsset ? `${API}/assets/card` : `${API}/assets`;
    const method = editingAsset ? 'PUT' : 'POST';
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    closePanel();
    await load();
  }

  async function deleteAsset(asset) {
    await fetch(`${API}/assets/${asset.id}`, { method: 'DELETE' });
    await load();
  }

  async function saveCategory(event) {
    event.preventDefault();
    const payload = { ...categoryForm, type: categoryType };
    const url = editingCategory ? `${API}/categories/${editingCategory.id}` : `${API}/categories`;
    const method = editingCategory ? 'PUT' : 'POST';
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    setEditingCategory(null);
    setCategoryForm(emptyCategoryForm(categoryType));
    await load();
  }

  async function deleteCategory(category) {
    await fetch(`${API}/categories/${category.id}`, { method: 'DELETE' });
    await load();
  }

  async function loadMembers() {
    const response = await fetch(`${API}/members`);
    setMembers(await response.json());
  }

  async function loadConsumerMigration() {
    const response = await fetch(`${API}/members/consumer-migration`);
    if (!response.ok) return;
    setConsumerMigration(await response.json());
  }

  async function migrateUnassignedPersonalExpenses() {
    if (!consumerMigration?.eligibleCount) return;
    const confirmed = window.confirm(
      `명의가 없는 개인 지출 ${consumerMigration.eligibleCount}건을 ${consumerMigration.ownerMemberName} 명의로 연결할까요?`
    );
    if (!confirmed) return;
    const response = await fetch(`${API}/members/consumer-migration`, { method: 'POST' });
    if (!response.ok) {
      window.alert('기존 개인 지출 명의 연결에 실패했습니다.');
      return;
    }
    const result = await response.json();
    window.alert(`${result.migratedCount}건을 ${result.ownerMemberName} 명의로 연결했습니다.`);
    await Promise.all([loadConsumerMigration(), load()]);
  }

  async function saveMember(event) {
    event.preventDefault();
    const name = memberForm.name.trim();
    if (!name) return;
    const response = await fetch(editingMember ? `${API}/members/${editingMember.id}` : `${API}/members`, {
      method: editingMember ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      window.alert(error.error || '명의 저장에 실패했습니다.');
      return;
    }
    setEditingMember(null);
    setMemberForm({ name: '' });
    await Promise.all([loadMembers(), load()]);
  }

  async function deleteMember(member) {
    const confirmed = window.confirm(`${member.name} 명의를 삭제할까요?`);
    if (!confirmed) return;
    const response = await fetch(`${API}/members/${member.id}`, { method: 'DELETE' });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      window.alert(error.error === 'Member is used by an asset'
        ? '이 명의를 사용하는 자산이 있습니다. 자산 명의를 먼저 변경해 주세요.'
        : error.error || '명의 삭제에 실패했습니다.');
      return;
    }
    await loadMembers();
  }

  async function saveBudget(event) {
    event.preventDefault();
    if (!budgetSettings) return;
    await fetch(`${API}/budgets/settings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        month: budgetSettings.month,
        totalAmount: Number(budgetSettings.totalAmount || 0),
        categories: budgetSettings.categories.map((item) => ({
          categoryId: item.categoryId,
          amount: Number(item.amount || 0)
        }))
      })
    });
    closePanel();
    await load();
  }

  async function copyPreviousBudget() {
    if (!budgetSettings?.month) return;
    const response = await fetch(`${API}/budgets/settings/copy-previous?month=${budgetSettings.month}`, { method: 'POST' });
    if (!response.ok) {
      window.alert('전월 예산을 찾을 수 없습니다.');
      return;
    }
    setBudgetSettings(await response.json());
    await load();
  }

  async function loadRecurringRules() {
    const response = await fetch(`${API}/recurring-transactions`);
    setRecurringRules(await response.json());
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
    const url = editingRecurringRule ? `${API}/recurring-transactions/${editingRecurringRule.id}` : `${API}/recurring-transactions`;
    const method = editingRecurringRule ? 'PUT' : 'POST';
    const response = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const savedRule = await response.json();
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
    await fetch(`${API}/recurring-transactions/${rule.id}`, { method: 'DELETE' });
    await loadRecurringRules();
  }

  async function generateRecurringDue() {
    await fetch(`${API}/recurring-transactions/generate-due`, { method: 'POST' });
    await loadRecurringRules();
    await load();
  }

  async function loadCardPaymentData(cardAssetId) {
    const [detailResponse, schedulesResponse] = await Promise.all([
      fetch(`${API}/cards/${cardAssetId}`),
      fetch(`${API}/cards/${cardAssetId}/payment-schedules`)
    ]);
    setCardDetail(await detailResponse.json());
    setCardSchedules(await schedulesResponse.json());
  }

  async function saveCardSchedule(event) {
    event.preventDefault();
    if (!selectedCard) return;
    const amount = Number(cardScheduleForm.amount || 0);
    if (!amount || amount <= 0 || !cardScheduleForm.scheduledDate) return;

    await fetch(`${API}/cards/${selectedCard.id}/payment-schedules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        scheduledDate: cardScheduleForm.scheduledDate,
        amount
      })
    });
    setCardScheduleForm(emptyCardScheduleForm());
    await loadCardPaymentData(selectedCard.id);
  }

  async function executeCardSchedule(schedule) {
    if (!selectedCard) return;
    await fetch(`${API}/cards/payment-schedules/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scheduleId: schedule.id })
    });
    await loadCardPaymentData(selectedCard.id);
    await load();
  }

  async function retryCardSchedule(schedule) {
    if (!selectedCard) return;
    await fetch(`${API}/cards/payment-schedules/${schedule.id}/retry`, { method: 'POST' });
    await loadCardPaymentData(selectedCard.id);
    await load();
  }

  async function rescheduleCardSchedule(schedule) {
    if (!selectedCard) return;
    await fetch(`${API}/cards/payment-schedules/${schedule.id}/reschedule`, { method: 'POST' });
    await loadCardPaymentData(selectedCard.id);
  }

  async function cancelCardSchedule(schedule) {
    if (!selectedCard) return;
    await fetch(`${API}/cards/payment-schedules/${schedule.id}`, { method: 'DELETE' });
    await loadCardPaymentData(selectedCard.id);
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
    let clipboardText = '';
    try {
      clipboardText = await navigator.clipboard.readText();
    } catch (error) {
      console.error(error);
      window.alert('클립보드 내용을 읽을 수 없습니다. 브라우저의 클립보드 권한을 확인해 주세요.');
      return;
    }
    if (!clipboardText.trim()) {
      window.alert('클립보드에 분석할 문자 내용이 없습니다.');
      return;
    }
    const response = await fetch(`${API}/import/text/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawText: clipboardText })
    });
    if (!response.ok) {
      window.alert('클립보드 문자 분석에 실패했습니다.');
      return;
    }
    const parsed = await response.json();
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

  return (
    <main className="page-frame">
      <section className="phone-shell" aria-label="편한가계부 미리보기">
        {mainTab === 'ledger' && (
          <LedgerScreen
            {...screenProps}
            ledgerMode={ledgerMode}
            setLedgerMode={setLedgerMode}
            filters={ledgerFilters}
            setFilters={setLedgerFilters}
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
            openRecurringManager={openRecurringManager}
            openMemberManager={openMemberManager}
          />
        )}

        <BottomNav active={mainTab} onChange={setMainTab} />

        {(mainTab === 'ledger' || mainTab === 'stats') && (
          <div className="floating-actions">
            <button className="fab" type="button" onClick={openEntryChoice} aria-label="거래 추가">
              +
            </button>
          </div>
        )}

        {panel === 'entryChoice' && (
          <EntryChoiceSheet
            openEntry={openEntry}
            openClipboardEntry={openClipboardEntry}
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
      </section>
    </main>
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

function BudgetSettingsScreen({ settings, setSettings, month, setMonth, saveBudget, copyPreviousBudget, onClose }) {
  const categories = settings?.categories || [];

  return (
    <div className="full-panel">
      <form className="budget-settings-screen" onSubmit={saveBudget}>
        <AppHeader title="예산설정" left={<BackButton label="뒤로" onClick={onClose} />} />
        <MonthNav month={month} setMonth={setMonth} />
        <button className="budget-copy-button" type="button" onClick={copyPreviousBudget}>전월 예산 복사</button>
        <div className="budget-total-edit">
          <span>전체 예산</span>
          <input inputMode="numeric" value={settings?.totalAmount || 0} onChange={(event) => setSettings((prev) => ({ ...prev, totalAmount: event.target.value }))} />
        </div>
        <div className="budget-settings-list">
          {categories.map((item, index) => (
            <label key={item.categoryId}>
              <span>{item.categoryIcon || '•'} {item.categoryName}</span>
              <input inputMode="numeric" value={item.amount} onChange={(event) => {
                const amount = event.target.value;
                setSettings((prev) => ({
                  ...prev,
                  categories: prev.categories.map((category, categoryIndex) => categoryIndex === index ? { ...category, amount } : category)
                }));
              }} />
            </label>
          ))}
        </div>
        <button className="wide-save-button sticky" type="submit">저장</button>
      </form>
    </div>
  );
}

function BottomNav({ active, onChange }) {
  const tabs = [
    ['ledger', '▤', '6. 22.'],
    ['stats', '▥', '통계'],
    ['assets', '◎', '자산'],
    ['more', '···', '더보기']
  ];

  return (
    <nav className="bottom-nav" aria-label="하단 메뉴">
      {tabs.map(([key, icon, label]) => (
        <button key={key} type="button" className={active === key ? 'active' : ''} onClick={() => onChange(key)}>
          <span>{icon}</span>
          {label}
        </button>
      ))}
    </nav>
  );
}

createRoot(document.getElementById('root')).render(<App />);
