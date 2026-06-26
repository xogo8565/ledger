import { formatMoneyInput, normalizeMoneyInput } from '../utils/numberValues';

export function AppHeader({ title, left, right }) {
  return (
    <header className="app-header">
      <div>{left}</div>
      <h1>{title}</h1>
      <div>{right}</div>
    </header>
  );
}

export function BackButton({ label = '뒤로', onClick }) {
  return (
    <button className="back-button" type="button" onClick={onClick}>
      ‹ {label}
    </button>
  );
}

export function IconButton({ label, children, onClick }) {
  return (
    <button className="top-icon-button" type="button" aria-label={label} onClick={onClick}>
      {children}
    </button>
  );
}

export function EmptyState({ icon = '▤', label, compact }) {
  return (
    <div className={`empty-state ${compact ? 'compact' : ''}`}>
      <span>{icon}</span>
      <p>{label}</p>
    </div>
  );
}

export function ProgressBar({ value, marker }) {
  return (
    <div className="progress-wrap">
      {marker && <span className="today-marker" style={{ left: `${Math.min(95, Math.max(6, value || 64))}%` }}>오늘</span>}
      <div className="progress-bar">
        <i style={{ width: `${Math.min(100, Number(value || 0))}%` }} />
      </div>
    </div>
  );
}

export function LineField({ label, side, children }) {
  return (
    <label className="line-field">
      <span>{label}</span>
      <div>{children}</div>
      {side && <em>{side}</em>}
    </label>
  );
}

export function MoneyInput({ value, onValueChange, ...props }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="numeric"
      value={formatMoneyInput(value)}
      onChange={(event) => onValueChange(normalizeMoneyInput(event.target.value))}
    />
  );
}
