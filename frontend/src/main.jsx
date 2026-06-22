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
  const [statsMode, setStatsMode] = useState('stats');
  const [month, setMonth] = useState(currentMonth);
  const [data, setData] = useState({ assets: [], categories: [], transactions: [], summary: null });
  const [loading, setLoading] = useState(true);
  const [panel, setPanel] = useState(null);
  const [editingAsset, setEditingAsset] = useState(null);
  const [editingCategory, setEditingCategory] = useState(null);
  const [entryExpression, setEntryExpression] = useState('');
  const [form, setForm] = useState(emptyTransactionForm());
  const [assetForm, setAssetForm] = useState(emptyAssetForm());
  const [categoryForm, setCategoryForm] = useState(emptyCategoryForm());
  const [categoryType, setCategoryType] = useState('EXPENSE');
  const [budgetSettings, setBudgetSettings] = useState(null);
  const [receiptFile, setReceiptFile] = useState(null);
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

  async function openBudgetSettings() {
    const response = await fetch(`${API}/budgets/settings?month=${month}`);
    setBudgetSettings(await response.json());
    setPanel('budget');
  }

  function openEntry(type = 'EXPENSE') {
    setForm((prev) => ({ ...prev, type }));
    setEntryExpression(form.amount || '');
    setPanel('entry');
  }

  function openAssetForm(asset = null) {
    setEditingAsset(asset);
    setAssetForm(asset ? {
      type: asset.type,
      groupName: asset.groupName || assetTypeLabels[asset.type] || '기타',
      name: asset.name || '',
      balance: String(Number(asset.balance || 0)),
      memo: asset.memo || ''
    } : emptyAssetForm());
    setPanel('assetForm');
  }

  function openCategoryManager() {
    setEditingCategory(null);
    setCategoryForm(emptyCategoryForm(categoryType));
    setPanel('categories');
  }

  function closePanel() {
    setPanel(null);
    setEditingAsset(null);
    setEditingCategory(null);
  }

  function updateForm(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
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

    const response = await fetch(`${API}/transactions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const created = await response.json();

    if (receiptFile) {
      const upload = new FormData();
      upload.append('file', receiptFile);
      await fetch(`${API}/transactions/${created.id}/receipts`, { method: 'POST', body: upload });
    }

    const fee = Number(form.fee || 0);
    if (form.type === 'TRANSFER' && fee > 0 && form.fromAssetId) {
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
    setReceiptFile(null);
    closePanel();
    await load();
  }

  async function saveAsset(event) {
    event.preventDefault();
    const payload = {
      ...assetForm,
      balance: Number(assetForm.balance || 0)
    };
    const url = editingAsset ? `${API}/assets/${editingAsset.id}` : `${API}/assets`;
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

  async function parseText() {
    if (!rawText.trim()) return;
    const response = await fetch(`${API}/import/text/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawText })
    });
    const parsed = await response.json();
    setPreview(parsed);
    setForm((prev) => ({
      ...prev,
      type: parsed.type || 'EXPENSE',
      transactionDate: parsed.transactionDate || today,
      amount: parsed.amount || '',
      title: parsed.merchant || prev.title,
      memo: parsed.memo || ''
    }));
    setEntryExpression(String(parsed.amount || ''));
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
          />
        )}
        {mainTab === 'stats' && (
          <StatsScreen
            {...screenProps}
            statsMode={statsMode}
            setStatsMode={setStatsMode}
            openBudgetSettings={openBudgetSettings}
          />
        )}
        {mainTab === 'assets' && (
          <AssetsScreen
            {...screenProps}
            openAssetForm={openAssetForm}
            deleteAsset={deleteAsset}
          />
        )}
        {mainTab === 'more' && (
          <MoreScreen
            rawText={rawText}
            setRawText={setRawText}
            preview={preview}
            parseText={parseText}
            openCategoryManager={openCategoryManager}
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
            receiptFile={receiptFile}
            updateForm={updateForm}
            setReceiptFile={setReceiptFile}
            setExpression={setEntryExpression}
            submitTransaction={submitTransaction}
            onClose={closePanel}
          />
        )}
        {panel === 'assetForm' && (
          <AssetFormScreen
            form={assetForm}
            setForm={setAssetForm}
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
    installmentMonths: 0
  };
}

