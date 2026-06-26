import { useState } from 'react';
import { AppHeader, BackButton, EmptyState, IconButton, LineField, MoneyInput } from '../components/ui';
import { iconForType, KeyValue, transferLabel } from './LedgerScreen';
import { money, transactionTone } from '../utils/format';
import { formatMoneyInput, formatNumber, parseWonAmount, toNumber } from '../utils/numberValues';
import { normalizeWhitespace, trimToEmpty, uniqueNonBlank } from '../utils/stringValues';

const API = '/api';
const typeLabels = { INCOME: '수입', EXPENSE: '지출', TRANSFER: '이체' };
const consumptionScopeLabels = { PERSONAL: '개인', SHARED: '공동' };

function defaultConsumerMemberId(members) {
  const member = members.find((item) => item.role === 'OWNER') || members[0];
  return member ? String(member.id) : '';
}

function uniqueValues(values) {
  return uniqueNonBlank(values);
}

function extractAmountCandidates(rawText, currentAmount) {
  const values = [];
  if (currentAmount) values.push(String(toNumber(currentAmount)));
  const matcher = String(rawText || '').matchAll(/([0-9][0-9,]{2,})\s*(?:원|won)?/gi);
  for (const match of matcher) {
    const amount = parseWonAmount(match[1]);
    if (amount > 0) values.push(String(amount));
  }
  return uniqueValues(values).slice(0, 6);
}

function formatDateCandidate(year, month, day) {
  const parsedYear = Number(year);
  const parsedMonth = Number(month);
  const parsedDay = Number(day);
  if (!parsedYear || !parsedMonth || !parsedDay) return null;
  if (parsedMonth < 1 || parsedMonth > 12 || parsedDay < 1 || parsedDay > 31) return null;
  const date = new Date(parsedYear, parsedMonth - 1, parsedDay);
  if (date.getFullYear() !== parsedYear || date.getMonth() !== parsedMonth - 1 || date.getDate() !== parsedDay) {
    return null;
  }
  return [
    String(parsedYear).padStart(4, '0'),
    String(parsedMonth).padStart(2, '0'),
    String(parsedDay).padStart(2, '0')
  ].join('-');
}

function extractDateCandidates(rawText, currentDate) {
  const values = [];
  if (currentDate) values.push(currentDate);
  const text = String(rawText || '');
  const currentYear = new Date().getFullYear();

  for (const match of text.matchAll(/\b(20\d{2})[./-]\s*(\d{1,2})[./-]\s*(\d{1,2})\b/g)) {
    values.push(formatDateCandidate(match[1], match[2], match[3]));
  }
  for (const match of text.matchAll(/\b(\d{1,2})[./월]\s*(\d{1,2})\s*(?:일)?\b/g)) {
    values.push(formatDateCandidate(currentYear, match[1], match[2]));
  }

  return uniqueValues(values).slice(0, 6);
}

function extractTitleCandidates(rawText, currentTitle) {
  const text = String(rawText || '');
  const values = [];
  if (currentTitle) values.push(currentTitle);

  const lines = text.split(/\r?\n/)
    .map(normalizeWhitespace)
    .filter(Boolean);
  let itemTableStarted = false;
  for (const line of lines) {
    const compact = line.replace(/\s+/g, '').toLowerCase();
    if (!itemTableStarted && compact.includes('품명') && compact.includes('단가') && compact.includes('수량') && compact.includes('금액')) {
      itemTableStarted = true;
      continue;
    }
    if (itemTableStarted && /합계|총액|결제|받은금액|거스름|부가세|과세|면세|total/i.test(line)) {
      break;
    }
    if (itemTableStarted) {
      const itemName = trimToEmpty(line.split(/[0-9]/)[0].replace(/[·*]/g, ''));
      if (itemName) values.push(itemName);
    }
  }

  for (const line of lines) {
    if (/[0-9][0-9,]*\s*(?:원|won)?/i.test(line)) continue;
    if (/20\d{2}[./-]\d{1,2}[./-]\d{1,2}|\d{1,2}[./월]\s*\d{1,2}/.test(line)) continue;
    if (/영수증|매출전표|사업자|대표|주소|전화|tel|품명|단가|수량|금액|합계|총액|결제|승인|카드|부가세|과세|면세|total/i.test(line)) continue;
    if (line.length >= 2 && line.length <= 40) values.push(line);
  }

  return uniqueValues(values).slice(0, 6);
}

