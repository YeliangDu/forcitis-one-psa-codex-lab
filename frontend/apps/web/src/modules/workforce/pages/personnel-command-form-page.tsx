import { describeFailure } from '@forcitis/core';
import { Button, LoadingState, Notice, PageTitle, SelectField, TextField } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useEmployeeChoices, useEnterPersonnelCommand, useLegalEntityChoices, useOrgUnits, usePositionChoices } from '../hooks/use-workforce';
import {
  COMMAND_KIND_LABELS,
  type CommandDraft,
  EMPTY_DRAFT,
  FEE_EARNER_OPTIONS,
  GENDER_OPTIONS,
  KIND_FIELDS,
  PHASE_OPTIONS,
  SUBMITTABLE_KINDS,
  bodyOf,
  personnelCommandPath,
  submittableKindOf,
} from '../model/personnel-command';

const NONE = { value: '', label: '请选择' };
const KIND_OPTIONS = SUBMITTABLE_KINDS.map((kind) => ({ value: kind, label: COMMAND_KIND_LABELS[kind] ?? kind }));

export function PersonnelCommandFormPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [kind, setKind] = useState(submittableKindOf('TRANSFER'));
  const [draft, setDraft] = useState<CommandDraft>({ ...EMPTY_DRAFT, employeeId: params.get('employeeId') ?? '' });
  const employees = useEmployeeChoices();
  const units = useOrgUnits(false);
  const positions = usePositionChoices();
  const legalEntities = useLegalEntityChoices();
  const enter = useEnterPersonnelCommand();

  const fields = KIND_FIELDS[kind];
  const set = (key: keyof CommandDraft) => (value: string) => setDraft((current) => ({ ...current, [key]: value }));
  const choices = [employees, units, positions, legalEntities];
  const failed = choices.find((query) => query.isError);
  if (failed?.isError) return <Notice tone="error" title="加载失败">{describeFailure(failed.error)}</Notice>;
  if (choices.some((query) => query.isPending)) return <LoadingState />;

  const employeeOptions = [NONE, ...(employees.data ?? []).map((employee) => ({ value: employee.employeeId, label: `${employee.displayName}（${employee.employeeCode}）` }))];
  const unitOptions = [NONE, ...(units.data ?? []).map((unit) => ({ value: unit.orgUnitId, label: unit.name }))];
  const positionOptions = [NONE, ...(positions.data ?? []).map((position) => ({ value: position.positionId, label: position.name }))];
  const legalEntityOptions = [NONE, ...(legalEntities.data ?? []).map((entity) => ({ value: entity.legalEntityId, label: entity.name }))];
  const reportsToOptions = employeeOptions;

  const submit = () => enter.mutate(bodyOf(kind, draft), { onSuccess: (created) => void navigate(personnelCommandPath(created.personnelCommandId)) });

  return (
    <>
      <PageTitle text="发起人事命令" />
      <SelectField label="命令类型" value={kind} options={KIND_OPTIONS} onChange={(value) => { setKind(submittableKindOf(value)); enter.reset(); }} />
      {fields.newPerson === true ? (
        <>
          <TextField label="姓名" value={draft.displayName} onChange={set('displayName')} />
          <SelectField label="性别" value={draft.gender} options={[NONE, ...GENDER_OPTIONS]} onChange={set('gender')} />
          <TextField label="企业邮箱" value={draft.email} onChange={set('email')} />
          <TextField label="企业微信账号" value={draft.wecomUserId} onChange={set('wecomUserId')} />
        </>
      ) : (
        <SelectField label="员工" value={draft.employeeId} options={employeeOptions} onChange={set('employeeId')} />
      )}
      <TextField label={fields.date === 'lastWorkingDay' ? '最后工作日' : '生效日期'} type="date" value={draft.date} onChange={set('date')} />
      {fields.orgUnit === undefined ? null : <SelectField label="目标组织单元" value={draft.orgUnitId} options={unitOptions} onChange={set('orgUnitId')} />}
      {fields.position === undefined ? null : <SelectField label="目标岗位" value={draft.positionId} options={positionOptions} onChange={set('positionId')} />}
      {fields.reportsTo === undefined ? null : <SelectField label="目标直属上级" value={draft.reportsToEmployeeId} options={reportsToOptions} onChange={set('reportsToEmployeeId')} />}
      {fields.feeEarner === undefined ? null : <SelectField label="是否计费人员" value={draft.feeEarner} options={[NONE, ...FEE_EARNER_OPTIONS]} onChange={set('feeEarner')} />}
      {fields.phase === undefined ? null : <SelectField label="任职阶段" value={draft.phase} options={[NONE, ...PHASE_OPTIONS]} onChange={set('phase')} />}
      {fields.legalEntity === undefined ? null : <SelectField label="成本法人" value={draft.legalEntityId} options={legalEntityOptions} onChange={set('legalEntityId')} />}
      {enter.isError ? <Notice tone="error" title="提交失败">{describeFailure(enter.error)}</Notice> : null}
      <Button htmlType="submit" loading={enter.isPending} onClick={submit}>提交</Button>
    </>
  );
}
