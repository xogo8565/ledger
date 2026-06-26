import { AppHeader, BackButton, EmptyState, IconButton } from '../components/ui';

export function MoreScreen({
  exportMonthlyTransactions,
  openCategoryManager,
  openMemberManager
}) {
  return (
    <div className="screen more-screen">
      <AppHeader title="더보기" />
      <section className="more-list">
        <button type="button" onClick={openCategoryManager}>카테고리 관리<span>›</span></button>
        <button type="button" onClick={openMemberManager}>명의 관리<span>›</span></button>
        <button type="button" onClick={exportMonthlyTransactions}>월 거래 CSV 내보내기<span>›</span></button>
      </section>
    </div>
  );
}

export function MemberManagerScreen({
  members,
  form,
  setForm,
  editingMember,
  setEditingMember,
  saveMember,
  deleteMember,
  consumerMigration,
  migrateUnassignedPersonalExpenses,
  onClose
}) {
  return (
    <div className="full-panel">
      <section className="member-manager">
        <AppHeader title="명의 관리" left={<BackButton label="더보기" onClick={onClose} />} />
        <p className="member-manager-note">자산과 개인 소비에 사용할 명의를 관리합니다. 기본 명의는 삭제할 수 없습니다.</p>
        <form className="member-inline-form" onSubmit={saveMember}>
          <input
            value={form.name}
            onChange={(event) => setForm({ name: event.target.value })}
            placeholder="명의 이름"
            required
          />
          <button type="submit">{editingMember ? '수정' : '추가'}</button>
          {editingMember && (
            <button type="button" className="secondary" onClick={() => {
              setEditingMember(null);
              setForm({ name: '' });
            }}>취소</button>
          )}
        </form>
        <section className="consumer-migration-card" aria-label="기존 개인 지출 명의 연결">
          <div>
            <strong>기존 개인 지출 명의 연결</strong>
            <span>
              {consumerMigration?.eligibleCount
                ? `명의 미지정 개인 지출 ${consumerMigration.eligibleCount}건이 있습니다.`
                : '명의가 비어 있는 개인 지출이 없습니다.'}
            </span>
          </div>
          <button
            type="button"
            disabled={!consumerMigration?.eligibleCount}
            onClick={migrateUnassignedPersonalExpenses}
          >
            {consumerMigration?.ownerMemberName || '기본 명의'}로 연결
          </button>
        </section>
        <div className="member-list">
          {members.map((member) => (
            <article className="member-row" key={member.id}>
              <div>
                <strong>{member.name}</strong>
                <span>{member.role === 'OWNER' ? '기본 명의' : '일반 명의'}</span>
              </div>
              <button type="button" onClick={() => {
                setEditingMember(member);
                setForm({ name: member.name });
              }}>수정</button>
              <button type="button" className="danger" disabled={!member.deletable} onClick={() => deleteMember(member)}>
                삭제
              </button>
            </article>
          ))}
          {!members.length && <EmptyState label="등록된 명의가 없습니다." compact />}
        </div>
      </section>
    </div>
  );
}

export function CategoryManagerScreen({
  categories,
  categoryType,
  setCategoryType,
  form,
  setForm,
  editingCategory,
  setEditingCategory,
  resetCategoryForm,
  saveCategory,
  deleteCategory,
  onClose
}) {
  const list = categories.filter((category) => category.type === categoryType);
  return (
    <div className="full-panel">
      <section className="category-manager">
        <AppHeader
          title={categoryType === 'EXPENSE' ? '지출' : '수입'}
          left={<BackButton label="설정" onClick={onClose} />}
          right={<IconButton label="추가" onClick={() => {
            setEditingCategory(null);
            resetCategoryForm();
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
                setForm({ type: category.type, name: category.name, icon: category.icon || '', color: category.color || '#b8875d' });
              }}>✎</button>
              <span>☰</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
