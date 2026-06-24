import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const API = '/api';
const today = formatDate(new Date());
const currentMonth = today.slice(0, 7);
const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const palette = ['#ff675f', '#ff914d', '#ffcc3d', '#bde837', '#6bd26b', '#5edbd1', '#58a9ff', '#f38cc9'];

const typeLabels = {
  INCOME: '수입',
  EXPENSE: '지출',
  TRANSFER: '이체'
};

const assetTypeLabels = {
  CASH: '현금',
  BANK: '은행',
  CARD: '카드',
  OTHER: '기타',
  DEBT: '부채'
};

const assetTypeOptions = [
  ['CASH', '현금'],
  ['BANK', '은행'],
  ['CARD', '카드'],
  ['OTHER', '기타'],
  ['DEBT', '부채']
];

function money(value) {
  return `${Number(value || 0).toLocaleString('ko-KR')}원`;
}

function numberOnly(value) {
  return Number(value || 0).toLocaleString('ko-KR');
}

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function monthLabel(month) {
  const [year, monthNumber] = month.split('-');
  return `${year}년 ${Number(monthNumber)}월`;
}

function dayMeta(date) {
  const parsed = new Date(`${date}T00:00:00`);
  return {
    day: parsed.getDate(),
    weekday: weekdays[parsed.getDay()]
  };
}

function shiftMonth(month, amount) {
  const parsed = new Date(`${month}-01T00:00:00`);
  parsed.setMonth(parsed.getMonth() + amount);
  return formatDate(parsed).slice(0, 7);
}

function sameMonth(date, month) {
  return date.slice(0, 7) === month;
}

function transactionTone(type) {
  if (type === 'INCOME') return 'income';
  if (type === 'TRANSFER') return 'transfer';
  return 'expense';
}

