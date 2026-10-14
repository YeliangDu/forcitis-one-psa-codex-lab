import { businessToday, describeFailure, labelOf, useCurrentUser, versionOf } from '@forcitis/core';
import { Button, Dialog, LoadingState, Notice, PageTitle, SelectField, StatusBadge } from '@forcitis/ui';
import { useState } from 'react';
import { useMaintainedYears, useSaveWorkCalendar, useWorkCalendarYear } from '../hooks/use-work-calendar';
import {
  type DayKind,
  EFFECTIVE_KIND_LABELS,
  type EffectiveKind,
  type Exceptions,
  WEEKDAY_HEADERS,
  WORK_CALENDAR_UPDATE_PERMISSION,
  type WorkCalendarYear,
  changedDates,
  effectiveKind,
  entriesOf,
  exceptionsOf,
  isLocked,
  kindChoices,
  monthsOf,
  withKind,
  yearChoices,
} from '../model/work-calendar';
import './work-calendar-page.css';

const LEGEND_TONES: Readonly<Record<EffectiveKind, 'posted' | 'pending' | 'neutral'>> = {
  HOLIDAY: 'posted',
  MAKE_UP_WORKDAY: 'pending',
  REST_DAY: 'neutral',
  WORKDAY: 'neutral',
};

const DAY_CLASSES: Readonly<Record<string, string>> = {
  HOLIDAY: 'work-calendar__day--holiday',
  MAKE_UP_WORKDAY: 'work-calendar__day--make-up',
  REST_DAY: 'work-calendar__day--rest',
  WORKDAY: '',
};

interface EditorProps {
  year: number;
  saved: WorkCalendarYear | null;
  mayUpdate: boolean;
  today: string;
  save: ReturnType<typeof useSaveWorkCalendar>;
}