export function EntryChoiceSheet({ openEntry, openClipboardEntry, openReceiptOcr, onClose }) {
  return (
    <div className="sheet-backdrop entry-choice-backdrop" role="presentation" onClick={onClose}>
      <section className="entry-choice-sheet" role="dialog" aria-modal="true" aria-label="거래 입력 방식" onClick={(event) => event.stopPropagation()}>
        <header>
          <div>
            <strong>거래 추가</strong>
            <span>입력 방식을 선택하세요.</span>
          </div>
          <button type="button" onClick={onClose} aria-label="닫기">×</button>
        </header>
        <button type="button" className="entry-choice-button" onClick={() => openEntry('EXPENSE')}>
          <span>✎</span>
          <div>
            <strong>직접 입력</strong>
            <small>수입·지출·이체를 직접 작성합니다.</small>
          </div>
        </button>
        <button type="button" className="entry-choice-button" onClick={openClipboardEntry}>
          <span>▤</span>
          <div>
            <strong>문자 자동 입력</strong>
            <small>클립보드의 카드·은행 문자를 분석합니다.</small>
          </div>
        </button>
        <button type="button" className="entry-choice-button" onClick={openReceiptOcr}>
          <span>⌁</span>
          <div>
            <strong>영수증 자동 입력</strong>
            <small>영수증 사진에서 거래 초안을 만듭니다.</small>
          </div>
        </button>
      </section>
    </div>
  );
}

