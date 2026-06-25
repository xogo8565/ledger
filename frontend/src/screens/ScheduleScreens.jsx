import { AppHeader, BackButton, EmptyState, LineField } from '../components/ui';
import { Metric } from './LedgerScreen';
import { formatDate, money, transactionTone } from '../utils/format';

const typeLabels = {
  INCOME: '수입',
  EXPENSE: '지출',
  TRANSFER: '이체'
};

export function emptyRecurringForm(baseDate = formatDate(new Date())) {
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
    startDate: baseDate,
    endDate: '',
    nextRunDate: baseDate
  };
}

export function InstallmentScheduleScreen({ transaction, schedule, editGroup, deleteGroup, onClose }) {
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

export function RecurringManagerScreen({
  rules,
  form,
  setForm,
  editingRule,
  clearEditingRule,
  assets,
  categories,
  saveRule,
  deleteRule,
  editRule,
  generateDue,
  onClose
}) {
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