function CalendarEditor({ year, saved, mayUpdate, today, save }: EditorProps) {
  const savedExceptions = exceptionsOf(saved);
  const [draft, setDraft] = useState<Exceptions>(savedExceptions);
  const [editing, setEditing] = useState<string>();
  const [choice, setChoice] = useState<DayKind | ''>('');
  const [confirming, setConfirming] = useState(false);
  const [discarded, setDiscarded] = useState(false);
  const [seenVersion, setSeenVersion] = useState(versionOf(saved));
  const [base, setBase] = useState<Exceptions>(savedExceptions);
  const changed = changedDates(savedExceptions, draft);
  if (seenVersion !== versionOf(saved)) {
    setSeenVersion(versionOf(saved));
    setBase(savedExceptions);
    if (changedDates(base, savedExceptions).length > 0 && changed.length > 0) {
      setDraft(savedExceptions);
      setDiscarded(true);
    }
  }
  const lockedChanges = changed.filter((date) => isLocked(date, today));

  const send = (lockedDaysConfirmed: boolean) => {
    save.mutate({ days: entriesOf(draft), lockedDaysConfirmed, version: versionOf(saved), firstMaintenance: saved === null }, { onSuccess: () => setConfirming(false) });
  };
  const open = (date: string) => {
    const current = draft.get(date);
    setChoice(current === 'HOLIDAY' || current === 'MAKE_UP_WORKDAY' ? current : '');
    setEditing(date);
  };
  const apply = () => {
    if (editing !== undefined) setDraft((current) => withKind(current, editing, choice));
    setDiscarded(false);
    setEditing(undefined);
  };

  return (
    <>
      <div className="work-calendar__legend">
        {(Object.keys(EFFECTIVE_KIND_LABELS) as EffectiveKind[]).map((kind) => (
          <StatusBadge key={kind} tone={LEGEND_TONES[kind]}>{EFFECTIVE_KIND_LABELS[kind]}</StatusBadge>
        ))}
      </div>
      <p>已锁定的日期显示为灰色，修改需要确认。</p>
      <div className="work-calendar__months">
        {monthsOf(year).map((month) => (
          <section key={month.month} className="work-calendar__month">
            <h2 className="work-calendar__month-title">{month.month}</h2>
            <div className="work-calendar__days">
              {WEEKDAY_HEADERS.map((header) => <span key={header} className="work-calendar__weekday">{header}</span>)}
              {Array.from({ length: month.leadingBlanks }, (_, index) => <span key={`blank-${index}`} />)}
              {month.dates.map((date) => {
                const kind = effectiveKind(date, draft);
                const locked = isLocked(date, today);
                const edited = changed.includes(date);
                return (
                  <button
                    key={date}
                    type="button"
                    className={['work-calendar__day', DAY_CLASSES[kind] ?? '', locked ? 'work-calendar__day--locked' : '', edited ? 'work-calendar__day--edited' : ''].filter(Boolean).join(' ')}
                    aria-label={`${date} ${labelOf(EFFECTIVE_KIND_LABELS, kind)}${locked ? ' 已锁定' : ''}`}
                    disabled={!mayUpdate}
                    onClick={() => open(date)}
                  >
                    {Number(date.slice(8))}
                  </button>
                );
              })}
            </div>
          </section>
        ))}
      </div>
      {discarded ? <Notice tone="warning" title="已载入最新内容，你的修改未保存" /> : null}
      {save.isError && !confirming ? <Notice tone="error" title="保存失败">{describeFailure(save.error)}</Notice> : null}
      {save.isSuccess && changed.length === 0 ? <Notice tone="success" title="已保存" /> : null}
      {mayUpdate
        ? (
          <>
            <Button loading={save.isPending && !confirming} disabled={changed.length === 0} onClick={() => (lockedChanges.length > 0 ? setConfirming(true) : send(false))}>保存修改</Button>
            <Button variant="default" disabled={changed.length === 0} onClick={() => setDraft(savedExceptions)}>取消修改</Button>
          </>
        )
        : null}
      <Dialog open={editing !== undefined} title={editing ?? ''} confirmText="确定" onConfirm={apply} onClose={() => setEditing(undefined)}>
        {editing !== undefined && isLocked(editing, today) ? <Notice tone="warning" title="这个日期已锁定">保存时需要确认。</Notice> : null}
        {editing === undefined ? null : <SelectField label="日期类型" value={choice} options={kindChoices(editing)} onChange={(value) => setChoice(value === 'HOLIDAY' || value === 'MAKE_UP_WORKDAY' ? value : '')} />}
      </Dialog>
      <Dialog
        open={confirming}
        title="确认修改已锁定的日期"
        confirmText="确认修改"
        confirmLoading={save.isPending}
        onConfirm={() => send(true)}
        onClose={() => setConfirming(false)}
      >
        {save.isError ? <Notice tone="error" title="保存失败">{describeFailure(save.error)}</Notice> : null}
        <p>修改下面的日期会影响计算结果。</p>
        <ul>{lockedChanges.map((date) => <li key={date}>{date}</li>)}</ul>
      </Dialog>
    </>
  );
}

export function WorkCalendarPage() {
  const session = useCurrentUser();
  const today = businessToday(new Date());
  const [year, setYear] = useState(Number(today.slice(0, 4)));
  const years = useMaintainedYears();
  const calendar = useWorkCalendarYear(year);
  const save = useSaveWorkCalendar(year);

  if (years.isPending) return <LoadingState />;
  if (years.isError) return <Notice tone="error" title="工作日历加载失败">{describeFailure(years.error)}</Notice>;

  return (
    <>
      <PageTitle text="工作日历" />
      <SelectField label="年度" value={String(year)} options={yearChoices(years.data, today).map((choice) => ({ value: String(choice), label: String(choice) }))} onChange={(value) => setYear(Number(value))} />
      {calendar.isPending ? <LoadingState /> : null}
      {calendar.isError ? <Notice tone="error" title="工作日历加载失败">{describeFailure(calendar.error)}</Notice> : null}
      {calendar.isSuccess && calendar.data === null ? <Notice tone="info" title="这个年度还没有维护工作日历">保存之后开始使用。</Notice> : null}
      {calendar.isSuccess
        ? <CalendarEditor key={year} year={year} saved={calendar.data} mayUpdate={session.can(WORK_CALENDAR_UPDATE_PERMISSION)} today={today} save={save} />
        : null}
    </>
  );
}
