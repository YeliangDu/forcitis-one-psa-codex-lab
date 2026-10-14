import type { Schema } from '@forcitis/api';

export const NUMBERING_UPDATE_PERMISSION = 'platform.numbering:numbering-rule:update';

export type NumberingRule = Schema<'NumberingRuleResponse'>;

export const RESET_POLICY_LABELS: Readonly<Record<string, string>> = {
  NEVER: '不重置',
  YEARLY: '每年重置',
  MONTHLY: '每月重置',
  DAILY: '每日重置',
};

export const RESET_POLICY_OPTIONS = Object.entries(RESET_POLICY_LABELS).map(([value, label]) => ({ value, label }));

export function isPendingConfirmation(rule: NumberingRule): boolean {
  return rule.confirmationStatus === 'PENDING_CONFIRMATION';
}

export interface RuleDraft {
  pattern: string;
  resetPolicy: string;
  startValue: string;
}

export function draftOf(rule: NumberingRule): RuleDraft {
  return { pattern: rule.pattern, resetPolicy: rule.resetPolicy, startValue: String(rule.startValue) };
}

export function draftProblems(draft: RuleDraft): { pattern?: string; startValue?: string } {
  const problems: { pattern?: string; startValue?: string } = {};
  if (draft.pattern.trim() === '') problems.pattern = '请填写编号模板';
  if (!/^\d+$/.test(draft.startValue) || Number(draft.startValue) < 1) problems.startValue = '起始流水号须为正整数';
  return problems;
}
