import React from 'react';
import { AppHeader, BackButton } from '../components/ui';
import { MonthNav } from './LedgerScreen';

export function BudgetSettingsScreen({
  settings,
  setSettings,
  month,
  setMonth,
  saveBudget,
  copyPreviousBudget,
  onClose
}) {
  const categories = settings?.categories || [];

  return (
    <div className="full-panel">
      <form className="budget-settings-screen" onSubmit={saveBudget}>
        <AppHeader title="예산설정" left={<BackButton label="뒤로" onClick={onClose} />} />
        <MonthNav month={month} setMonth={setMonth} />
        <button className="budget-copy-button" type="button" onClick={copyPreviousBudget}>전월 예산 복사</button>
        <div className="budget-total-edit">
          <span>전체 예산</span>
          <input
            inputMode="numeric"
            value={settings?.totalAmount || 0}
            onChange={(event) => setSettings((prev) => ({ ...prev, totalAmount: event.target.value }))}
          />
        </div>
        <div className="budget-settings-list">
          {categories.map((item, index) => (
            <label key={item.categoryId}>
              <span>{item.categoryIcon || '•'} {item.categoryName}</span>
              <input
                inputMode="numeric"
                value={item.amount}
                onChange={(event) => {
                  const amount = event.target.value;
                  setSettings((prev) => ({
                    ...prev,
                    categories: prev.categories.map((category, categoryIndex) => (
                      categoryIndex === index ? { ...category, amount } : category
                    ))
                  }));
                }}
              />
            </label>
          ))}
        </div>
        <button className="wide-save-button sticky" type="submit">저장</button>
      </form>
    </div>
  );
}
