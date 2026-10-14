import { describeFailure, useCurrentUser } from '@forcitis/core';
import { Button, DescriptionList, Dialog, LoadingState, Notice, PageTitle, StatusBadge, TextAreaField, TextField } from '@forcitis/ui';
import { useState } from 'react';
import { useParams } from 'react-router';
import { useTimesheet, useTimesheetActions } from '../hooks/use-timesheet';
import {
  EMPTY_DRAFT,
  TIMESHEET_STATUS_LABELS,
  TIMESHEET_STATUS_TONES,
  TIMESHEET_SUBMIT_PERMISSION,
  type TimesheetDraft,
  businessToday,
  draftProblems,
  isEditable,
  submitBodyOf,
} from '../model/timesheet';

export function TimesheetPage() {
  const { timesheetId = '' } = useParams();
  const id = Number(timesheetId);
  const session = useCurrentUser();
  const sheet = useTimesheet(id);
  const actions = useTimesheetActions(id);
  const [draft, setDraft] = useState<TimesheetDraft>(EMPTY_DRAFT);
  const [shown, setShown] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [reason, setReason] = useState('');

  if (!Number.isSafeInteger(id) || id <= 0) return <Notice tone="error" title="工时单加载失败">工时单不存在</Notice>;
  if (sheet.isPending) return <LoadingState />;
  if (sheet.isError) return <Notice tone="error" title="工时单加载失败">{describeFailure(sheet.error)}</Notice>;

  const timesheet = sheet.data;
  const mayEdit = session.can(TIMESHEET_SUBMIT_PERMISSION) && isEditable(timesheet.status);
  const today = businessToday(new Date());
  const problems = draftProblems(draft, today);
  const change = (part: Partial<TimesheetDraft>) => setDraft((current) => ({ ...current, ...part }));
  const hint = (field: keyof TimesheetDraft) => (shown && problems[field] !== undefined ? { error: problems[field] } : {});

  const submit = () => {
    setShown(true);
    if (Object.keys(problems).length > 0) return;
    actions.submit.mutate(submitBodyOf(draft), { onSuccess: () => setDraft(EMPTY_DRAFT) });
  };
  const closeWithdraw = () => {
    setWithdrawing(false);
    setReason('');
    actions.withdraw.reset();
  };

  return (
    <>
      <PageTitle text="工时单" actions={<StatusBadge tone={TIMESHEET_STATUS_TONES[timesheet.status]}>{TIMESHEET_STATUS_LABELS[timesheet.status]}</StatusBadge>} />
      <DescriptionList
        items={[
          { label: '状态', value: TIMESHEET_STATUS_LABELS[timesheet.status] },
          { label: '轮次', value: timesheet.round },
          { label: '工作日期', value: timesheet.workDate },
        ]}
      />
      {timesheet.canWithdraw ? <Button variant="default" onClick={() => setWithdrawing(true)}>撤回</Button> : null}
      {mayEdit
        ? (
          <>
            {actions.submit.isError ? <Notice tone="error" title="提交失败">{describeFailure(actions.submit.error)}</Notice> : null}
            <TextField label="工作日期" type="date" max={today} value={draft.workDate} onChange={(workDate) => change({ workDate })} {...hint('workDate')} />
            <TextField label="工作包" type="number" value={draft.workPackageId} onChange={(workPackageId) => change({ workPackageId })} {...hint('workPackageId')} />
            <TextField label="工时" type="number" value={draft.hours} onChange={(hours) => change({ hours })} {...hint('hours')} />
            <TextField label="项目经理" type="number" value={draft.projectManagerId} onChange={(projectManagerId) => change({ projectManagerId })} {...hint('projectManagerId')} />
            <Button loading={actions.submit.isPending} onClick={submit}>提交</Button>
          </>
        )
        : null}
      <Dialog
        open={withdrawing}
        title="撤回工时单"
        confirmText="确认撤回"
        confirmLoading={actions.withdraw.isPending}
        confirmDisabled={reason.trim() === ''}
        onConfirm={() => actions.withdraw.mutate(reason.trim(), { onSuccess: closeWithdraw })}
        onClose={closeWithdraw}
      >
        {actions.withdraw.isError ? <Notice tone="error" title="撤回失败">{describeFailure(actions.withdraw.error)}</Notice> : null}
        <TextAreaField label="理由（必填）" value={reason} onChange={setReason} />
      </Dialog>
    </>
  );
}
