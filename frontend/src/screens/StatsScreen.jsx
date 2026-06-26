import { useMemo } from 'react';
import { EmptyState, ProgressBar } from '../components/ui';
import { MonthNav, YearNav } from './LedgerScreen';
import { money, numberOnly } from '../utils/format';

const palette = ['#9bcf8e', '#b8875d', '#c58f67', '#6fa96f', '#d9bf8f', '#86b786', '#7b5638', '#e2d0b5'];
const consumptionScopeLabels = { PERSONAL: '개인', SHARED: '공동' };
export function StatsScreen({
  data,
  month,
  setMonth,
  statsMode,
  setStatsMode,
  statsPeriod,
  setStatsPeriod,
  budgetPeriod,
  setBudgetPeriod,
  statsBreakdown,
  setStatsBreakdown,
  yearlySummary,
  yearlyBudgetSummary,
  rangeSummary,
  statsRange,
  setStatsRange,
  categoryByName,
  loading,
  openBudgetSettings,
  openLedgerCategory,
  openLedgerTag,
  openLedgerScope,
  openLedgerMember
}) {
  const summary = data.summary || {};
  const activePeriod = statsMode === 'stats'
    ? statsPeriod
    : statsMode === 'budget'
      ? budgetPeriod
      : 'monthly';
  const activeSummary = activePeriod === 'yearly'
    ? statsMode === 'budget' ? yearlyBudgetSummary || {} : yearlySummary || {}
    : activePeriod === 'range'
      ? rangeSummary || {}
      : summary;
  const rangeInvalid = statsRange.endDate && statsRange.startDate && statsRange.endDate < statsRange.startDate;
  const tabs = [
    ['stats', '통계'],
    ['budget', '예산'],
    ['details', '내용']
  ];
  const periodTabs = [
    ['monthly', '월별'],
    ['yearly', '연별'],
    ['range', '기간']
  ];
  const budgetPeriodTabs = [
    ['monthly', '월별'],
    ['yearly', '연간']
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
        {statsMode === 'stats' ? (
          <nav className="period-mode-tabs" aria-label="통계 기간">
            {periodTabs.map(([key, label]) => (
              <button key={key} type="button" className={statsPeriod === key ? 'active' : ''} onClick={() => setStatsPeriod(key)}>
                {label}
              </button>
            ))}
          </nav>
        ) : statsMode === 'budget' ? (
          <nav className="period-mode-tabs" aria-label="예산 기간">
            {budgetPeriodTabs.map(([key, label]) => (
              <button key={key} type="button" className={budgetPeriod === key ? 'active' : ''} onClick={() => setBudgetPeriod(key)}>
                {label}
              </button>
            ))}
          </nav>
        ) : (
          <button className="period-button" type="button">월별</button>
        )}
      </header>

      {activePeriod === 'range' ? (
        <section className={`stats-range-filter ${rangeInvalid ? 'invalid' : ''}`}>
          <input type="date" value={statsRange.startDate} onChange={(event) => setStatsRange((current) => ({ ...current, startDate: event.target.value }))} aria-label="통계 시작일" />
          <input type="date" value={statsRange.endDate} onChange={(event) => setStatsRange((current) => ({ ...current, endDate: event.target.value }))} aria-label="통계 종료일" />
          <span>{rangeInvalid ? '종료일을 시작일 이후로 선택' : rangeSummary?.period || '기간 선택'}</span>
        </section>
      ) : activePeriod === 'yearly' ? (
        <YearNav year={Number(month.slice(0, 4))} setYear={(year) => setMonth(`${year}-${month.slice(5, 7)}`)} />
      ) : (
        <MonthNav month={month} setMonth={setMonth} />
      )}
      <IncomeExpenseSwitch summary={activeSummary} />
      {statsMode === 'stats' && (
        <nav className="stats-breakdown-tabs" aria-label="지출 통계 기준">
          <button type="button" className={statsBreakdown === 'category' ? 'active' : ''} onClick={() => setStatsBreakdown('category')}>카테고리</button>
          <button type="button" className={statsBreakdown === 'tag' ? 'active' : ''} onClick={() => setStatsBreakdown('tag')}>소비 태그</button>
          <button type="button" className={statsBreakdown === 'scope' ? 'active' : ''} onClick={() => setStatsBreakdown('scope')}>개인/공동</button>
          <button type="button" className={statsBreakdown === 'member' ? 'active' : ''} onClick={() => setStatsBreakdown('member')}>명의별</button>
        </nav>
      )}

      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          {statsMode === 'stats' && activePeriod === 'monthly' && (
            statsBreakdown === 'category'
              ? <CategoryStats summary={summary} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />
              : statsBreakdown === 'tag'
                ? <TagStats summary={summary} openLedgerTag={openLedgerTag} />
                : statsBreakdown === 'scope'
                  ? <ScopeStats summary={summary} openLedgerScope={openLedgerScope} />
                  : <MemberStats summary={summary} openLedgerMember={openLedgerMember} />
          )}
          {statsMode === 'stats' && activePeriod === 'yearly' && (
            <YearlyStats
              summary={yearlySummary || {}}
              categoryByName={categoryByName}
              breakdown={statsBreakdown}
              openLedgerCategory={openLedgerCategory}
              openLedgerTag={openLedgerTag}
              openLedgerScope={openLedgerScope}
              openLedgerMember={openLedgerMember}
            />
          )}
          {statsMode === 'stats' && activePeriod === 'range' && (
            rangeInvalid
              ? <EmptyState label="통계 기간을 확인해 주세요." />
              : <PeriodStats
                  summary={rangeSummary || {}}
                  categoryByName={categoryByName}
                  breakdown={statsBreakdown}
                  openLedgerCategory={openLedgerCategory}
                  openLedgerTag={openLedgerTag}
                  openLedgerScope={openLedgerScope}
                  openLedgerMember={openLedgerMember}
                />
          )}
          {statsMode === 'budget' && activePeriod === 'monthly' && (
            <BudgetStats summary={summary} categoryByName={categoryByName} openBudgetSettings={openBudgetSettings} />
          )}
          {statsMode === 'budget' && activePeriod === 'yearly' && (
            <YearlyBudgetStats summary={yearlyBudgetSummary || {}} />
          )}
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

function ScopeStats({ summary, openLedgerScope }) {
  return (
    <section className="stats-content">
      <ScopeRanking scopes={summary.scopeSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerScope={openLedgerScope} />
    </section>
  );
}

function MemberStats({ summary, openLedgerMember }) {
  return (
    <section className="stats-content">
      <MemberRanking members={summary.memberSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerMember={openLedgerMember} />
    </section>
  );
}

function YearlyStats({ summary, categoryByName, breakdown, openLedgerCategory, openLedgerTag, openLedgerScope, openLedgerMember }) {
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
        : breakdown === 'scope'
          ? <ScopeRanking scopes={summary.scopeSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerScope={openLedgerScope} />
          : breakdown === 'member'
            ? <MemberRanking members={summary.memberSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerMember={openLedgerMember} />
            : <CategoryRanking spends={spends} total={total} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />}
    </section>
  );
}

function PeriodStats({ summary, categoryByName, breakdown, openLedgerCategory, openLedgerTag, openLedgerScope, openLedgerMember }) {
  const spends = summary.categorySpends || [];
  const total = spends.reduce((sum, item) => sum + Number(item.amount), 0);

  return (
    <section className="stats-content period-stats">
      <div className="period-headline">
        <span>{summary.period || ''}</span>
        <strong>{money(summary.expense)}</strong>
      </div>
      {breakdown === 'tag'
        ? <TagRanking tags={summary.tagSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerTag={openLedgerTag} />
        : breakdown === 'scope'
          ? <ScopeRanking scopes={summary.scopeSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerScope={openLedgerScope} />
          : breakdown === 'member'
            ? <MemberRanking members={summary.memberSpends || []} expenseTotal={Number(summary.expense || 0)} openLedgerMember={openLedgerMember} />
            : <CategoryRanking spends={spends} total={total} categoryByName={categoryByName} openLedgerCategory={openLedgerCategory} />}
    </section>
  );
}

function buildChartGradient(spends, total, categoryByName) {
  if (!total) return 'conic-gradient(#f1f7ec 0 100%)';
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

function ScopeRanking({ scopes, expenseTotal, openLedgerScope }) {
  if (!scopes.length) return <EmptyState label="개인/공동 소비 통계가 없습니다." compact />;
  return (
    <div className="ranking-list scope-ranking-list">
      {scopes.map((item, index) => {
        const percent = expenseTotal ? Math.round((Number(item.amount) / expenseTotal) * 100) : 0;
        const color = item.scope === 'SHARED' ? '#9bcf8e' : palette[index % palette.length];
        return (
          <button className="ranking-row scope-ranking-row" type="button" key={item.scope} onClick={() => openLedgerScope(item)}>
            <span className="percent-badge" style={{ backgroundColor: color }}>{percent}%</span>
            <strong>{consumptionScopeLabels[item.scope] || item.scope}<small>{item.transactionCount}건</small></strong>
            <b>{money(item.amount)}</b>
          </button>
        );
      })}
    </div>
  );
}

function MemberRanking({ members, expenseTotal, openLedgerMember }) {
  if (!members.length) return <EmptyState label="명의별 소비 통계가 없습니다." compact />;
  return (
    <div className="ranking-list member-ranking-list">
      {members.map((item, index) => {
        const percent = expenseTotal ? Math.round((Number(item.amount) / expenseTotal) * 100) : 0;
        const color = palette[(index + 2) % palette.length];
        return (
          <button className="ranking-row member-ranking-row" type="button" key={item.memberId || item.memberName} onClick={() => openLedgerMember(item)}>
            <span className="percent-badge" style={{ backgroundColor: color }}>{percent}%</span>
            <strong>{item.memberName || '명의 미지정'}<small>{item.transactionCount}건</small></strong>
            <b>{money(item.amount)}</b>
          </button>
        );
      })}
    </div>
  );
}

function BudgetStats({ summary, categoryByName, openBudgetSettings }) {
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
      <WeeklyBudgetStats weeks={summary.weeklyTotals || []} />
    </section>
  );
}

function WeeklyBudgetStats({ weeks }) {
  const maxExpense = Math.max(1, ...weeks.map((item) => Number(item.expense || 0)));
  return (
    <section className="weekly-budget-stats">
      <header>
        <strong>주간별 지출</strong>
        <span>월 내 일요일 기준</span>
      </header>
      <div>
        {weeks.map((item) => (
          <article className="weekly-budget-row" key={item.weekIndex}>
            <span>{item.weekIndex}주</span>
            <div><i style={{ width: `${Math.round((Number(item.expense || 0) / maxExpense) * 100)}%` }} /></div>
            <b>{money(item.expense)}</b>
            <small>{Number(item.startDate.slice(8, 10))}일~{Number(item.endDate.slice(8, 10))}일 · {item.transactionCount}건</small>
          </article>
        ))}
      </div>
    </section>
  );
}

function YearlyBudgetStats({ summary }) {
  const usage = Math.min(100, Number(summary.budgetUsageRate || 0));
  return (
    <section className="budget-screen yearly-budget-screen">
      <div className="budget-headline">
        <div>
          <span>{summary.year || ''}년 남은 예산</span>
          <strong>{money(summary.remainingBudget)}</strong>
        </div>
      </div>
      <div className="budget-track">
        <span>연간 예산</span>
        <ProgressBar value={usage} marker />
        <div>
          <b>{money(summary.budget)}</b>
          <em>{usage}%</em>
        </div>
      </div>
      <div className="yearly-budget-list">
        {(summary.monthlyUsages || []).map((item) => (
          <article className={`yearly-budget-row ${item.exceeded ? 'over' : ''}`} key={item.month}>
            <span>{Number(item.month.slice(5, 7))}월</span>
            <div>
              <strong>{money(item.expense)}</strong>
              <small>예산 {money(item.budget)}</small>
            </div>
            <b>{item.budgetUsageRate}%</b>
          </article>
        ))}
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