function App() {
  const [mainTab, setMainTab] = useState('ledger');
  const [ledgerMode, setLedgerMode] = useState('daily');
  const [ledgerFilters, setLedgerFilters] = useState(emptyLedgerFilters());
  const [statsMode, setStatsMode] = useState('stats');
  const [statsPeriod, setStatsPeriod] = useState('monthly');
  const [statsBreakdown, setStatsBreakdown] = useState('category');
  const [month, setMonth] = useState(currentMonth);
  const [data, setData] = useState({ assets: [], categories: [], transactions: [], summary: null });
  const [rangeTransactions, setRangeTransactions] = useState(null);
  const [yearlySummary, setYearlySummary] = useState(null);
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
  const [rawText, setRawText] = useState('');
  const [preview, setPreview] = useState(null);

  async function load() {
    setLoading(true);
    const response = await fetch(`${API}/bootstrap?month=${month}`);
    setData(await response.json());
    setLoading(false);
  }

  useEffect(() => {
    load().catch((error) => {
      console.error(error);
      setLoading(false);
    });
  }, [month]);

  useEffect(() => {
    loadYearlySummary().catch((error) => console.error(error));
  }, [month]);

  useEffect(() => {
    loadRangeTransactions().catch((error) => console.error(error));
  }, [ledgerFilters.startDate, ledgerFilters.endDate]);

  async function loadRangeTransactions() {
    if (!ledgerFilters.startDate || !ledgerFilters.endDate) {
      setRangeTransactions(null);
      return;
    }
    if (ledgerFilters.endDate < ledgerFilters.startDate) {
      setRangeTransactions([]);
      return;
    }
    const response = await fetch(`${API}/transactions/range?startDate=${ledgerFilters.startDate}&endDate=${ledgerFilters.endDate}`);
    setRangeTransactions(await response.json());
  }

  async function loadYearlySummary() {
    const year = Number(month.slice(0, 4));
    const response = await fetch(`${API}/summary/yearly?year=${year}`);
    setYearlySummary(await response.json());
  }

  async function openBudgetSettings() {
    const response = await fetch(`${API}/budgets/settings?month=${month}`);
    setBudgetSettings(await response.json());
    setPanel('budget');
  }

  function openEntry(type = 'EXPENSE') {
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    const nextForm = { ...emptyTransactionForm(), type };
    setForm(nextForm);
    setEntryExpression('');
    setReceiptFiles([]);
    setPanel('entry');
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
    setEditingRecurringRule(null);
    setSelectedCard(null);
    setCardDetail(null);
    setCardSchedules([]);
    setSelectedInstallment(null);
    setInstallmentSchedule([]);
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
        item.assetName || '',
        assetNames.get(String(item.fromAssetId)) || '',
        assetNames.get(String(item.toAssetId)) || '',
        item.title || '',
        item.memo || '',
        item.installmentIndex || '',
        item.installmentMonths || ''
      ]);
    const csv = [
      ['기간', '거래일', '유형', '금액', '카테고리', '소비태그', '자산', '출금자산', '입금자산', '제목', '메모', '할부회차', '할부개월'],
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

    if (receiptFiles.length && !editingInstallmentGroup) {
      const receiptTransactionId = created.id || editingTransaction?.id;
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
    await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    setEditingRecurringRule(null);
    setRecurringForm(emptyRecurringForm());
    await loadRecurringRules();
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

  async function parseText() {
    if (!rawText.trim()) return;
    const response = await fetch(`${API}/import/text/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawText })
    });
    const parsed = await response.json();
    setPreview(parsed);
  }

  function confirmTextImport() {
    if (!preview) return;
    setEditingTransaction(null);
    setEditingInstallmentGroup(null);
    setForm({
      ...emptyTransactionForm(),
      type: preview.type || 'EXPENSE',
      transactionDate: preview.transactionDate || today,
      amount: preview.amount || '',
      title: preview.merchant || '',
      memo: preview.memo || ''
    });
    setEntryExpression(String(preview.amount || ''));
    setReceiptFiles([]);
    setPanel('entry');
  }

  const expenseCategories = data.categories.filter((category) => category.type === 'EXPENSE');
  const incomeCategories = data.categories.filter((category) => category.type === 'INCOME');
  const selectedCategories = form.type === 'INCOME' ? incomeCategories : expenseCategories;

  const categoryByName = useMemo(() => new Map(data.categories.map((category) => [category.name, category])), [data.categories]);
  const spendMax = useMemo(() => {
    const spends = data.summary?.categorySpends || [];
    return Math.max(1, ...spends.map((item) => Number(item.amount)));
  }, [data.summary]);

  const screenProps = {
    data,
    month,
    setMonth,
    loading,
    categoryByName,
    spendMax
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
            rangeTransactions={rangeTransactions}
            exportMonthlyTransactions={exportMonthlyTransactions}
            exportFilteredTransactions={exportFilteredTransactions}
            openInstallmentSchedule={openInstallmentSchedule}
            openTransactionDetail={openTransactionDetail}
          />
        )}
        {mainTab === 'stats' && (
          <StatsScreen
            {...screenProps}
            statsMode={statsMode}
            setStatsMode={setStatsMode}
            statsPeriod={statsPeriod}
            setStatsPeriod={setStatsPeriod}
            statsBreakdown={statsBreakdown}
            setStatsBreakdown={setStatsBreakdown}
            yearlySummary={yearlySummary}
            openBudgetSettings={openBudgetSettings}
            openLedgerCategory={openLedgerCategory}
            openLedgerTag={openLedgerTag}
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
            rawText={rawText}
            setRawText={setRawText}
            preview={preview}
            parseText={parseText}
            confirmTextImport={confirmTextImport}
            exportMonthlyTransactions={exportMonthlyTransactions}
            openCategoryManager={openCategoryManager}
            openRecurringManager={openRecurringManager}
          />
        )}

        <BottomNav active={mainTab} onChange={setMainTab} />

        {(mainTab === 'ledger' || mainTab === 'stats') && (
          <div className="floating-actions">
            <button className="fab" type="button" onClick={() => openEntry('EXPENSE')} aria-label="거래 추가">
              +
            </button>
          </div>
        )}

        {panel === 'entry' && (
          <EntryScreen
            form={form}
            expression={entryExpression}
            assets={data.assets}
            categories={selectedCategories}
            receiptFiles={receiptFiles}
            updateForm={updateForm}
            setReceiptFiles={setReceiptFiles}
            setExpression={setEntryExpression}
            submitTransaction={submitTransaction}
            editingTransaction={editingTransaction}
            editingInstallmentGroup={editingInstallmentGroup}
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
            saveCategory={saveCategory}
            deleteCategory={deleteCategory}
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
    installmentMonths: 0
  };
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

function emptyRecurringForm() {
  return {
    type: 'EXPENSE',
    amount: '',
    categoryId: '',
    assetId: '',
    fromAssetId: '',
    toAssetId: '',
    title: '',
    memo: '',
    installmentMonths: 0,
    frequency: 'MONTHLY',
    intervalValue: 1,
    startDate: today,
    endDate: '',
    nextRunDate: today
  };
}

function emptyCardScheduleForm() {
  return {
    scheduledDate: today,
    amount: ''
  };
}

function emptyLedgerFilters() {
  return {
    query: '',
    type: 'ALL',
    categoryId: '',
    startDate: '',
    endDate: ''
  };
}

function filterTransactions(transactions, filters) {
  const query = (filters.query || '').trim().toLowerCase();
  return transactions.filter((item) => {
    if (filters.type !== 'ALL' && item.type !== filters.type) return false;
    if (filters.categoryId && String(item.categoryId || '') !== String(filters.categoryId)) return false;
    if (filters.startDate && item.transactionDate < filters.startDate) return false;
    if (filters.endDate && item.transactionDate > filters.endDate) return false;
    if (!query) return true;
    const haystack = [
      item.title,
      item.memo,
      item.spendingTag,
      item.categoryName,
      item.assetName,
      item.transactionDate,
      transferLabel(item)
    ].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(query);
  });
}

function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

function downloadTextFile(filename, contents, type) {
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

function filteredPeriodLabel(filters, month) {
  if (filters.startDate && filters.endDate) return `${filters.startDate} ~ ${filters.endDate}`;
  if (filters.startDate) return `${filters.startDate} 이후`;
  if (filters.endDate) return `${filters.endDate} 이전`;
  return month;
}

function filteredFileLabel(filters, month) {
  const period = filters.startDate || filters.endDate
    ? `${filters.startDate || 'start'}_${filters.endDate || 'end'}`
    : month;
  return `${period}-filtered`;
}

function AppHeader({ title, left, right }) {
  return (
    <header className="app-header">
      <div>{left}</div>
      <h1>{title}</h1>
      <div>{right}</div>
    </header>
  );
}

function BackButton({ label = '뒤로', onClick }) {
  return (
    <button className="back-button" type="button" onClick={onClick}>
      ‹ {label}
    </button>
  );
}

function IconButton({ label, children, onClick }) {
  return (
    <button className="top-icon-button" type="button" aria-label={label} onClick={onClick}>
      {children}
    </button>
  );
}

function LedgerScreen({ data, month, setMonth, ledgerMode, setLedgerMode, filters, setFilters, rangeTransactions, loading, exportFilteredTransactions, openInstallmentSchedule, openTransactionDetail }) {
  const summary = data.summary || {};
  const sourceTransactions = rangeTransactions || data.transactions || [];
  const filteredTransactions = useMemo(() => filterTransactions(sourceTransactions, filters), [sourceTransactions, filters]);
  const filterCategories = data.categories.filter((category) => filters.type === 'ALL' || category.type === filters.type);
  const hasActiveFilter = Boolean(filters.query || filters.categoryId || filters.startDate || filters.endDate || filters.type !== 'ALL');
  const rangeActive = Boolean(filters.startDate && filters.endDate);
  const tabs = [
    ['daily', '일일'],
    ['calendar', '달력'],
    ['monthly', '월별'],
    ['summary', '요약'],
    ['memo', '메모']
  ];

  return (
    <div className="screen ledger-screen">
      <AppHeader title="가계부" />
      <MonthNav month={month} setMonth={setMonth} />
      {rangeActive && <div className="range-banner">{filters.startDate} ~ {filters.endDate}</div>}

      <nav className="view-tabs" aria-label="가계부 보기">
        {tabs.map(([key, label]) => (
          <button key={key} type="button" className={ledgerMode === key ? 'active' : ''} onClick={() => setLedgerMode(key)}>
            {label}
          </button>
        ))}
      </nav>

      <MonthTotals summary={summary} />
      <LedgerFilters
        filters={filters}
        setFilters={setFilters}
        categories={filterCategories}
        resultCount={filteredTransactions.length}
        hasActiveFilter={hasActiveFilter}
        exportFilteredTransactions={() => exportFilteredTransactions(filteredTransactions, filters)}
      />

      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {ledgerMode === 'daily' && <DailyLedger transactions={filteredTransactions} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />}
          {ledgerMode === 'calendar' && <CalendarLedger transactions={filteredTransactions} month={month} />}
          {ledgerMode === 'monthly' && <MonthlyLedger summary={summary} month={month} />}
          {ledgerMode === 'summary' && (
            <LedgerSummary
              summary={summary}
              hasActiveFilter={hasActiveFilter}
              exportTransactions={() => exportFilteredTransactions(filteredTransactions, filters)}
            />
          )}
          {ledgerMode === 'memo' && <MemoLedger transactions={filteredTransactions} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />}
        </>
      )}
    </div>
  );
}

function LedgerFilters({ filters, setFilters, categories, resultCount, hasActiveFilter, exportFilteredTransactions }) {
  function updateFilter(key, value) {
    setFilters((prev) => {
      const next = { ...prev, [key]: value };
      if (key === 'type') next.categoryId = '';
      return next;
    });
  }

  return (
    <section className="ledger-filters">
      <input
        value={filters.query}
        onChange={(event) => updateFilter('query', event.target.value)}
        placeholder="검색"
        aria-label="거래 검색"
      />
      <div>
        <select value={filters.type} onChange={(event) => updateFilter('type', event.target.value)} aria-label="거래 유형 필터">
          <option value="ALL">전체</option>
          <option value="INCOME">수입</option>
          <option value="EXPENSE">지출</option>
          <option value="TRANSFER">이체</option>
        </select>
        <select value={filters.categoryId} onChange={(event) => updateFilter('categoryId', event.target.value)} aria-label="카테고리 필터">
          <option value="">분류 전체</option>
          {categories.map((category) => (
            <option value={category.id} key={category.id}>{category.icon} {category.name}</option>
          ))}
        </select>
        {hasActiveFilter && (
          <button type="button" onClick={() => setFilters(emptyLedgerFilters())}>초기화</button>
        )}
      </div>
      <div className="date-filter-row">
        <input type="date" value={filters.startDate} onChange={(event) => updateFilter('startDate', event.target.value)} aria-label="시작일" />
        <input type="date" value={filters.endDate} onChange={(event) => updateFilter('endDate', event.target.value)} aria-label="종료일" />
      </div>
      <div className="filter-result-row">
        <span>{hasActiveFilter ? `${resultCount}건` : `이번 달 ${resultCount}건`}</span>
        <button type="button" onClick={exportFilteredTransactions} disabled={resultCount === 0}>결과 CSV</button>
      </div>
    </section>
  );
}

function MonthNav({ month, setMonth }) {
  return (
    <div className="month-nav">
      <button type="button" aria-label="이전 달" onClick={() => setMonth(shiftMonth(month, -1))}>‹</button>
      <label>
        <span>{monthLabel(month)}</span>
        <input type="month" value={month} onChange={(event) => setMonth(event.target.value)} aria-label="월 선택" />
      </label>
      <button type="button" aria-label="다음 달" onClick={() => setMonth(shiftMonth(month, 1))}>›</button>
    </div>
  );
}

function MonthTotals({ summary }) {
  return (
    <section className="month-totals" aria-label="월 합계">
      <Metric label="수입" value={numberOnly(summary.income)} tone="income" />
      <Metric label="지출" value={numberOnly(summary.expense)} tone="expense" />
      <Metric label="합계" value={money((Number(summary.income) || 0) - (Number(summary.expense) || 0))} tone="total" />
    </section>
  );
}

function Metric({ label, value, tone }) {
  return (
    <div className={`mini-metric ${tone || ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function DailyLedger({ transactions, openInstallmentSchedule, openTransactionDetail }) {
  const groups = useMemo(() => {
    const next = new Map();
    transactions.forEach((item) => {
      if (!next.has(item.transactionDate)) next.set(item.transactionDate, []);
      next.get(item.transactionDate).push(item);
    });
    return Array.from(next.entries()).sort(([a], [b]) => b.localeCompare(a));
  }, [transactions]);

  if (!transactions.length) {
    return <EmptyState label="이번 달 거래가 없습니다." />;
  }

  return (
    <section className="daily-list">
      {groups.map(([date, items]) => {
        const meta = dayMeta(date);
        const income = items.filter((item) => item.type === 'INCOME').reduce((sum, item) => sum + Number(item.amount), 0);
        const expense = items.filter((item) => item.type === 'EXPENSE').reduce((sum, item) => sum + Number(item.amount), 0);
        return (
          <article className="day-section" key={date}>
            <header className="day-header">
              <div>
                <strong>{meta.day}</strong>
                <span className={`weekday ${meta.weekday === '일' ? 'sunday' : meta.weekday === '토' ? 'saturday' : ''}`}>{meta.weekday}요일</span>
              </div>
              <div className="day-sums">
                <span className="income">{money(income)}</span>
                <span className="expense">{money(expense)}</span>
              </div>
            </header>
            {items.map((item) => <TransactionRow item={item} key={item.id} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />)}
          </article>
        );
      })}
    </section>
  );
}

function TransactionRow({ item, openInstallmentSchedule, openTransactionDetail }) {
  const hasInstallment = item.installmentGroupId && item.installmentMonths > 1;
  return (
    <div
      className={`transaction-row ${hasInstallment ? 'has-installment' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => openTransactionDetail?.(item)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') openTransactionDetail?.(item);
      }}
    >
      <div className="category-cell">
        <span>{item.categoryIcon || iconForType(item.type)}</span>
        <em>{item.categoryName || typeLabels[item.type]}</em>
      </div>
      <div className="transaction-main">
        <strong>{item.title || item.categoryName || typeLabels[item.type]}</strong>
        <span>{item.assetName || transferLabel(item) || '자산 미지정'}</span>
        {hasInstallment && (
          <button className="installment-chip" type="button" onClick={(event) => {
            event.stopPropagation();
            openInstallmentSchedule(item);
          }}>
            {item.installmentIndex}/{item.installmentMonths} 할부
          </button>
        )}
      </div>
      <b className={transactionTone(item.type)}>{money(item.amount)}</b>
    </div>
  );
}

function transferLabel(item) {
  if (item.type !== 'TRANSFER') return '';
  return '자산 이체';
}

function iconForType(type) {
  if (type === 'INCOME') return '↙';
  if (type === 'TRANSFER') return '⇄';
  return '•';
}

function CalendarLedger({ transactions, month }) {
  const monthDate = new Date(`${month}-01T00:00:00`);
  const firstDay = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1);
  const start = new Date(firstDay);
  start.setDate(firstDay.getDate() - firstDay.getDay());
  const cells = Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    const key = formatDate(date);
    const items = transactions.filter((item) => item.transactionDate === key);
    const income = items.filter((item) => item.type === 'INCOME').reduce((sum, item) => sum + Number(item.amount), 0);
    const expense = items.filter((item) => item.type === 'EXPENSE').reduce((sum, item) => sum + Number(item.amount), 0);
    return { key, date, income, expense, activeMonth: sameMonth(key, month) };
  });

  return (
    <section className="calendar-panel">
      <div className="calendar-weekdays">
        {weekdays.map((day) => <span key={day}>{day}</span>)}
      </div>
      <div className="calendar-grid">
        {cells.map((cell) => (
          <div className={`calendar-cell ${cell.activeMonth ? '' : 'muted'} ${cell.key === today ? 'today' : ''}`} key={cell.key}>
            <span>{cell.date.getDate() === 1 ? `${cell.date.getMonth() + 1}. 1.` : cell.date.getDate()}</span>
            {cell.income > 0 && <b className="income">{numberOnly(cell.income)}</b>}
            {cell.expense > 0 && <b className="expense">{numberOnly(cell.expense)}</b>}
          </div>
        ))}
      </div>
    </section>
  );
}

function MonthlyLedger({ summary, month }) {
  const rows = [0, -1, -2, -3].map((offset) => {
    const rowMonth = shiftMonth(month, offset);
    const active = offset === 0;
    return {
      month: rowMonth,
      income: active ? summary.income : 0,
      expense: active ? summary.expense : 0
    };
  });

  return (
    <section className="plain-list">
      {rows.map((row) => (
        <div className="monthly-row" key={row.month}>
          <strong>{monthLabel(row.month)}</strong>
          <div>
            <span className="income">수입 {money(row.income)}</span>
            <span className="expense">지출 {money(row.expense)}</span>
          </div>
        </div>
      ))}
    </section>
  );
}

function LedgerSummary({ summary, hasActiveFilter, exportTransactions }) {
  const budgetUsage = Math.min(100, Number(summary.budgetUsageRate || 0));
  return (
    <section className="summary-sections">
      <SummaryBlock icon="◎" title="자산">
        <KeyValue label="자산" value={money(summary.assetTotal)} />
        <KeyValue label="부채" value={money(summary.liabilityTotal)} />
      </SummaryBlock>

      <SummaryBlock icon="▤" title="예산">
        <div className="budget-overview">
          <div>
            <span>전체예산</span>
            <strong>{money(summary.budget)}</strong>
          </div>
          <ProgressBar value={budgetUsage} marker />
        </div>
      </SummaryBlock>

      <button className="export-button" type="button" onClick={exportTransactions}>
        <span>▦</span>
        {hasActiveFilter ? '필터 결과 CSV 내보내기' : '월 거래 CSV 내보내기'}
      </button>
    </section>
  );
}

function SummaryBlock({ icon, title, action, children }) {
  return (
    <article className="summary-block">
      <header>
        <h2><span>{icon}</span>{title}</h2>
        {action && <button type="button" aria-label={`${title} 설정`}>{action}</button>}
      </header>
      <div className="summary-card">{children}</div>
    </article>
  );
}

function KeyValue({ label, value }) {
  return (
    <div className="key-value">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function MemoLedger({ transactions, openInstallmentSchedule, openTransactionDetail }) {
  const memoTransactions = transactions.filter((item) => item.memo);
  if (!memoTransactions.length) {
    return <EmptyState icon="▤" label="데이터가 없습니다." />;
  }
  return (
    <section className="plain-list">
      {memoTransactions.map((item) => (
        <article className="memo-row" key={item.id}>
          <TransactionRow item={item} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />
          <p>{item.memo}</p>
        </article>
      ))}
    </section>
  );
}

function StatsScreen({ data, month, setMonth, statsMode, setStatsMode, statsPeriod, setStatsPeriod, statsBreakdown, setStatsBreakdown, yearlySummary, categoryByName, spendMax, loading, openBudgetSettings, openLedgerCategory, openLedgerTag }) {
  const summary = data.summary || {};
  const activeSummary = statsPeriod === 'yearly' ? yearlySummary || {} : summary;
  const tabs = [
    ['stats', '통계'],
    ['budget', '예산'],
    ['details', '내용']
  ];

  return (
    <div className="screen stats-screen">
      <header className="stats-top">
        <nav className="segmented-tabs" aria-label="통계 보기">
          {tabs.map(([key, label]) => (
            <button key={key} type="button" className={statsMode === key ? 'active' : ''} onClick={() => setStatsMode(key)}>
              {label}
            </button>
          ))}
        </nav>
        <button className="period-button" type="button" onClick={() => setStatsPeriod((prev) => prev === 'monthly' ? 'yearly' : 'monthly')}>
          {statsPeriod === 'yearly' ? '연별' : '월별'}
        </button>
      </header>

      <MonthNav month={month} setMonth={setMonth} />
      <IncomeExpenseSwitch summary={activeSummary} />
      {statsMode === 'stats' && (
        <nav className="stats-breakdown-tabs" aria-label="지출 통계 기준">
          <button type="button" className={statsBreakdown === 'category' ? 'active' : ''} onClick={() => setStatsBreakdown('category')}>카테고리</button>
          <button type="button" className={statsBreakdown === 'tag' ? 'active' : ''} onClick={() => setStatsBreakdown('tag')}>소비 태그</button>
        </nav>
      )}

      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {statsMode === 'stats' && statsPeriod === 'monthly' && (
            statsBreakdown === 'category'
              ? <CategoryStats summary={summary} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />
              : <TagStats summary={summary} openLedgerTag={openLedgerTag} />
          )}
          {statsMode === 'stats' && statsPeriod === 'yearly' && (
            <YearlyStats
              summary={yearlySummary || {}}
              categoryByName={categoryByName}
              breakdown={statsBreakdown}
              openLedgerCategory={openLedgerCategory}
              openLedgerTag={openLedgerTag}
            />
          )}
          {statsMode === 'budget' && <BudgetStats summary={summary} categoryByName={categoryByName} spendMax={spendMax} openBudgetSettings={openBudgetSettings} />}
          {statsMode === 'details' && <DetailStats transactions={data.transactions} />}
        </>
      )}
    </div>
  );
}

function IncomeExpenseSwitch({ summary }) {
  return (
    <div className="income-expense-switch">
      <button type="button">수입</button>
      <button type="button" className="active">지출 {money(summary.expense)}</button>
    </div>
  );
}

function CategoryStats({ summary, categoryByName, openLedgerCategory }) {
  const spends = summary.categorySpends || [];
  const total = spends.reduce((sum, item) => sum + Number(item.amount), 0);
  const chartStyle = { background: buildChartGradient(spends, total, categoryByName) };

  return (
    <section className="stats-content">
      <div className="chart-zone">
        <div className="donut-chart" style={chartStyle}>
          <span>{total ? '지출' : '0원'}</span>
        </div>
      </div>
      <CategoryRanking spends={spends} total={total} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />
    </section>
  );
}

function TagStats({ summary, openLedgerTag }) {
  const tags = summary.tagSpends || [];
  return (
    <section className="stats-content">
      <TagRanking tags={tags} expenseTotal={Number(summary.expense || 0)} openLedgerTag={openLedgerTag} />
    </section>
  );
}

function YearlyStats({ summary, categoryByName, breakdown, openLedgerCategory, openLedgerTag }) {
  const spends = summary.categorySpends || [];
  const total = spends.reduce((sum, item) => sum + Number(item.amount), 0);
  const rows = summary.monthlyTotals || [];
  const maxExpense = Math.max(1, ...rows.map((item) => Number(item.expense || 0)));

  return (
    <section className="stats-content yearly-stats">
      <div className="yearly-headline">
        <span>{summary.year || ''}년</span>
        <strong>{money(summary.expense)}</strong>
      </div>
      <div className="yearly-month-list">
        {rows.map((item) => {
          const width = Math.round((Number(item.expense || 0) / maxExpense) * 100);
          return (
            <div className="yearly-month-row" key={item.month}>
              <span>{Number(item.month.slice(5, 7))}월</span>
              <div><i style={{ width: `${width}%` }} /></div>
              <b>{money(item.expense)}</b>
            </div>
          );
        })}
      </div>
      {breakdown === 'tag'
        ? <TagRanking tags={summary.tagSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerTag={openLedgerTag} />
        : <CategoryRanking spends={spends} total={total} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />}
    </section>
  );
}

function buildChartGradient(spends, total, categoryByName) {
  if (!total) return 'conic-gradient(#eef0f5 0 100%)';
  let start = 0;
  const segments = spends.map((item, index) => {
    const category = categoryByName.get(item.categoryName);
    const color = category?.color || palette[index % palette.length];
    const next = start + (Number(item.amount) / total) * 100;
    const segment = `${color} ${start}% ${next}%`;
    start = next;
    return segment;
  });
  return `conic-gradient(${segments.join(', ')})`;
}

function CategoryRanking({ spends, total, categoryByName, openLedgerCategory }) {
  if (!spends.length) return <EmptyState label="통계 데이터가 없습니다." compact />;
  return (
    <div className="ranking-list">
      {spends.map((item, index) => {
        const category = categoryByName.get(item.categoryName);
        const percent = total ? Math.round((Number(item.amount) / total) * 100) : 0;
        const color = category?.color || palette[index % palette.length];
        return (
          <button className="ranking-row" type="button" key={item.categoryName} onClick={() => openLedgerCategory(item)}>
            <span className="percent-badge" style={{ backgroundColor: color }}>{percent}%</span>
            <strong>{category?.icon || '•'} {item.categoryName}</strong>
            <b>{money(item.amount)}</b>
          </button>
        );
      })}
    </div>
  );
}

function TagRanking({ tags, expenseTotal, openLedgerTag }) {
  if (!tags.length) return <EmptyState label="소비 태그 통계가 없습니다." compact />;
  return (
    <div className="ranking-list tag-ranking-list">
      {tags.map((item, index) => {
        const percent = expenseTotal ? Math.round((Number(item.amount) / expenseTotal) * 100) : 0;
        const color = palette[index % palette.length];
        return (
          <button className="ranking-row tag-ranking-row" type="button" key={item.tagName} onClick={() => openLedgerTag(item)}>
            <span className="percent-badge" style={{ backgroundColor: color }}>{percent}%</span>
            <strong>#{item.tagName}<small>{item.transactionCount}건</small></strong>
            <b>{money(item.amount)}</b>
          </button>
        );
      })}
    </div>
  );
}

function BudgetStats({ summary, categoryByName, spendMax, openBudgetSettings }) {
  const usage = Math.min(100, Number(summary.budgetUsageRate || 0));
  const categoryBudgets = summary.categoryBudgetUsages || [];
  return (
    <section className="budget-screen">
      <div className="budget-headline">
        <div>
          <span>남은 예산(월별)</span>
          <strong>{money(summary.remainingBudget)}</strong>
        </div>
        <button type="button" onClick={openBudgetSettings}>예산설정 ›</button>
      </div>
      <div className="budget-track">
        <span>예산 (월별)</span>
        <ProgressBar value={usage} marker />
        <div>
          <b>{money(summary.budget)}</b>
          <em>{usage}%</em>
        </div>
      </div>
      <div className="budget-category-list">
        {categoryBudgets.map((item, index) => {
          const category = categoryByName.get(item.categoryName);
          const percent = Number(item.usageRate || 0);
          const barWidth = Math.min(100, percent);
          const amountLabel = item.exceeded ? `${numberOnly(Math.abs(Number(item.remainingAmount || 0)))} 초과` : `${numberOnly(item.remainingAmount)} 남음`;
          return (
            <div className={`budget-category-row ${item.exceeded ? 'over' : ''}`} key={item.categoryId || item.categoryName}>
              <span>{category?.icon || '•'} {item.categoryName}</span>
              <div><i style={{ width: `${barWidth}%`, backgroundColor: item.exceeded ? 'var(--accent)' : category?.color || palette[index % palette.length] }} /></div>
              <b>{percent}%</b>
              <small>{numberOnly(item.spentAmount)} / {numberOnly(item.budgetAmount)}</small>
              <em>{amountLabel}</em>
            </div>
          );
        })}
        {!categoryBudgets.length && <EmptyState label="카테고리 예산 데이터가 없습니다." compact />}
      </div>
    </section>
  );
}

function DetailStats({ transactions }) {
  const rows = useMemo(() => {
    const grouped = new Map();
    transactions
      .filter((item) => item.type === 'EXPENSE')
      .forEach((item) => {
        const key = item.title || item.categoryName || '미분류';
        const prev = grouped.get(key) || { title: key, count: 0, amount: 0 };
        grouped.set(key, { ...prev, count: prev.count + 1, amount: prev.amount + Number(item.amount) });
      });
    return Array.from(grouped.values()).sort((a, b) => b.amount - a.amount);
  }, [transactions]);

  return (
    <section className="details-table">
      <header>
        <span>내용</span>
        <span>↓ 9 1</span>
        <span>금액</span>
      </header>
      {rows.map((row) => (
        <div className="details-row" key={row.title}>
          <strong>{row.title}</strong>
          <span>{row.count}건</span>
          <b>{money(row.amount)}</b>
        </div>
      ))}
      {!rows.length && <EmptyState label="내용 데이터가 없습니다." compact />}
    </section>
  );
}

function ProgressBar({ value, marker }) {
  return (
    <div className="progress-wrap">
      {marker && <span className="today-marker" style={{ left: `${Math.min(95, Math.max(6, value || 64))}%` }}>오늘</span>}
      <div className="progress-bar">
        <i style={{ width: `${Math.min(100, Number(value || 0))}%` }} />
      </div>
    </div>
  );
}

function AssetsScreen({ data, loading, openAssetForm, openCardPaymentManager, deleteAsset }) {
  const summary = data.summary || {};
  const groups = useMemo(() => {
    const next = new Map();
    data.assets.forEach((asset) => {
      const groupName = asset.groupName || assetTypeLabels[asset.type] || '자산';
      if (!next.has(groupName)) next.set(groupName, []);
      next.get(groupName).push(asset);
    });
    return Array.from(next.entries());
  }, [data.assets]);

  return (
    <div className="screen assets-screen">
      <AppHeader
        title="자산"
        right={
          <div className="asset-actions">
            <IconButton label="자산 편집">✎</IconButton>
            <IconButton label="자산 추가" onClick={() => openAssetForm(null)}>+</IconButton>
          </div>
        }
      />
      <section className="asset-summary">
        <Metric label="자산" value={numberOnly(summary.assetTotal)} tone="income" />
        <Metric label="부채" value={`-${numberOnly(summary.liabilityTotal)}`} tone="expense" />
        <Metric label="합계" value={numberOnly(summary.netWorth)} tone="total" />
      </section>
      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <section className="asset-groups">
          {groups.map(([groupName, assets]) => {
            const groupTotal = assets.reduce((sum, asset) => sum + Number(asset.balance), 0);
            return (
              <article className="asset-group" key={groupName}>
                <h2><span>{groupName}</span><b>{money(groupTotal)}</b></h2>
                {assets.map((asset) => (
                  <div className="asset-row" key={asset.id}>
                    <button type="button" onClick={() => openAssetForm(asset)}>
                      <strong>{asset.name}</strong>
                      <span>{asset.ownerName ? `${asset.ownerName} · ${assetTypeLabels[asset.type] || asset.type}` : assetTypeLabels[asset.type] || asset.type}</span>
                    </button>
                    <b className={asset.type === 'CARD' || asset.type === 'DEBT' ? 'expense' : 'income'}>{money(asset.balance)}</b>
                    {asset.type === 'CARD' && (
                      <button className="card-pay-button" type="button" onClick={() => openCardPaymentManager(asset)} aria-label={`${asset.name} 결제 관리`}>
                        결제
                      </button>
                    )}
                    <button className="row-delete" type="button" aria-label={`${asset.name} 삭제`} onClick={() => deleteAsset(asset)}>−</button>
                  </div>
                ))}
              </article>
            );
          })}
        </section>
      )}
    </div>
  );
}

function CardPaymentManagerScreen({ card, detail, schedules, form, setForm, saveSchedule, executeSchedule, retrySchedule, rescheduleSchedule, cancelSchedule, onClose }) {
  const pending = schedules.filter((schedule) => schedule.status === 'SCHEDULED' || schedule.status === 'PROCESSING');
  const completed = schedules.filter((schedule) => schedule.status !== 'SCHEDULED' && schedule.status !== 'PROCESSING');

  return (
    <div className="full-panel">
      <section className="card-payment-manager">
        <AppHeader title="카드 결제" left={<BackButton label="자산" onClick={onClose} />} />
        <section className="card-payment-summary">
          <strong>{card?.name || detail?.name || '카드'}</strong>
          <div>
            <Metric label="미결제" value={money(detail?.unpaidAmount)} tone="expense" />
            <Metric label="예정" value={money(detail?.paymentScheduleAmount)} tone="total" />
          </div>
          {detail?.billingStartDate && detail?.billingEndDate && (
            <p className="card-billing-note">
              {detail.billingStartDate} ~ {detail.billingEndDate}
              {detail.nextPaymentDate ? ` · ${detail.nextPaymentDate} 결제 예정` : ''}
              {detail.paymentDateAdjusted && detail.originalPaymentDate ? ` (${detail.originalPaymentDate} 영업일 보정)` : ''}
            </p>
          )}
        </section>
        <form className="card-payment-form" onSubmit={saveSchedule}>
          <LineField label="결제일">
            <input type="date" value={form.scheduledDate} onChange={(event) => setForm((prev) => ({ ...prev, scheduledDate: event.target.value }))} required />
          </LineField>
          <LineField label="금액">
            <input inputMode="numeric" type="number" min="1" step="1" value={form.amount} onChange={(event) => setForm((prev) => ({ ...prev, amount: event.target.value }))} placeholder="0" required />
          </LineField>
          <button className="wide-save-button" type="submit">예약 추가</button>
        </form>
        <section className="card-payment-list">
          <h2>결제 예약</h2>
          {pending.map((schedule) => (
            <div className="card-payment-row" key={schedule.id}>
              <div>
                <strong>{schedule.scheduledDate}</strong>
                <span>{paymentStatusLabel(schedule)}</span>
              </div>
              <b>{money(schedule.amount)}</b>
              {schedule.status === 'SCHEDULED' ? (
                <div className="card-payment-row-actions">
                  <button type="button" onClick={() => executeSchedule(schedule)}>실행</button>
                  <button className="secondary" type="button" onClick={() => cancelSchedule(schedule)}>취소</button>
                </div>
              ) : <span className="payment-status-badge">처리 중</span>}
            </div>
          ))}
          {!pending.length && <EmptyState label="예약된 결제가 없습니다." compact />}
          {!!completed.length && <h2>처리 내역</h2>}
          {completed.map((schedule) => (
            <div className={`card-payment-row muted ${schedule.status === 'FAILED' ? 'failed' : ''}`} key={schedule.id}>
              <div>
                <strong>{schedule.scheduledDate}</strong>
                <span>{paymentStatusLabel(schedule)}</span>
                {schedule.failureReason && <small>{schedule.failureReason}</small>}
              </div>
              <b>{money(schedule.amount)}</b>
              {schedule.status === 'FAILED' && (
                <div className="card-payment-row-actions">
                  <button type="button" onClick={() => retrySchedule(schedule)}>재시도</button>
                  <button className="secondary" type="button" onClick={() => rescheduleSchedule(schedule)}>재예약</button>
                </div>
              )}
            </div>
          ))}
        </section>
      </section>
    </div>
  );
}

function paymentStatusLabel(schedule) {
  if (schedule.status === 'SCHEDULED') return '예약됨';
  if (schedule.status === 'PROCESSING') return '처리 중';
  if (schedule.status === 'COMPLETED') {
    return schedule.completedAt ? `완료 ${schedule.completedAt.slice(0, 10)}` : '완료';
  }
  if (schedule.status === 'FAILED') return '실패';
  return schedule.status;
}

function InstallmentScheduleScreen({ transaction, schedule, editGroup, deleteGroup, onClose }) {
  const total = schedule.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const title = transaction?.title || transaction?.categoryName || '할부 거래';

  return (
    <div className="full-panel">
      <section className="installment-manager">
        <AppHeader
          title="할부 일정"
          left={<BackButton label="가계부" onClick={onClose} />}
          right={<button className="text-action" type="button" onClick={editGroup}>수정</button>}
        />
        <section className="installment-summary">
          <strong>{title}</strong>
          <div>
            <Metric label="총액" value={money(total)} tone="expense" />
            <Metric label="개월" value={`${schedule.length || transaction?.installmentMonths || 0}`} tone="total" />
          </div>
          <button className="danger-outline-button" type="button" onClick={deleteGroup}>전체 삭제</button>
        </section>
        <section className="installment-list">
          {schedule.map((item) => (
            <div className={item.id === transaction?.id ? 'installment-row active' : 'installment-row'} key={item.id}>
              <div>
                <strong>{item.installmentIndex}/{item.installmentMonths}</strong>
                <span>{item.transactionDate}</span>
              </div>
              <b>{money(item.amount)}</b>
            </div>
          ))}
          {!schedule.length && <EmptyState label="할부 일정이 없습니다." compact />}
        </section>
      </section>
    </div>
  );
}

function MoreScreen({ rawText, setRawText, preview, parseText, confirmTextImport, exportMonthlyTransactions, openCategoryManager, openRecurringManager }) {
  return (
    <div className="screen more-screen">
      <AppHeader title="더보기" />
      <section className="sms-import">
        <h2>문자 자동 입력</h2>
        <textarea
          value={rawText}
          onChange={(event) => setRawText(event.target.value)}
          placeholder="[카드승인] 6/22 석수 점심식비 8,900원"
        />
        <button className="primary-button" type="button" onClick={parseText}>문자 분석</button>
        {preview && (
          <div className="preview-card">
            <div>
              <strong>{preview.merchant || '가맹점 후보 없음'}</strong>
              <span>{preview.transactionDate} · {typeLabels[preview.type]} · {money(preview.amount)}</span>
            </div>
            <dl className="preview-detail">
              <div>
                <dt>메모</dt>
                <dd>{preview.memo || '-'}</dd>
              </div>
              <div>
                <dt>원문</dt>
                <dd>{preview.rawText || rawText}</dd>
              </div>
            </dl>
            <button className="secondary-button" type="button" onClick={confirmTextImport}>거래 입력으로 이동</button>
          </div>
        )}
      </section>

      <section className="more-list">
        <button type="button" onClick={openCategoryManager}>카테고리 관리<span>›</span></button>
        <button type="button" onClick={openRecurringManager}>반복 거래<span>›</span></button>
        <button type="button">영수증 사진<span>›</span></button>
        <button type="button" onClick={exportMonthlyTransactions}>월 거래 CSV 내보내기<span>›</span></button>
      </section>
    </div>
  );
}

function RecurringManagerScreen({ rules, form, setForm, editingRule, clearEditingRule, assets, categories, saveRule, deleteRule, editRule, generateDue, onClose }) {
  const selectedCategories = categories.filter((category) => category.type === form.type);

  function updateField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function setType(type) {
    setForm((prev) => ({
      ...prev,
      type,
      categoryId: '',
      assetId: type === 'TRANSFER' ? '' : prev.assetId,
      installmentMonths: type === 'EXPENSE' ? prev.installmentMonths : 0
    }));
  }

  return (
    <div className="full-panel">
      <section className="recurring-manager">
        <AppHeader title="반복 거래" left={<BackButton label="더보기" onClick={onClose} />} />
        <form className="recurring-form" onSubmit={saveRule}>
          <nav className="entry-tabs compact">
            {['INCOME', 'EXPENSE', 'TRANSFER'].map((type) => (
              <button key={type} type="button" className={`${form.type === type ? 'active' : ''} ${type.toLowerCase()}`} onClick={() => setType(type)}>
                {typeLabels[type]}
              </button>
            ))}
          </nav>
          <div className="edit-fields recurring-fields">
            <LineField label="금액">
              <input inputMode="numeric" value={form.amount} onChange={(event) => updateField('amount', event.target.value)} required />
            </LineField>
            {form.type === 'TRANSFER' ? (
              <>
                <LineField label="출금">
                  <select value={form.fromAssetId} onChange={(event) => updateField('fromAssetId', event.target.value)}>
                    <option value="">선택</option>
                    {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                  </select>
                </LineField>
                <LineField label="입금">
                  <select value={form.toAssetId} onChange={(event) => updateField('toAssetId', event.target.value)}>
                    <option value="">선택</option>
                    {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                  </select>
                </LineField>
              </>
            ) : (
              <>
                <LineField label="분류">
                  <select value={form.categoryId} onChange={(event) => updateField('categoryId', event.target.value)}>
                    <option value="">선택</option>
                    {selectedCategories.map((category) => <option value={category.id} key={category.id}>{category.icon} {category.name}</option>)}
                  </select>
                </LineField>
                <LineField label="자산">
                  <select value={form.assetId} onChange={(event) => updateField('assetId', event.target.value)}>
                    <option value="">선택</option>
                    {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                  </select>
                </LineField>
              </>
            )}
            <LineField label="내용">
              <input value={form.title} onChange={(event) => updateField('title', event.target.value)} placeholder="내용" />
            </LineField>
            <LineField label="반복">
              <select value={form.frequency} onChange={(event) => updateField('frequency', event.target.value)}>
                <option value="DAILY">매일</option>
                <option value="WEEKLY">매주</option>
                <option value="MONTHLY">매월</option>
                <option value="YEARLY">매년</option>
              </select>
            </LineField>
            <LineField label="간격">
              <input inputMode="numeric" min="1" value={form.intervalValue} onChange={(event) => updateField('intervalValue', event.target.value)} />
            </LineField>
            <LineField label="시작">
              <input type="date" value={form.startDate} onChange={(event) => {
                updateField('startDate', event.target.value);
                if (!form.nextRunDate) updateField('nextRunDate', event.target.value);
              }} />
            </LineField>
            <LineField label="다음">
              <input type="date" value={form.nextRunDate} onChange={(event) => updateField('nextRunDate', event.target.value)} />
            </LineField>
            <LineField label="종료">
              <input type="date" value={form.endDate} onChange={(event) => updateField('endDate', event.target.value)} />
            </LineField>
          </div>
          <button className="wide-save-button" type="submit">{editingRule ? '수정' : '저장'}</button>
        </form>
        <div className="recurring-actions">
          <button type="button" onClick={() => {
            clearEditingRule();
            setForm(emptyRecurringForm());
          }}>새 규칙</button>
          <button type="button" onClick={generateDue}>오늘분 생성</button>
        </div>
        <div className="recurring-list">
          {rules.map((rule) => (
            <div className="recurring-row" key={rule.id}>
              <button type="button" onClick={() => editRule(rule)}>
                <strong>{rule.title || rule.categoryName || typeLabels[rule.type]}</strong>
                <span>{frequencyLabel(rule.frequency)} · 다음 {rule.nextRunDate}</span>
              </button>
              <b className={transactionTone(rule.type)}>{money(rule.amount)}</b>
              <button className="row-delete" type="button" onClick={() => deleteRule(rule)} aria-label={`${rule.title || '반복 거래'} 삭제`}>×</button>
            </div>
          ))}
          {!rules.length && <EmptyState label="반복 거래 규칙이 없습니다." compact />}
        </div>
      </section>
    </div>
  );
}

function frequencyLabel(frequency) {
  return {
    DAILY: '매일',
    WEEKLY: '매주',
    MONTHLY: '매월',
    YEARLY: '매년'
  }[frequency] || frequency;
}

function TransactionDetailScreen({ transaction, receipts, editTransaction, deleteTransaction, deleteReceipt, openInstallmentSchedule, onClose }) {
  if (!transaction) return null;
  const hasInstallment = transaction.installmentGroupId && transaction.installmentMonths > 1;
  return (
    <div className="full-panel">
      <section className="transaction-detail-screen">
        <AppHeader
          title="거래 상세"
          left={<BackButton label="가계부" onClick={onClose} />}
          right={<IconButton label="수정" onClick={() => editTransaction(transaction)}>✎</IconButton>}
        />
        <section className={`transaction-detail-hero ${transactionTone(transaction.type)}`}>
          <span>{transaction.categoryIcon || iconForType(transaction.type)}</span>
          <strong>{money(transaction.amount)}</strong>
          <em>{transaction.title || transaction.categoryName || typeLabels[transaction.type]}</em>
        </section>
        <section className="transaction-detail-list">
          <KeyValue label="유형" value={typeLabels[transaction.type]} />
          <KeyValue label="날짜" value={transaction.transactionDate} />
          <KeyValue label="분류" value={transaction.categoryName || '미분류'} />
          <KeyValue label="자산" value={transaction.assetName || transferLabel(transaction) || '자산 미지정'} />
          {transaction.spendingTag && <KeyValue label="소비 태그" value={transaction.spendingTag} />}
          {hasInstallment && <KeyValue label="할부" value={`${transaction.installmentIndex}/${transaction.installmentMonths}개월`} />}
          {transaction.memo && <KeyValue label="메모" value={transaction.memo} />}
          <section className="receipt-preview-list">
            <h2>영수증</h2>
            {receipts.map((receipt) => (
              <div className="receipt-preview-row" key={receipt.id}>
                <a href={`${API}/transactions/${transaction.id}/receipts/${receipt.id}/file`} target="_blank" rel="noreferrer">
                  <img src={`${API}/transactions/${transaction.id}/receipts/${receipt.id}/file`} alt={receipt.originalFilename || '영수증'} />
                  <span>{receipt.originalFilename || '영수증'}</span>
                </a>
                <button type="button" onClick={() => deleteReceipt(receipt)}>삭제</button>
              </div>
            ))}
            {!receipts.length && <EmptyState label="첨부된 영수증이 없습니다." compact />}
          </section>
        </section>
        <div className="transaction-detail-actions">
          {hasInstallment && (
            <button className="secondary-action" type="button" onClick={() => openInstallmentSchedule(transaction)}>
              할부 내역
            </button>
          )}
          <button className="danger-action" type="button" onClick={() => deleteTransaction(transaction)}>
            삭제
          </button>
        </div>
      </section>
    </div>
  );
}

function EntryScreen({ form, expression, assets, categories, receiptFiles, updateForm, setReceiptFiles, setExpression, submitTransaction, editingTransaction, editingInstallmentGroup, onClose }) {
  const tone = form.type === 'INCOME' ? 'income' : form.type === 'TRANSFER' ? 'transfer' : 'expense';
  const isEditing = Boolean(editingTransaction || editingInstallmentGroup);

  function setType(type) {
    updateForm('type', type);
    updateForm('categoryId', '');
    if (type !== 'EXPENSE') {
      updateForm('installmentMonths', 0);
      updateForm('spendingTag', '');
    }
    if (type === 'TRANSFER') {
      updateForm('assetId', '');
    }
  }

  function handleKey(value) {
    if (value === '확인' || value === '저장') return;
    if (value === '⌫') {
      const next = expression.slice(0, -1);
      setExpression(next);
      updateForm('amount', amountFromExpression(next));
      return;
    }
    if (value === '=') {
      const result = amountFromExpression(expression);
      setExpression(result ? String(result) : expression);
      updateForm('amount', result);
      return;
    }
    const next = `${expression}${value}`;
    setExpression(next);
    updateForm('amount', amountFromExpression(next));
  }

  return (
    <div className="full-panel">
      <form className="entry-screen-form" onSubmit={submitTransaction}>
        <AppHeader
          title={editingInstallmentGroup ? '할부 전체 수정' : editingTransaction ? '거래 수정' : typeLabels[form.type]}
          left={<BackButton label="가계부" onClick={onClose} />}
          right={!isEditing && <IconButton label="즐겨찾기">☆</IconButton>}
        />

        <div className="entry-tabs">
          {['INCOME', 'EXPENSE', 'TRANSFER'].map((type) => (
            <button key={type} type="button" className={`${form.type === type ? 'active' : ''} ${type.toLowerCase()}`} onClick={() => setType(type)}>
              {typeLabels[type]}
            </button>
          ))}
        </div>

        <section className={`entry-fields ${tone}`}>
          <LineField label="날짜">
            <input type="date" value={form.transactionDate} onChange={(event) => updateForm('transactionDate', event.target.value)} />
          </LineField>

          <LineField label="금액" side={form.type === 'TRANSFER' ? <span className="fee-pill">수수료</span> : <span className="repeat-pill">반복/할부</span>}>
            <input value={expression || form.amount} inputMode="decimal" onChange={(event) => {
              setExpression(event.target.value);
              updateForm('amount', amountFromExpression(event.target.value));
            }} placeholder="0" />
          </LineField>

          {form.type === 'TRANSFER' ? (
            <>
              <LineField label="출금" side={<span className="swap-icon">⇅</span>}>
                <select value={form.fromAssetId} onChange={(event) => updateForm('fromAssetId', event.target.value)}>
                  <option value="">선택</option>
                  {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                </select>
              </LineField>
              <LineField label="입금">
                <select value={form.toAssetId} onChange={(event) => updateForm('toAssetId', event.target.value)}>
                  <option value="">선택</option>
                  {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                </select>
              </LineField>
              <LineField label="수수료">
                <input inputMode="numeric" value={form.fee} onChange={(event) => updateForm('fee', event.target.value)} placeholder="0" />
              </LineField>
            </>
          ) : (
            <>
              <LineField label="분류">
                <select value={form.categoryId} onChange={(event) => updateForm('categoryId', event.target.value)}>
                  <option value="">선택</option>
                  {categories.map((category) => <option value={category.id} key={category.id}>{category.icon} {category.name}</option>)}
                </select>
              </LineField>
              <LineField label="자산">
                <select value={form.assetId} onChange={(event) => updateForm('assetId', event.target.value)}>
                  <option value="">선택</option>
                  {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                </select>
              </LineField>
              {form.type === 'EXPENSE' && (
                <LineField label="할부">
                  <select value={form.installmentMonths} onChange={(event) => updateForm('installmentMonths', Number(event.target.value))}>
                    <option value={0}>일시불</option>
                    <option value={2}>2개월</option>
                    <option value={3}>3개월</option>
                    <option value={6}>6개월</option>
                    <option value={12}>12개월</option>
                  </select>
                </LineField>
              )}
            </>
          )}

          <LineField label="내용" side={<span className="memo-alert">!</span>}>
            <input value={form.title} onChange={(event) => updateForm('title', event.target.value)} placeholder="내용" />
          </LineField>
          {form.type === 'EXPENSE' && (
            <LineField label="태그">
              <input value={form.spendingTag} onChange={(event) => updateForm('spendingTag', event.target.value)} placeholder="식비, 생활, 고정비" />
            </LineField>
          )}

          {!editingInstallmentGroup && (
            <label className="receipt-compact">
              <strong>{editingTransaction ? '영수증 추가' : '영수증 사진'}</strong>
              <input
                type="file"
                accept="image/*"
                multiple
                onChange={(event) => setReceiptFiles(Array.from(event.target.files || []).slice(0, 10))}
              />
              <em>최대 10장</em>
              {receiptFiles.length > 0 && (
                <span className="receipt-selection-list">
                  {receiptFiles.map((file, index) => (
                    <button type="button" key={`${file.name}-${file.lastModified}-${index}`} onClick={(event) => {
                      event.preventDefault();
                      setReceiptFiles((current) => current.filter((_, fileIndex) => fileIndex !== index));
                    }}>
                      {file.name} ×
                    </button>
                  ))}
                </span>
              )}
            </label>
          )}
        </section>

        <CalculatorPad onKey={handleKey} submitLabel={isEditing ? '저장' : '확인'} />
      </form>
    </div>
  );
}

function LineField({ label, side, children }) {
  return (
    <label className="line-field">
      <span>{label}</span>
      <div>{children}</div>
      {side && <em>{side}</em>}
    </label>
  );
}

function CalculatorPad({ onKey, submitLabel = '확인' }) {
  const keys = ['+', '-', '×', '÷', '7', '8', '9', '=', '4', '5', '6', '.', '1', '2', '3', '⌫', '', '0', '', submitLabel];
  return (
    <section className="calculator">
      <header>
        <span>금액</span>
        <button type="button">◎</button>
        <button type="button">×</button>
      </header>
      <div className="calculator-grid">
        {keys.map((key, index) => key ? (
          <button key={`${key}-${index}`} type={key === submitLabel ? 'submit' : 'button'} className={key === submitLabel ? 'confirm' : ''} onClick={() => onKey(key)}>
            {key}
          </button>
        ) : <span key={`blank-${index}`} />)}
      </div>
    </section>
  );
}

function amountFromExpression(value) {
  const expression = String(value || '').replaceAll('×', '*').replaceAll('÷', '/').trim();
  if (!expression) return '';
  if (!/^[0-9+\-*/. ()]+$/.test(expression)) return '';
  try {
    const result = Function(`"use strict"; return (${expression})`)();
    return Number.isFinite(result) ? Math.max(0, Math.round(result)) : '';
  } catch {
    return '';
  }
}

function AssetFormScreen({ form, setForm, assets, editingAsset, saveAsset, onClose }) {
  const paymentAccounts = assets.filter((asset) => asset.type !== 'CARD' && asset.type !== 'DEBT');
  return (
    <div className="full-panel">
      <form className="simple-edit-screen" onSubmit={saveAsset}>
        <AppHeader title={editingAsset ? '수정' : '추가'} left={<BackButton label="자산" onClick={onClose} />} />
        <section className="edit-fields">
          <LineField label="그룹">
            <select value={form.type} onChange={(event) => {
              const type = event.target.value;
              setForm((prev) => ({ ...prev, type, groupName: assetTypeLabels[type] || prev.groupName }));
            }}>
              {assetTypeOptions.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select>
          </LineField>
          <LineField label="이름">
            <input autoFocus value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
          </LineField>
          <LineField label="명의">
            <input value={form.ownerName} onChange={(event) => setForm((prev) => ({ ...prev, ownerName: event.target.value }))} placeholder="본인" />
          </LineField>
          <LineField label="금액">
            <input inputMode="numeric" value={form.balance} onChange={(event) => setForm((prev) => ({ ...prev, balance: event.target.value }))} />
          </LineField>
          <LineField label="메모">
            <input value={form.memo} onChange={(event) => setForm((prev) => ({ ...prev, memo: event.target.value }))} />
          </LineField>
          {form.type === 'CARD' && (
            <section className="card-profile-fields">
              <h2>카드 결제 설정</h2>
              <LineField label="결제계좌">
                <select value={form.paymentAccountId} onChange={(event) => setForm((prev) => ({ ...prev, paymentAccountId: event.target.value }))} required>
                  <option value="">선택</option>
                  {paymentAccounts.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
                </select>
              </LineField>
              <LineField label="확정일">
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={form.statementClosingDay}
                  onChange={(event) => setForm((prev) => ({ ...prev, statementClosingDay: event.target.value }))}
                  required
                />
              </LineField>
              <LineField label="결제일">
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={form.paymentDay}
                  onChange={(event) => setForm((prev) => ({ ...prev, paymentDay: event.target.value }))}
                  required
                />
              </LineField>
              <label className="toggle-line">
                <span>자동 결제</span>
                <input
                  type="checkbox"
                  checked={form.autoPayment}
                  onChange={(event) => setForm((prev) => ({ ...prev, autoPayment: event.target.checked }))}
                />
              </label>
            </section>
          )}
        </section>
        <button className="wide-save-button" type="submit">저장</button>
      </form>
    </div>
  );
}

function CategoryManagerScreen({ categories, categoryType, setCategoryType, form, setForm, editingCategory, setEditingCategory, saveCategory, deleteCategory, onClose }) {
  const list = categories.filter((category) => category.type === categoryType);
  return (
    <div className="full-panel">
      <section className="category-manager">
        <AppHeader
          title={categoryType === 'EXPENSE' ? '지출' : '수입'}
          left={<BackButton label="설정" onClick={onClose} />}
          right={<IconButton label="추가" onClick={() => {
            setEditingCategory(null);
            setForm(emptyCategoryForm(categoryType));
          }}>+</IconButton>}
        />
        <div className="category-manager-toggle">
          <span>소분류 기능</span>
          <label className="switch"><input type="checkbox" /><i /></label>
        </div>
        <nav className="entry-tabs compact">
          <button type="button" className={categoryType === 'EXPENSE' ? 'active expense' : ''} onClick={() => setCategoryType('EXPENSE')}>지출</button>
          <button type="button" className={categoryType === 'INCOME' ? 'active income' : ''} onClick={() => setCategoryType('INCOME')}>수입</button>
        </nav>
        <form className="category-inline-form" onSubmit={saveCategory}>
          <input value={form.icon} onChange={(event) => setForm((prev) => ({ ...prev, icon: event.target.value }))} aria-label="아이콘" />
          <input value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} placeholder="카테고리명" required />
          <input type="color" value={form.color} onChange={(event) => setForm((prev) => ({ ...prev, color: event.target.value }))} aria-label="색상" />
          <button type="submit">{editingCategory ? '수정' : '추가'}</button>
        </form>
        <div className="category-admin-list">
          {list.map((category) => (
            <div className="category-admin-row" key={category.id}>
              <button className="minus-button" type="button" onClick={() => deleteCategory(category)}>−</button>
              <strong>{category.icon} {category.name}</strong>
              <button type="button" onClick={() => {
                setEditingCategory(category);
                setForm({ type: category.type, name: category.name, icon: category.icon || '', color: category.color || '#ff625c' });
              }}>✎</button>
              <span>☰</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
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

function EmptyState({ icon = '▤', label, compact }) {
  return (
    <div className={`empty-state ${compact ? 'compact' : ''}`}>
      <span>{icon}</span>
      <p>{label}</p>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
