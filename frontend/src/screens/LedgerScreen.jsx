import { useMemo, useState } from 'react';
import { AppHeader, EmptyState, MoneyInput, ProgressBar } from '../components/ui';
import {
  formatDate,
  money,
  monthLabel,
  numberOnly,
  sameMonth,
  shiftMonth,
  transactionTone,
  weekdays
} from '../utils/format';
import { toNumber } from '../utils/numberValues';

const today = formatDate(new Date());
const currentMonth = today.slice(0, 7);
const typeLabels = { INCOME: '수입', EXPENSE: '지출', TRANSFER: '이체' };
const consumptionScopeLabels = { PERSONAL: '개인', SHARED: '공동' };

export function emptyLedgerFilters() {
  return {
    query: '',
    type: 'ALL',
    categoryId: '',
    consumptionScope: '',
    consumerMemberId: '',
    assetId: '',
    minAmount: '',
    maxAmount: '',
    page: 0,
    size: 50,
    sort: 'DATE_DESC'
  };
}

export function isAllFilterValue(value) {
  return value === '' || value === null || value === undefined || value === 'ALL';
}

export function hasLedgerFilters(filters) {
  return Boolean(
    filters.query
    || !isAllFilterValue(filters.categoryId)
    || !isAllFilterValue(filters.consumptionScope)
    || !isAllFilterValue(filters.consumerMemberId)
    || !isAllFilterValue(filters.assetId)
    || filters.minAmount
    || filters.maxAmount
    || filters.type !== 'ALL'
  );
}

function filterTransactions(transactions, filters) {
  const query = (filters.query || '').trim().toLowerCase();
  const categoryId = isAllFilterValue(filters.categoryId) ? '' : filters.categoryId;
  const consumptionScope = isAllFilterValue(filters.consumptionScope) ? '' : filters.consumptionScope;
  const consumerMemberId = isAllFilterValue(filters.consumerMemberId) ? '' : filters.consumerMemberId;
  const assetId = isAllFilterValue(filters.assetId) ? '' : filters.assetId;
  return transactions.filter((item) => {
    if (filters.type !== 'ALL' && item.type !== filters.type) return false;
    if (categoryId && String(item.categoryId || '') !== String(categoryId)) return false;
    if (consumptionScope && item.consumptionScope !== consumptionScope) return false;
    if (consumerMemberId && String(item.consumerMemberId || '') !== String(consumerMemberId)) return false;
    if (assetId && ![item.assetId, item.fromAssetId, item.toAssetId].some((id) => String(id || '') === String(assetId))) return false;
    if (filters.minAmount && Number(item.amount || 0) < toNumber(filters.minAmount)) return false;
    if (filters.maxAmount && Number(item.amount || 0) > toNumber(filters.maxAmount)) return false;
    if (!query) return true;
    const haystack = [
      item.title,
      item.memo,
      item.spendingTag,
      consumptionScopeLabels[item.consumptionScope],
      item.consumerMemberName,
      item.categoryName,
      item.assetName,
      item.transactionDate,
      transferLabel(item)
    ].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(query);
  });
}

function summarizeTransactions(transactions) {
  return transactions.reduce((summary, item) => {
    const amount = Number(item.amount || 0);
    if (item.type === 'INCOME') summary.income += amount;
    if (item.type === 'EXPENSE') summary.expense += amount;
    if (item.type === 'TRANSFER') summary.transfer += amount;
    summary.count += 1;
    return summary;
  }, { income: 0, expense: 0, transfer: 0, count: 0 });
}

export function filteredPeriodLabel(filters, month) {
  return monthLabel(month);
}

