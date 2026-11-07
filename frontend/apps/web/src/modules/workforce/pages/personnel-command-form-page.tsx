import { businessToday, describeFailure, useCurrentUser } from '@forcitis/core';
import { Button, LoadingState, Notice, PageTitle, SelectField, TextField } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { optionsOf } from '../../../shared/options';
import { useActingDuties, useEmployeeChoices, useEnterPersonnelCommand, useLegalEntityChoices, useOrgUnits, usePositionChoices } from '../hooks/use-workforce';
import { PERSONNEL_COMMAND_CREATE } from '../model/acting-duty';
import {
  COMMAND_KIND_LABELS,
  type CommandDraft,
  EMPTY_DRAFT,
  FEE_EARNER_OPTIONS,
  GENDER_OPTIONS,
  KIND_FIELDS,
  PHASE_OPTIONS,
  SUBMITTABLE_KINDS,
  type SubmittableKind,
  bodyOf,
  isSubmittableKind,
  personnelCommandPath,
} from '../model/personnel-command';

const NONE = { value: '', label: '请选择' };
const KIND_OPTIONS = SUBMITTABLE_KINDS.map((kind) => ({ value: kind, label: COMMAND_KIND_LABELS[kind] ?? kind }));

export function PersonnelCommandFormPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initialKind = params.get('commandKind') ?? '';
  const [kind, setKind] = useState<SubmittableKind>(isSubmittableKind(initialKind) ? initialKind : 'REGULARIZE');
  const [draft, setDraft] = useState<CommandDraft>({
    ...EMPTY_DRAFT, employeeId: params.get('employeeId') ?? '', targetActingDutyId: params.get('targetActingDutyId') ?? '',
    date: initialKind === 'ACTING_EXTEND' ? businessToday(new Date()) : '',
  });
  const fields = KIND_FIELDS[kind];
  const session = useCurrentUser();
  const needsUnits = fields.orgUnit !== undefined || fields.acting === 'start';
  const needsPositions = fields.position !== undefined || fields.acting === 'start';
  const needsDuty = fields.acting === 'end' || fields.acting === 'extend';
  // Only the lists the chosen kind shows are read: the roles that may submit do not all hold the read of every list.
  const employees = useEmployeeChoices();
  const units = useOrgUnits(false, needsUnits);
  const positions = usePositionChoices(needsPositions);
  const duties = useActingDuties(draft.employeeId, needsDuty);
  const needsLegalEntities = fields.legalEntity !== undefined || fields.employer !== undefined;
  const legalEntities = useLegalEntityChoices(needsLegalEntities);
  const enter = useEnterPersonnelCommand();

  const set = (key: keyof CommandDraft) => (value: string) => setDraft((current) => ({ ...current, [key]: value }));
  const choices = [employees, ...(needsUnits ? [units] : []), ...(needsPositions ? [positions] : []), ...(needsLegalEntities ? [legalEntities] : []), ...(needsDuty && draft.employeeId !== '' ? [duties] : [])];
  const failed = choices.find((query) => query.isError);
  if (fields.acting !== undefined && session.status === 'loading') return <LoadingState />;
  if (fields.acting !== undefined && !session.can(PERSONNEL_COMMAND_CREATE)) return <Notice tone="error" title="没有权限执行这个操作" />;
  if (failed?.isError) return <Notice tone="error" title="加载失败">{describeFailure(failed.error)}</Notice>;
  if (choices.some((query) => query.isPending)) return <LoadingState />;

  const employeeOptions = optionsOf(employees.data ?? [], (employee) => employee.employeeId, (employee) => `${employee.displayName}（${employee.employeeCode}）`, '请选择');
  const unitOptions = optionsOf(units.data ?? [], (unit) => unit.orgUnitId, (unit) => unit.name, '请选择');
  const positionOptions = optionsOf(positions.data ?? [], (position) => position.positionId, (position) => position.name, '请选择');
  const legalEntityOptions = optionsOf(legalEntities.data ?? [], (entity) => entity.legalEntityId, (entity) => entity.name, '请选择');
  const reportsToOptions = employeeOptions;
  const dutyOptions = optionsOf(duties.data?.items.filter((duty) => duty.state === 'ACTIVE') ?? [], (duty) => duty.actingDutyId,
    (duty) => `${duty.firstDay} · ${duty.reason}`, '请选择');

  const changeKind = (value: string) => {
    if (!isSubmittableKind(value)) return;
    setKind(value);
    setDraft((current) => ({
      ...current,
      date: value === 'ACTING_EXTEND' ? businessToday(new Date()) : (kind === 'ACTING_EXTEND' ? '' : current.date),
    }));
    enter.reset();
  };

  const submit = () => {
    if (fields.acting !== undefined && !session.can(PERSONNEL_COMMAND_CREATE)) return;
    if (needsDuty && !duties.data?.items.some((duty) => duty.actingDutyId === draft.targetActingDutyId && duty.state === 'ACTIVE')) return;
    enter.mutate(bodyOf(kind, draft), { onSuccess: (created) => void navigate(personnelCommandPath(created.personnelCommandId)) });
  };

  return (
    <>
      <PageTitle text="发起人事命令" />
      <SelectField label="命令类型" value={kind} options={KIND_OPTIONS} onChange={changeKind} />
      {fields.newPerson === true ? (
        <>
          <TextField label="姓名" value={draft.displayName} onChange={set('displayName')} />
          <SelectField label="性别" value={draft.gender} options={[NONE, ...GENDER_OPTIONS]} onChange={set('gender')} />
          <TextField label="企业邮箱" value={draft.email} onChange={set('email')} />
          <TextField label="企业微信账号" value={draft.wecomUserId} onChange={set('wecomUserId')} />
        </>
      ) : (
        <SelectField label="员工" value={draft.employeeId} options={employeeOptions} onChange={(value) => setDraft((current) => ({ ...current, employeeId: value, targetActingDutyId: '' }))} />
      )}
      <TextField label={fields.date === 'lastWorkingDay' ? '最后工作日' : '生效日期'} type="date" value={draft.date} onChange={set('date')} />
      {fields.acting === 'start' ? (
        <>
          <SelectField label="被代理组织单元" value={draft.actingCoveredOrgUnitId} options={unitOptions} onChange={set('actingCoveredOrgUnitId')} />
          <SelectField label="被代理岗位" value={draft.actingCoveredPositionId} options={positionOptions} onChange={set('actingCoveredPositionId')} />
          <SelectField label="被代理员工" value={draft.actingCoveredEmployeeId} options={employeeOptions} onChange={set('actingCoveredEmployeeId')} />
        </>
      ) : null}
      {needsDuty ? <SelectField label="代理职务" value={draft.targetActingDutyId} options={dutyOptions} onChange={set('targetActingDutyId')} /> : null}
      {fields.acting === 'start' || fields.acting === 'extend' ? <TextField label="最后一天" type="date" value={draft.actingLastDay} onChange={set('actingLastDay')} /> : null}
      {fields.acting !== undefined ? <TextField label="原因" value={draft.reason} onChange={set('reason')} /> : null}
      {fields.orgUnit === undefined ? null : <SelectField label="目标组织单元" value={draft.orgUnitId} options={unitOptions} onChange={set('orgUnitId')} />}
      {fields.position === undefined ? null : <SelectField label="目标岗位" value={draft.positionId} options={positionOptions} onChange={set('positionId')} />}
      {fields.reportsTo === undefined ? null : <SelectField label="目标直属上级" value={draft.reportsToEmployeeId} options={reportsToOptions} onChange={set('reportsToEmployeeId')} />}
      {fields.feeEarner === undefined ? null : <SelectField label="是否计费人员" value={draft.feeEarner} options={[NONE, ...FEE_EARNER_OPTIONS]} onChange={set('feeEarner')} />}
      {fields.phase === undefined ? null : <SelectField label="任职阶段" value={draft.phase} options={[NONE, ...PHASE_OPTIONS]} onChange={set('phase')} />}
      {fields.legalEntity === undefined ? null : <SelectField label="成本法人" value={draft.legalEntityId} options={legalEntityOptions} onChange={set('legalEntityId')} />}
      {fields.employer === undefined ? null : <SelectField label="用人单位" value={draft.employerLegalEntityId} options={legalEntityOptions} onChange={set('employerLegalEntityId')} />}
      {enter.isError ? <Notice tone="error" title="提交失败">{describeFailure(enter.error)}</Notice> : null}
      <Button htmlType="submit" disabled={needsDuty && !duties.data?.items.some((duty) => duty.actingDutyId === draft.targetActingDutyId && duty.state === 'ACTIVE')} loading={enter.isPending} onClick={submit}>提交</Button>
    </>
  );
}