export function ReceiptOcrScreen({ previewReceiptOcr, parseTransactionText, applyReceiptOcrPreview, onClose }) {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [editedRawText, setEditedRawText] = useState('');
  const [loading, setLoading] = useState(false);
  const [reparsing, setReparsing] = useState(false);
  const [error, setError] = useState('');

  function openManualEntry() {
    applyReceiptOcrPreview({
      rawText: editedRawText,
      preview: {
        type: 'EXPENSE'
      }
    }, null);
  }

  async function analyze(event) {
    event.preventDefault();
    if (!file) {
      setError('OCR로 읽을 영수증 이미지를 선택해 주세요.');
      return;
    }
    setLoading(true);
    setError('');
    setResult(null);
    const response = await previewReceiptOcr(file);
    setLoading(false);
    if (!response.ok) {
      setError(response.data?.message || '영수증 OCR 분석에 실패했습니다.');
      return;
    }
    setResult(response.data);
    setEditedRawText(response.data?.rawText || '');
  }

  async function reparseEditedText() {
    if (!trimToEmpty(editedRawText)) {
      setError('재분석할 OCR 원문을 입력해 주세요.');
      return;
    }
    setReparsing(true);
    setError('');
    const response = await parseTransactionText(editedRawText);
    setReparsing(false);
    if (!response.ok) {
      setError(response.data?.message || '수정한 OCR 원문 재분석에 실패했습니다.');
      return;
    }
    setResult((current) => ({
      ...(current || {}),
      rawText: editedRawText,
      preview: response.data,
      candidates: null,
      warnings: current?.warnings || []
    }));
  }

  const preview = result?.preview || {};
  const serverCandidates = result?.candidates || {};
  const titleCandidates = result
    ? (serverCandidates.titleCandidates?.length ? serverCandidates.titleCandidates : extractTitleCandidates(result.rawText || editedRawText, preview.merchant))
    : [];
  const amountCandidates = result
    ? (serverCandidates.amountCandidates?.length
      ? serverCandidates.amountCandidates.map((candidate) => String(toNumber(candidate)))
      : extractAmountCandidates(result.rawText || editedRawText, preview.amount))
    : [];
  const dateCandidates = result
    ? (serverCandidates.dateCandidates?.length ? serverCandidates.dateCandidates : extractDateCandidates(result.rawText || editedRawText, preview.transactionDate))
    : [];

  function updatePreviewCandidate(patch) {
    setResult((current) => ({
      ...(current || {}),
      preview: {
        ...(current?.preview || {}),
        ...patch
      }
    }));
  }

  return (
    <div className="full-panel">
      <section className="receipt-ocr-screen">
        <AppHeader title="영수증 업로드" left={<BackButton label="닫기" onClick={onClose} />} />
        <form className="receipt-ocr-card" onSubmit={analyze}>
          <strong>Tesseract OCR 자동 입력</strong>
          <p>영수증 사진을 업로드하면 텍스트를 추출하고 거래 입력 초안을 만듭니다.</p>
          <input
            type="file"
            accept="image/*"
            onChange={(event) => {
              setFile(event.target.files?.[0] || null);
              setResult(null);
              setEditedRawText('');
              setError('');
            }}
          />
          <button type="submit" disabled={loading}>{loading ? '분석 중...' : 'OCR 분석'}</button>
          {error && (
            <div className="ocr-error-box">
              <em className="ocr-error">{error}</em>
              <button type="button" className="secondary-action" onClick={openManualEntry}>
                직접 입력으로 전환
              </button>
            </div>
          )}
        </form>

        {result && (
          <section className="receipt-ocr-result">
            <h2>분석 결과</h2>
            <div className="ocr-preview-grid">
              <span>파일</span><strong>{result.originalFilename}</strong>
              <span>날짜</span><strong>{preview.transactionDate || '-'}</strong>
              <span>금액</span><strong>{preview.amount ? formatNumber(preview.amount) : '-'}</strong>
              <span>가맹점</span><strong>{preview.merchant || '-'}</strong>
              <span>추천 분류</span><strong>{preview.recommendedCategoryName || '-'}</strong>
            </div>
            {(titleCandidates.length > 1 || amountCandidates.length > 1 || dateCandidates.length > 1) && (
              <div className="ocr-candidate-card">
                <strong>OCR 후보 선택</strong>
                {dateCandidates.length > 1 && (
                  <div className="ocr-candidate-group">
                    <span>날짜 후보</span>
                    <div className="ocr-candidate-list">
                      {dateCandidates.map((candidate) => (
                        <button
                          key={candidate}
                          type="button"
                          className={preview.transactionDate === candidate ? 'ocr-candidate-chip active' : 'ocr-candidate-chip'}
                          onClick={() => updatePreviewCandidate({ transactionDate: candidate })}
                        >
                          {candidate}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {titleCandidates.length > 1 && (
                  <div className="ocr-candidate-group">
                    <span>내용/품명 후보</span>
                    <div className="ocr-candidate-list">
                      {titleCandidates.map((candidate) => (
                        <button
                          key={candidate}
                          type="button"
                          className={preview.merchant === candidate ? 'ocr-candidate-chip active' : 'ocr-candidate-chip'}
                          onClick={() => updatePreviewCandidate({ merchant: candidate })}
                        >
                          {candidate}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {amountCandidates.length > 1 && (
                  <div className="ocr-candidate-group">
                    <span>금액 후보</span>
                    <div className="ocr-candidate-list">
                      {amountCandidates.map((candidate) => (
                        <button
                          key={candidate}
                          type="button"
                          className={String(toNumber(preview.amount)) === candidate ? 'ocr-candidate-chip active' : 'ocr-candidate-chip'}
                          onClick={() => updatePreviewCandidate({ amount: candidate })}
                        >
                          {formatNumber(candidate)}원
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
            {result.warnings?.length > 0 && (
              <ul className="ocr-warnings">
                {result.warnings.map((warning, index) => <li key={index}>{warning}</li>)}
              </ul>
            )}
            <div className="ocr-help-card">
              <strong>인식이 부정확하면</strong>
              <span>밝은 곳에서 영수증 전체가 나오게 다시 촬영하거나, 아래 OCR 원문에서 품명·합계·금액을 수정한 뒤 다시 분석하세요.</span>
            </div>
            <details>
              <summary>OCR 원문 보기</summary>
              <pre>{result.rawText || '추출된 텍스트가 없습니다.'}</pre>
            </details>
            <textarea
              className="ocr-raw-text-editor"
              value={editedRawText}
              onChange={(event) => setEditedRawText(event.target.value)}
              aria-label="OCR 원문 편집"
              placeholder="OCR로 추출한 텍스트가 여기에 표시됩니다."
            />
            <button type="button" className="secondary-action" onClick={reparseEditedText} disabled={reparsing}>
              {reparsing ? '재분석 중...' : '수정한 원문으로 다시 분석'}
            </button>
            <button type="button" onClick={() => applyReceiptOcrPreview(result, file)}>
              거래 입력에 사용
            </button>
          </section>
        )}
      </section>
    </div>
  );
}

export function TransactionDetailScreen({ transaction, receipts, editTransaction, deleteTransaction, deleteReceipt, openInstallmentSchedule, onClose }) {
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
          {transaction.type === 'EXPENSE' && (
            <KeyValue label="소비 구분" value={consumptionScopeLabels[transaction.consumptionScope] || '개인'} />
          )}
          {transaction.type === 'EXPENSE' && transaction.consumptionScope === 'PERSONAL' && (
            <KeyValue label="소비 명의" value={transaction.consumerMemberName || '기본 명의'} />
          )}
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

export function EntryScreen({
  form,
  expression,
  assets,
  categories,
  members,
  receiptFiles,
  updateForm,
  setReceiptFiles,
  setExpression,
  submitTransaction,
  editingTransaction,
  editingInstallmentGroup,
  installmentReceiptTargetIndex,
  setInstallmentReceiptTargetIndex,
  onClose
}) {
  const tone = form.type === 'INCOME' ? 'income' : form.type === 'TRANSFER' ? 'transfer' : 'expense';
  const isEditing = Boolean(editingTransaction || editingInstallmentGroup);
  const installmentReceiptOptions = Array.from(
    { length: Math.max(Number(form.installmentMonths || 0), 0) },
    (_, index) => index + 1
  );
  const installmentReceiptTargetValue = Math.min(
    Math.max(Number(installmentReceiptTargetIndex || 1), 1),
    Math.max(installmentReceiptOptions.length, 1)
  );
  const amountDisplay = expression || (form.amount ? String(form.amount) : '');
  const amountPreview = amountFromExpression(amountDisplay);

  function setType(type) {
    updateForm('type', type);
    updateForm('categoryId', '');
    if (type !== 'EXPENSE') {
      updateForm('installmentMonths', 0);
      updateForm('spendingTag', '');
      updateForm('consumptionScope', 'PERSONAL');
      updateForm('consumerMemberId', '');
    } else if (!form.consumerMemberId) {
      updateForm('consumerMemberId', defaultConsumerMemberId(members));
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

        <section className={`entry-amount-panel ${tone}`} aria-label="금액">
          <span>금액</span>
          <strong>{formatCalculatorExpression(amountDisplay) || '0'}</strong>
          <em>{amountPreview ? `${formatNumber(amountPreview)}원` : '계산기로 입력'}</em>
        </section>

        <section className={`entry-fields ${tone}`}>
          <LineField label="날짜">
            <input type="date" value={form.transactionDate} onChange={(event) => updateForm('transactionDate', event.target.value)} />
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
                <MoneyInput value={form.fee} onValueChange={(fee) => updateForm('fee', fee)} placeholder="0" />
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
                  <select value={form.installmentMonths} onChange={(event) => {
                    const months = Number(event.target.value);
                    updateForm('installmentMonths', months);
                    if (editingInstallmentGroup && Number(installmentReceiptTargetIndex || 1) > months) {
                      setInstallmentReceiptTargetIndex(Math.max(months, 1));
                    }
                  }}>
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
            <>
              <LineField label="태그">
                <input value={form.spendingTag} onChange={(event) => updateForm('spendingTag', event.target.value)} placeholder="식비, 생활, 고정비" />
              </LineField>
              <LineField label="소비 구분">
                <select value={form.consumptionScope} onChange={(event) => {
                  const scope = event.target.value;
                  updateForm('consumptionScope', scope);
                  updateForm('consumerMemberId', scope === 'PERSONAL' ? form.consumerMemberId || defaultConsumerMemberId(members) : '');
                }}>
                  <option value="PERSONAL">개인 소비</option>
                  <option value="SHARED">공동 소비</option>
                </select>
              </LineField>
              {form.consumptionScope === 'PERSONAL' && (
                <LineField label="소비 명의">
                  <select required value={form.consumerMemberId} onChange={(event) => updateForm('consumerMemberId', event.target.value)}>
                    <option value="">명의 선택</option>
                    {members.map((member) => (
                      <option value={member.id} key={member.id}>
                        {member.name}{member.role === 'OWNER' ? ' · 기본' : ''}
                      </option>
                    ))}
                  </select>
                </LineField>
              )}
            </>
          )}

          {editingInstallmentGroup && (
            <LineField label="첨부 회차">
              <select
                value={installmentReceiptTargetValue}
                onChange={(event) => setInstallmentReceiptTargetIndex(Number(event.target.value))}
              >
                {installmentReceiptOptions.map((index) => (
                  <option value={index} key={index}>{index}회차</option>
                ))}
              </select>
            </LineField>
          )}

          <label className="receipt-compact">
            <strong>{isEditing ? '영수증 추가' : '영수증 사진'}</strong>
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
        </section>

        <CalculatorPad onKey={handleKey} submitLabel={isEditing ? '저장' : '확인'} />
      </form>
    </div>
  );
}

function CalculatorPad({ onKey, submitLabel = '확인' }) {
  const keys = ['+', '-', '×', '÷', '7', '8', '9', '=', '4', '5', '6', '.', '1', '2', '3', '⌫', '', '0', '', submitLabel];
  return (
    <section className="calculator" aria-label="금액 계산기">
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

function formatCalculatorExpression(value) {
  return String(value || '').replace(/\d+/g, (digits) => formatMoneyInput(digits));
}