export function LedgerScreen({
  data,
  month,
  setMonth,
  ledgerMode,
  setLedgerMode,
  filters,
  setFilters,
  searchTransactions,
  searchResult,
  loading,
  exportFilteredTransactions,
  openInstallmentSchedule,
  openTransactionDetail,
  members
}) {
  const summary = data.summary || {};
  const sourceTransactions = searchTransactions || data.transactions || [];
  const filteredTransactions = useMemo(() => filterTransactions(sourceTransactions, filters), [sourceTransactions, filters]);
  const filteredSummary = useMemo(() => summarizeTransactions(filteredTransactions), [filteredTransactions]);
  const filterCategories = data.categories.filter((category) => filters.type === 'ALL' || category.type === filters.type);
  const hasActiveFilter = hasLedgerFilters(filters);
  const summaryScope = hasActiveFilter ? filteredPeriodLabel(filters, month) : monthLabel(month);
  const tabs = [['daily', '일일'], ['calendar', '달력'], ['monthly', '월별'], ['summary', '요약'], ['memo', '메모']];

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
      <MonthTotals summary={hasActiveFilter ? filteredSummary : summary} scope={summaryScope} filtered={hasActiveFilter} />
      <LedgerFiltersPaged
        filters={filters}
        setFilters={setFilters}
        categories={filterCategories}
        assets={data.assets}
        members={members}
        resultCount={hasActiveFilter ? (searchResult?.totalElements ?? filteredTransactions.length) : filteredTransactions.length}
        hasActiveFilter={hasActiveFilter}
        searchResult={searchResult}
        exportFilteredTransactions={() => exportFilteredTransactions(filteredTransactions, filters, searchResult)}
      />
      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {ledgerMode === 'daily' && <DailyLedger transactions={filteredTransactions} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />}
          {ledgerMode === 'calendar' && <CalendarLedger transactions={filteredTransactions} month={month} />}
          {ledgerMode === 'monthly' && <MonthlyLedger summary={hasActiveFilter ? filteredSummary : summary} month={month} filtered={hasActiveFilter} scope={summaryScope} />}
          {ledgerMode === 'summary' && (
            <LedgerSummary
              summary={summary}
              transactionSummary={hasActiveFilter ? filteredSummary : summarizeTransactions(data.transactions || [])}
              summaryScope={summaryScope}
              hasActiveFilter={hasActiveFilter}
              exportTransactions={() => exportFilteredTransactions(filteredTransactions, filters, searchResult)}
            />
          )}
          {ledgerMode === 'memo' && <MemoLedger transactions={filteredTransactions} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} />}
        </>
      )}
    </div>
  );
}

