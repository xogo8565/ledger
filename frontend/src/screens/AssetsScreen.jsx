import { useMemo } from 'react';
import { AppHeader, BackButton, EmptyState, IconButton, LineField, MoneyInput } from '../components/ui';
import { Metric } from './LedgerScreen';
import { money, numberOnly } from '../utils/format';

export const assetTypeLabels = {
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
export function AssetsScreen({ data, loading, openAssetForm, openCardPaymentManager, deleteAsset }) {
  const summary = data.assetSummary || {
    totalAssets: data.summary?.assetTotal,
    totalLiabilities: data.summary?.liabilityTotal,
    netWorth: data.summary?.netWorth,
    owners: []
  };
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
        <Metric label="자산" value={numberOnly(summary.totalAssets)} tone="income" />
        <Metric label="부채" value={`-${numberOnly(summary.totalLiabilities)}`} tone="expense" />
        <Metric label="합계" value={numberOnly(summary.netWorth)} tone="total" />
      </section>
      {loading ? (
        <EmptyState label="불러오는 중입니다." />
      ) : (
        <>
          <section className="owner-asset-summary" aria-label="명의별 자산 요약">
            <h2>명의별 자산</h2>
            {(summary.owners || []).map((owner) => (
              <article className="owner-summary-row" key={owner.ownerName}>
                <div>
                  <strong>{owner.ownerName}</strong>
                  <span>{owner.assetCount}개 자산</span>
                </div>
                <dl>
                  <div><dt>자산</dt><dd className="income">{money(owner.totalAssets)}</dd></div>
                  <div><dt>부채</dt><dd className="expense">{money(owner.totalLiabilities)}</dd></div>
                  <div><dt>순자산</dt><dd>{money(owner.netWorth)}</dd></div>
                </dl>
              </article>
            ))}
            {!(summary.owners || []).length && <EmptyState label="명의별 자산이 없습니다." compact />}
          </section>
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
        </>
      )}
    </div>
  );
}

export function CardPaymentManagerScreen({ card, detail, schedules, form, setForm, saveSchedule, executeSchedule, retrySchedule, rescheduleSchedule, cancelSchedule, onClose }) {
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
            <MoneyInput value={form.amount} onValueChange={(amount) => setForm((prev) => ({ ...prev, amount }))} placeholder="0" required />
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

export function AssetFormScreen({ form, setForm, assets, members, editingAsset, saveAsset, onClose }) {
  const paymentAccounts = assets.filter((asset) => asset.type !== 'CARD' && asset.type !== 'DEBT');
  const legacyOwner = form.ownerName && !members.some((member) => member.name === form.ownerName)
    ? form.ownerName
    : '';
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
            <select value={form.ownerName} onChange={(event) => setForm((prev) => ({ ...prev, ownerName: event.target.value }))}>
              <option value="">명의 미지정</option>
              {legacyOwner && <option value={legacyOwner} disabled>{legacyOwner} · 명의 등록 필요</option>}
              {members.map((member) => (
                <option value={member.name} key={member.id}>
                  {member.name}{member.role === 'OWNER' ? ' · 기본' : ''}
                </option>
              ))}
            </select>
          </LineField>
          <LineField label="금액">
            <MoneyInput value={form.balance} onValueChange={(balance) => setForm((prev) => ({ ...prev, balance }))} />
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

