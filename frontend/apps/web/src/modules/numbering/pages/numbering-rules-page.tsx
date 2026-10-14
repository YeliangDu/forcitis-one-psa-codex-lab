import { DOCUMENT_TYPE_LABELS, describeFailure, versionOf } from '@forcitis/core';
import { Button, DataTable, Dialog, LoadingState, Notice, PageTitle, SelectField, StatusBadge, TextField, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useCurrentUser } from '@/modules/session';
import { useNumberingRuleForEdit, useNumberingRules, useSaveNumberingRule } from '../hooks/use-numbering-rules';
import {
  NUMBERING_UPDATE_PERMISSION,
  type NumberingRule,
  RESET_POLICY_LABELS,
  RESET_POLICY_OPTIONS,
  type RuleDraft,
  draftOf,
  draftProblems,
  isPendingConfirmation,
} from '../model/numbering-rule';

type Editing = { mode: 'edit' | 'confirm'; rule: NumberingRule; draft: RuleDraft };

export function NumberingRulesPage() {
  const session = useCurrentUser();
  const rules = useNumberingRules();
  const save = useSaveNumberingRule();
  const [editing, setEditing] = useState<Editing>();
  const editedRule = useNumberingRuleForEdit(editing?.rule.documentType);
  const mayUpdate = session.can(NUMBERING_UPDATE_PERMISSION);

  if (rules.isPending) return <LoadingState />;
  if (rules.isError) return <Notice tone="error" title="编号规则加载失败">{describeFailure(rules.error)}</Notice>;

  const close = () => {
    setEditing(undefined);
    save.reset();
  };
  const submit = () => {
    if (editing === undefined) return;
    save.mutate({ documentType: editing.rule.documentType, draft: editing.draft, version: versionOf(editedRule.data) }, { onSuccess: close });
  };

  const columns: DataColumn<NumberingRule>[] = [
    { key: 'type', title: '单据类型', render: (rule) => DOCUMENT_TYPE_LABELS[rule.documentType] },
    { key: 'code', title: '单据类型代码', render: (rule) => rule.documentTypeCode ?? '—' },
    { key: 'pattern', title: '编号模板', render: (rule) => <code>{rule.pattern}</code> },
    { key: 'reset', title: '流水号重置周期', render: (rule) => RESET_POLICY_LABELS[rule.resetPolicy] ?? rule.resetPolicy },
    { key: 'start', title: '起始流水号', render: (rule) => rule.startValue },
    { key: 'capacity', title: '编号容量', render: (rule) => rule.capacity },
    {
      key: 'status',
      title: '状态',
      render: (rule) => isPendingConfirmation(rule)
        ? <StatusBadge tone="pending">待确认</StatusBadge>
        : <StatusBadge tone="posted">已确认</StatusBadge>,
    },
    {
      key: 'actions',
      title: '操作',
      render: (rule) => mayUpdate
        ? (
          <>
            <Button variant="default" onClick={() => setEditing({ mode: 'edit', rule, draft: draftOf(rule) })}>编辑</Button>
            {isPendingConfirmation(rule) ? <Button onClick={() => setEditing({ mode: 'confirm', rule, draft: draftOf(rule) })}>确认</Button> : null}
          </>
        )
        : null,
    },
  ];

  const problems = editing === undefined ? {} : draftProblems(editing.draft);
  const setDraft = (change: Partial<RuleDraft>) => setEditing((current) => current === undefined ? current : { ...current, draft: { ...current.draft, ...change } });

  return (
    <>
      <PageTitle text="编号规则" />
      <DataTable columns={columns} rows={rules.data} rowKey={(rule) => rule.documentType} emptyText="暂无编号规则" />
      <Dialog
        open={editing !== undefined}
        title={editing?.mode === 'confirm' ? '确认编号规则' : '编辑编号规则'}
        confirmText={editing?.mode === 'confirm' ? '确认' : '保存'}
        confirmLoading={save.isPending}
        confirmDisabled={Object.keys(problems).length > 0 || !editedRule.isSuccess || editedRule.isFetching}
        onConfirm={submit}
        onClose={close}
      >
        {save.isError ? <Notice tone="error" title="保存失败">{describeFailure(save.error)}</Notice> : null}
        {editedRule.isError ? <Notice tone="error" title="编号规则加载失败">{describeFailure(editedRule.error)}</Notice> : null}
        {editing?.mode === 'confirm' ? <p>确认之后，这个单据类型将按下面的编号规则发号。如需调整，请先修改再确认。</p> : null}
        {editing === undefined ? null : (
          <>
            <TextField label="单据类型" value={DOCUMENT_TYPE_LABELS[editing.rule.documentType]} onChange={() => undefined} disabled />
            <TextField label="编号模板" value={editing.draft.pattern} onChange={(pattern) => setDraft({ pattern })} {...(problems.pattern === undefined ? {} : { error: problems.pattern })} />
            <SelectField label="流水号重置周期" value={editing.draft.resetPolicy} options={RESET_POLICY_OPTIONS} onChange={(resetPolicy) => setDraft({ resetPolicy })} />
            <TextField label="起始流水号" type="number" value={editing.draft.startValue} onChange={(startValue) => setDraft({ startValue })} {...(problems.startValue === undefined ? {} : { error: problems.startValue })} />
          </>
        )}
      </Dialog>
    </>
  );
}