function LedgerFilters({ filters, setFilters, categories, assets, members, resultCount, hasActiveFilter, searchResult, exportFilteredTransactions }) {
  function updateFilter(key, value) {
    setFilters((prev) => {
      const next = { ...prev, [key]: value };
      if (!['page', 'size'].includes(key)) next.page = 0;
      if (key === 'type') {
        next.categoryId = '';
        if (value !== 'EXPENSE') {
          next.consumptionScope = '';
          next.consumerMemberId = '';
        }
      }
      if (key === 'consumptionScope' && value !== 'PERSONAL') next.consumerMemberId = '';
      if (key === 'consumerMemberId' && value) {
        next.type = 'EXPENSE';
        next.consumptionScope = 'PERSONAL';
      }
      return next;
    });
  }

  function updateSearchPage(nextPage) {
    setFilters((prev) => ({ ...prev, page: Math.max(0, nextPage) }));
  }

  const page = searchResult?.page ?? Number(filters.page || 0);
  const totalPages = searchResult?.totalPages ?? 0;
  const totalElements = searchResult?.totalElements ?? resultCount;

  return (
    <section className="ledger-filters">
      <input value={filters.query} onChange={(event) => updateFilter('query', event.target.value)} placeholder="검색" aria-label="거래 검색" />
      <div>
        <select value={filters.type} onChange={(event) => updateFilter('type', event.target.value)} aria-label="거래 유형 필터">
          <option value="ALL">전체</option><option value="INCOME">수입</option><option value="EXPENSE">지출</option><option value="TRANSFER">이체</option>
        </select>
        <select value={filters.categoryId} onChange={(event) => updateFilter('categoryId', event.target.value)} aria-label="카테고리 필터">
          <option value="">분류 전체</option>
          {categories.map((category) => <option value={category.id} key={category.id}>{category.icon} {category.name}</option>)}
        </select>
        <select value={filters.consumptionScope} onChange={(event) => updateFilter('consumptionScope', event.target.value)} aria-label="소비 구분 필터">
          <option value="">소비 전체</option><option value="PERSONAL">개인 소비</option><option value="SHARED">공동 소비</option>
        </select>
        <select value={filters.consumerMemberId} onChange={(event) => updateFilter('consumerMemberId', event.target.value)} aria-label="소비 명의 필터">
          <option value="">명의 전체</option>
          {members.map((member) => <option value={member.id} key={member.id}>{member.name}</option>)}
        </select>
        <select value={filters.assetId} onChange={(event) => updateFilter('assetId', event.target.value)} aria-label="자산 필터">
          <option value="">자산 전체</option>
          {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
        </select>
        {hasActiveFilter && <button type="button" onClick={() => setFilters(emptyLedgerFilters())}>초기화</button>}
      </div>
      <div className="amount-filter-row">
        <MoneyInput value={filters.minAmount} onValueChange={(value) => updateFilter('minAmount', value)} placeholder="최소 금액" aria-label="최소 금액" />
        <MoneyInput value={filters.maxAmount} onValueChange={(value) => updateFilter('maxAmount', value)} placeholder="최대 금액" aria-label="최대 금액" />
      </div>
      {hasActiveFilter && (
        <div className="search-options-row">
          <select value={filters.sort} onChange={(event) => updateFilter('sort', event.target.value)} aria-label="검색 정렬">
            <option value="DATE_DESC">최신순</option>
            <option value="DATE_ASC">오래된순</option>
            <option value="AMOUNT_DESC">금액 높은순</option>
            <option value="AMOUNT_ASC">금액 낮은순</option>
          </select>
          <select value={filters.size} onChange={(event) => updateFilter('size', Number(event.target.value))} aria-label="페이지 크기">
            <option value={20}>20개씩</option>
            <option value={50}>50개씩</option>
            <option value={100}>100개씩</option>
          </select>
        </div>
      )}
      <div className="filter-result-row">
        <span>{hasActiveFilter ? `${resultCount}건` : `이번 달 ${resultCount}건`}</span>
        <button type="button" onClick={exportFilteredTransactions} disabled={resultCount === 0}>결과 CSV</button>
      </div>
    </section>
  );
}

function LedgerFiltersPaged({ filters, setFilters, categories, assets, members, resultCount, hasActiveFilter, searchResult, exportFilteredTransactions }) {
  const [filtersOpen, setFiltersOpen] = useState(false);

  function updateFilter(key, value) {
    setFilters((prev) => {
      const next = { ...prev, [key]: value };
      if (!['page', 'size'].includes(key)) next.page = 0;
      if (key === 'type') {
        next.categoryId = '';
        if (value !== 'EXPENSE') {
          next.consumptionScope = '';
          next.consumerMemberId = '';
        }
      }
      if (key === 'consumptionScope' && value !== 'PERSONAL') next.consumerMemberId = '';
      if (key === 'consumerMemberId' && value) {
        next.type = 'EXPENSE';
        next.consumptionScope = 'PERSONAL';
      }
      return next;
    });
  }

  function updateSearchPage(nextPage) {
    setFilters((prev) => ({ ...prev, page: Math.max(0, nextPage) }));
  }

  const page = searchResult?.page ?? Number(filters.page || 0);
  const totalPages = searchResult?.totalPages ?? 0;
  const totalElements = searchResult?.totalElements ?? resultCount;
  const resultLabel = hasActiveFilter ? `${totalElements}건${totalPages > 0 ? ` · ${page + 1}/${totalPages}쪽` : ''}` : `이번 달 ${resultCount}건`;

  return (
    <section className={`ledger-filters ${filtersOpen ? 'open' : 'collapsed'}`}>
      <button
        type="button"
        className="filter-toggle-button"
        onClick={() => setFiltersOpen((current) => !current)}
        aria-expanded={filtersOpen}
      >
        <span>필터</span>
        <strong>{resultLabel}</strong>
        <i aria-hidden="true">{filtersOpen ? '▴' : '▾'}</i>
      </button>
      {filtersOpen && (
        <div className="ledger-filter-controls">
          <input value={filters.query} onChange={(event) => updateFilter('query', event.target.value)} placeholder="검색" aria-label="거래 검색" />
          <div className="filter-grid">
            <select value={filters.type} onChange={(event) => updateFilter('type', event.target.value)} aria-label="거래 유형 필터">
              <option value="ALL">전체</option>
              <option value="INCOME">수입</option>
              <option value="EXPENSE">지출</option>
              <option value="TRANSFER">이체</option>
            </select>
            <select value={filters.categoryId} onChange={(event) => updateFilter('categoryId', event.target.value)} aria-label="카테고리 필터">
              <option value="">분류 전체</option>
              {categories.map((category) => <option value={category.id} key={category.id}>{category.icon} {category.name}</option>)}
            </select>
            <select value={filters.consumptionScope} onChange={(event) => updateFilter('consumptionScope', event.target.value)} aria-label="소비 구분 필터">
              <option value="">소비 전체</option>
              <option value="PERSONAL">개인 소비</option>
              <option value="SHARED">공동 소비</option>
            </select>
            <select value={filters.consumerMemberId} onChange={(event) => updateFilter('consumerMemberId', event.target.value)} aria-label="소비 명의 필터">
              <option value="">명의 전체</option>
              {members.map((member) => <option value={member.id} key={member.id}>{member.name}</option>)}
            </select>
            <select value={filters.assetId} onChange={(event) => updateFilter('assetId', event.target.value)} aria-label="자산 필터">
              <option value="">자산 전체</option>
              {assets.map((asset) => <option value={asset.id} key={asset.id}>{asset.name}</option>)}
            </select>
            {hasActiveFilter && <button type="button" onClick={() => setFilters(emptyLedgerFilters())}>초기화</button>}
          </div>
          <div className="amount-filter-row">
            <MoneyInput value={filters.minAmount} onValueChange={(value) => updateFilter('minAmount', value)} placeholder="최소 금액" aria-label="최소 금액" />
            <MoneyInput value={filters.maxAmount} onValueChange={(value) => updateFilter('maxAmount', value)} placeholder="최대 금액" aria-label="최대 금액" />
          </div>
          {hasActiveFilter && (
            <div className="search-options-row">
              <select value={filters.sort} onChange={(event) => updateFilter('sort', event.target.value)} aria-label="검색 정렬">
                <option value="DATE_DESC">최신순</option>
                <option value="DATE_ASC">오래된순</option>
                <option value="AMOUNT_DESC">금액 높은순</option>
                <option value="AMOUNT_ASC">금액 낮은순</option>
              </select>
              <select value={filters.size} onChange={(event) => updateFilter('size', Number(event.target.value))} aria-label="페이지 크기">
                <option value={20}>20개씩</option>
                <option value={50}>50개씩</option>
                <option value={100}>100개씩</option>
              </select>
            </div>
          )}
          <div className="filter-result-row">
            <span>{resultLabel}</span>
            <button type="button" onClick={exportFilteredTransactions} disabled={resultCount === 0}>{hasActiveFilter ? '현재 페이지 CSV' : '결과 CSV'}</button>
          </div>
          {hasActiveFilter && <p className="csv-scope-hint">CSV는 현재 페이지에 표시된 {resultCount}건만 내보냅니다.</p>}
          {hasActiveFilter && totalPages > 1 && (
            <div className="search-page-row">
              <button type="button" onClick={() => updateSearchPage(page - 1)} disabled={page <= 0}>이전</button>
              <span>{page + 1} / {totalPages}</span>
              <button type="button" onClick={() => updateSearchPage(page + 1)} disabled={page + 1 >= totalPages}>다음</button>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

export function MonthNav({ month, setMonth }) {
  return (
    <div className="month-nav">
      <button type="button" aria-label="이전 달" onClick={() => setMonth(shiftMonth(month, -1))}>‹</button>
      <label><span>{monthLabel(month)}</span><input type="month" value={month} onChange={(event) => setMonth(event.target.value)} aria-label="월 선택" /></label>
      <button type="button" aria-label="다음 달" onClick={() => setMonth(shiftMonth(month, 1))}>›</button>
    </div>
  );
}

export function YearNav({ year, setYear }) {
  return (
    <div className="month-nav year-nav">
      <button type="button" aria-label="이전 연도" onClick={() => setYear(year - 1)}>‹</button>
      <label><span>{year}년</span><input type="number" value={year} onChange={(event) => setYear(Number(event.target.value || year))} aria-label="연도 선택" /></label>
      <button type="button" aria-label="다음 연도" onClick={() => setYear(year + 1)}>›</button>
    </div>
  );
}

function MonthTotals({ summary, scope, filtered }) {
  return (
    <section className={`month-totals ${filtered ? 'filtered' : ''}`} aria-label={filtered ? '필터 결과 합계' : '월 합계'}>
      <span className="totals-scope">{scope}</span>
      <Metric label="수입" value={numberOnly(summary.income)} tone="income" />
      <Metric label="지출" value={numberOnly(summary.expense)} tone="expense" />
      <Metric label="합계" value={money((Number(summary.income) || 0) - (Number(summary.expense) || 0))} tone="total" />
    </section>
  );
}

export function Metric({ label, value, tone }) {
  return <div className={`mini-metric ${tone || ''}`}><span>{label}</span><strong>{value}</strong></div>;
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
  if (!transactions.length) return <EmptyState label="조건에 맞는 거래가 없습니다." />;
  return (
    <section className="daily-list">
      {groups.map(([date, items]) => {
        const parsed = new Date(`${date}T00:00:00`);
        const meta = { day: parsed.getDate(), weekday: weekdays[parsed.getDay()] };
        const income = items.filter((item) => item.type === 'INCOME').reduce((sum, item) => sum + Number(item.amount), 0);
        const expense = items.filter((item) => item.type === 'EXPENSE').reduce((sum, item) => sum + Number(item.amount), 0);
        return (
          <article className="day-section" key={date}>
            <header className="day-header">
              <div><strong>{meta.day}</strong><span className={`weekday ${meta.weekday === '일' ? 'sunday' : meta.weekday === '토' ? 'saturday' : ''}`}>{meta.weekday}요일</span></div>
              <div className="day-sums"><span className="income">{money(income)}</span><span className="expense">{money(expense)}</span></div>
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
    <div className={`transaction-row ${hasInstallment ? 'has-installment' : ''}`} role="button" tabIndex={0} onClick={() => openTransactionDetail?.(item)} onKeyDown={(event) => {
      if (event.key === 'Enter' || event.key === ' ') openTransactionDetail?.(item);
    }}>
      <div className="category-cell"><span>{item.categoryIcon || iconForType(item.type)}</span><em>{item.categoryName || typeLabels[item.type]}</em></div>
      <div className="transaction-main">
        <strong>{item.title || item.categoryName || typeLabels[item.type]}</strong>
        <span>{item.assetName || transferLabel(item) || '자산 미지정'}</span>
        {hasInstallment && <button className="installment-chip" type="button" onClick={(event) => {
          event.stopPropagation();
          openInstallmentSchedule(item);
        }}>{item.installmentIndex}/{item.installmentMonths} 할부</button>}
        {item.type === 'EXPENSE' && <span className={`consumption-scope-chip ${item.consumptionScope === 'SHARED' ? 'shared' : ''}`}>
          {item.consumptionScope === 'PERSONAL' && item.consumerMemberName ? `개인 · ${item.consumerMemberName}` : consumptionScopeLabels[item.consumptionScope] || '개인'}
        </span>}
      </div>
      <b className={transactionTone(item.type)}>{money(item.amount)}</b>
    </div>
  );
}

export function transferLabel(item) {
  return item.type === 'TRANSFER' ? '자산 이체' : '';
}

export function iconForType(type) {
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
    return {
      key,
      date,
      income: items.filter((item) => item.type === 'INCOME').reduce((sum, item) => sum + Number(item.amount), 0),
      expense: items.filter((item) => item.type === 'EXPENSE').reduce((sum, item) => sum + Number(item.amount), 0),
      activeMonth: sameMonth(key, month)
    };
  });
  return (
    <section className="calendar-panel">
      <div className="calendar-weekdays">{weekdays.map((day) => <span key={day}>{day}</span>)}</div>
      <div className="calendar-grid">
        {cells.map((cell) => <div className={`calendar-cell ${cell.activeMonth ? '' : 'muted'} ${cell.key === today ? 'today' : ''}`} key={cell.key}>
          <span>{cell.date.getDate() === 1 ? `${cell.date.getMonth() + 1}. 1.` : cell.date.getDate()}</span>
          {cell.income > 0 && <b className="income">{numberOnly(cell.income)}</b>}
          {cell.expense > 0 && <b className="expense">{numberOnly(cell.expense)}</b>}
        </div>)}
      </div>
    </section>
  );
}

function MonthlyLedger({ summary, month, filtered, scope }) {
  if (filtered) return <section className="plain-list"><div className="monthly-row filtered-summary-row">
    <strong>{scope}</strong><div><span className="income">수입 {money(summary.income)}</span><span className="expense">지출 {money(summary.expense)}</span><span className="transfer">이체 {money(summary.transfer)}</span></div>
  </div></section>;
  const rows = [0, -1, -2, -3].map((offset) => ({
    month: shiftMonth(month, offset),
    income: offset === 0 ? summary.income : 0,
    expense: offset === 0 ? summary.expense : 0
  }));
  return <section className="plain-list">{rows.map((row) => <div className="monthly-row" key={row.month}>
    <strong>{monthLabel(row.month)}</strong><div><span className="income">수입 {money(row.income)}</span><span className="expense">지출 {money(row.expense)}</span></div>
  </div>)}</section>;
}

function LedgerSummary({ summary, transactionSummary, summaryScope, hasActiveFilter, exportTransactions }) {
  const budgetUsage = Math.min(100, Number(summary.budgetUsageRate || 0));
  return (
    <section className="summary-sections">
      <SummaryBlock icon="▤" title={hasActiveFilter ? '필터 결과' : '거래 요약'}>
        <KeyValue label="대상" value={summaryScope} /><KeyValue label="거래 건수" value={`${transactionSummary.count || 0}건`} />
        <KeyValue label="수입" value={money(transactionSummary.income)} /><KeyValue label="지출" value={money(transactionSummary.expense)} />
        <KeyValue label="이체" value={money(transactionSummary.transfer)} /><KeyValue label="수지" value={money(Number(transactionSummary.income || 0) - Number(transactionSummary.expense || 0))} />
      </SummaryBlock>
      <SummaryBlock icon="◎" title="자산"><KeyValue label="자산" value={money(summary.assetTotal)} /><KeyValue label="부채" value={money(summary.liabilityTotal)} /></SummaryBlock>
      <SummaryBlock icon="▤" title={`예산 (${monthLabel(summary.month || currentMonth)})`}>
        <div className="budget-overview"><div><span>전체예산</span><strong>{money(summary.budget)}</strong></div><ProgressBar value={budgetUsage} marker /></div>
      </SummaryBlock>
      <button className="export-button" type="button" onClick={exportTransactions}><span>▦</span>{hasActiveFilter ? '현재 검색 페이지 CSV 내보내기' : '월 거래 CSV 내보내기'}</button>
    </section>
  );
}

function SummaryBlock({ icon, title, action, children }) {
  return <article className="summary-block"><header><h2><span>{icon}</span>{title}</h2>{action && <button type="button" aria-label={`${title} 설정`}>{action}</button>}</header><div className="summary-card">{children}</div></article>;
}

export function KeyValue({ label, value }) {
  return <div className="key-value"><span>{label}</span><strong>{value}</strong></div>;
}

function MemoLedger({ transactions, openInstallmentSchedule, openTransactionDetail }) {
  const memoTransactions = transactions.filter((item) => item.memo);
  if (!memoTransactions.length) return <EmptyState icon="▤" label="데이터가 없습니다." />;
  return <section className="plain-list">{memoTransactions.map((item) => <article className="memo-row" key={item.id}>
    <TransactionRow item={item} openInstallmentSchedule={openInstallmentSchedule} openTransactionDetail={openTransactionDetail} /><p>{item.memo}</p>
  </article>)}</section>;
}
