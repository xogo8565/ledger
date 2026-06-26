import React from 'react';

const navigationTabs = [
  ['ledger', '▤', '6. 22.'],
  ['stats', '▥', '통계'],
  ['assets', '◎', '자산'],
  ['more', '···', '더보기']
];

export function AppShell({
  activeTab,
  onTabChange,
  onAdd,
  showAddAction = false,
  content,
  children
}) {
  return (
    <main className="page-frame">
      <section className="phone-shell" aria-label="편한가계부 미리보기">
        {content}
        {showAddAction && (
          <div className="floating-actions">
            <button className="fab" type="button" onClick={onAdd} aria-label="거래 추가">
              +
            </button>
          </div>
        )}
        <BottomNav active={activeTab} onChange={onTabChange} />
        {children}
      </section>
    </main>
  );
}

function BottomNav({ active, onChange }) {
  return (
    <nav className="bottom-nav" aria-label="하단 메뉴">
      {navigationTabs.map(([key, icon, label]) => (
        <button key={key} type="button" className={active === key ? 'active' : ''} onClick={() => onChange(key)}>
          <span>{icon}</span>
          {label}
        </button>
      ))}
    </nav>
  );
}