function emptyAssetForm() {
  return {
    type: 'CASH',
    groupName: '현금',
    name: '',
    balance: '',
    memo: ''
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

function LedgerScreen({ data, month, setMonth, ledgerMode, setLedgerMode, loading }) {
  const summary = data.summary || {};
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

      <nav className="view-tabs" aria-label="가계부 보기">
        {tabs.map(([key, label]) => (
          <button key={key} type="button" className={ledgerMode === key ? 'active' : ''} onClick={() => setLedgerMode(key)}>
            {label}
          </button>
        ))}
      </nav>

      <MonthTotals summary={summary} />

      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {ledgerMode === 'daily' && <DailyLedger transactions={data.transactions} />}
          {ledgerMode === 'calendar' && <CalendarLedger transactions={data.transactions} month={month} />}
          {ledgerMode === 'monthly' && <MonthlyLedger summary={summary} month={month} />}
          {ledgerMode === 'summary' && <LedgerSummary summary={summary} transactions={data.transactions} />}
          {ledgerMode === 'memo' && <MemoLedger transactions={data.transactions} />}
        </>
      )}
    </div>
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

function DailyLedger({ transactions }) {
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
            {items.map((item) => <TransactionRow item={item} key={item.id} />)}
          </article>
        );
      })}
    </section>
  );
}

function TransactionRow({ item }) {
  return (
    <div className="transaction-row">
      <div className="category-cell">
        <span>{item.categoryIcon || iconForType(item.type)}</span>
        <em>{item.categoryName || typeLabels[item.type]}</em>
      </div>
      <div className="transaction-main">
        <strong>{item.title || item.categoryName || typeLabels[item.type]}</strong>
        <span>{item.assetName || transferLabel(item) || '자산 미지정'}</span>
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

function LedgerSummary({ summary }) {
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

      <button className="export-button" type="button">
        <span>▦</span>
        메일로 엑셀파일 내보내기
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

function MemoLedger({ transactions }) {
  const memoTransactions = transactions.filter((item) => item.memo);
  if (!memoTransactions.length) {
    return <EmptyState icon="▤" label="데이터가 없습니다." />;
  }
  return (
    <section className="plain-list">
      {memoTransactions.map((item) => (
        <article className="memo-row" key={item.id}>
          <TransactionRow item={item} />
          <p>{item.memo}</p>
        </article>
      ))}
    </section>
  );
}

function StatsScreen({ data, month, setMonth, statsMode, setStatsMode, categoryByName, spendMax, loading, openBudgetSettings }) {
  const summary = data.summary || {};
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
        <button className="period-button" type="button">월별⌄</button>
      </header>

      <MonthNav month={month} setMonth={setMonth} />
      <IncomeExpenseSwitch summary={summary} />

      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {statsMode === 'stats' && <CategoryStats summary={summary} categoryByName={categoryByName} />}
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

function CategoryStats({ summary, categoryByName }) {
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
      <CategoryRanking spends={spends} total={total} categoryByName={categoryByName} />
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

function CategoryRanking({ spends, total, categoryByName }) {
  if (!spends.length) return <EmptyState label="통계 데이터가 없습니다." compact />;
  return (
    <div className="ranking-list">
      {spends.map((item, index) => {
        const category = categoryByName.get(item.categoryName);
        const percent = total ? Math.round((Number(item.amount) / total) * 100) : 0;
        const color = category?.color || palette[index % palette.length];
        return (
          <div className="ranking-row" key={item.categoryName}>
            <span className="percent-badge" style={{ backgroundColor: color }}>{percent}%</span>
            <strong>{category?.icon || '•'} {item.categoryName}</strong>
            <b>{money(item.amount)}</b>
          </div>
        );
      })}
    </div>
  );
}

function BudgetStats({ summary, categoryByName, spendMax, openBudgetSettings }) {
  const usage = Math.min(100, Number(summary.budgetUsageRate || 0));
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
        {(summary.categorySpends || []).map((item, index) => {
          const category = categoryByName.get(item.categoryName);
          return (
            <div className="budget-category-row" key={item.categoryName}>
              <span>{category?.icon || '•'} {item.categoryName}</span>
              <div><i style={{ width: `${(Number(item.amount) / spendMax) * 100}%`, backgroundColor: category?.color || palette[index % palette.length] }} /></div>
              <b>{numberOnly(item.amount)}</b>
            </div>
          );
        })}
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

function AssetsScreen({ data, loading, openAssetForm, deleteAsset }) {
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
                      <span>{assetTypeLabels[asset.type] || asset.type}</span>
                    </button>
                    <b className={asset.type === 'CARD' || asset.type === 'DEBT' ? 'expense' : 'income'}>{money(asset.balance)}</b>
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

function MoreScreen({ rawText, setRawText, preview, parseText, openCategoryManager }) {
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
            <strong>{preview.merchant || '가맹점 후보 없음'}</strong>
            <span>{preview.transactionDate} · {typeLabels[preview.type]} · {money(preview.amount)}</span>
          </div>
        )}
      </section>

      <section className="more-list">
        <button type="button" onClick={openCategoryManager}>카테고리 관리<span>›</span></button>
        <button type="button">영수증 사진<span>›</span></button>
        <button type="button">백업 및 내보내기<span>›</span></button>
      </section>
    </div>
  );
}

function EntryScreen({ form, expression, assets, categories, receiptFile, updateForm, setReceiptFile, setExpression, submitTransaction, onClose }) {
  const tone = form.type === 'INCOME' ? 'income' : form.type === 'TRANSFER' ? 'transfer' : 'expense';

  function setType(type) {
    updateForm('type', type);
    updateForm('categoryId', '');
    if (type === 'TRANSFER') {
      updateForm('assetId', '');
    }
  }

  function handleKey(value) {
    if (value === '확인') return;
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
          title={typeLabels[form.type]}
          left={<BackButton label="가계부" onClick={onClose} />}
          right={<IconButton label="즐겨찾기">☆</IconButton>}
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
            </>
          )}

          <LineField label="내용" side={<span className="memo-alert">!</span>}>
            <input value={form.title} onChange={(event) => updateForm('title', event.target.value)} placeholder="내용" />
          </LineField>

          <label className="receipt-compact">
            영수증 사진
            <input type="file" accept="image/*" onChange={(event) => setReceiptFile(event.target.files?.[0] || null)} />
            {receiptFile && <span>{receiptFile.name}</span>}
          </label>
        </section>

        <CalculatorPad onKey={handleKey} submitLabel="확인" />
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

function CalculatorPad({ onKey }) {
  const keys = ['+', '-', '×', '÷', '7', '8', '9', '=', '4', '5', '6', '.', '1', '2', '3', '⌫', '', '0', '', '확인'];
  return (
    <section className="calculator">
      <header>
        <span>금액</span>
        <button type="button">◎</button>
        <button type="button">×</button>
      </header>
      <div className="calculator-grid">
        {keys.map((key, index) => key ? (
          <button key={`${key}-${index}`} type={key === '확인' ? 'submit' : 'button'} className={key === '확인' ? 'confirm' : ''} onClick={() => onKey(key)}>
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

function AssetFormScreen({ form, setForm, editingAsset, saveAsset, onClose }) {
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
          <LineField label="금액">
            <input inputMode="numeric" value={form.balance} onChange={(event) => setForm((prev) => ({ ...prev, balance: event.target.value }))} />
          </LineField>
          <LineField label="메모">
            <input value={form.memo} onChange={(event) => setForm((prev) => ({ ...prev, memo: event.target.value }))} />
          </LineField>
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

function BudgetSettingsScreen({ settings, setSettings, month, setMonth, saveBudget, onClose }) {
  const categories = settings?.categories || [];

  return (
    <div className="full-panel">
      <form className="budget-settings-screen" onSubmit={saveBudget}>
        <AppHeader title="예산설정" left={<BackButton label="뒤로" onClick={onClose} />} />
        <MonthNav month={month} setMonth={setMonth} />
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
