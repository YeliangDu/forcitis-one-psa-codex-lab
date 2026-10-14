import type { Schema } from '@forcitis/api';

export const NUMBERING_UPDATE_PERMISSION = 'platform.numbering:numbering-rule:update';
export const NUMBERING_APPROVE_PERMISSION = 'platform.numbering:numbering-rule:approve';

export type NumberingRule = Schema<'NumberingRuleResponse'>;
export type PendingTemplate = Schema<'PendingTemplate'>;

export const RESET_POLICY_LABELS: Readonly<Record<string, string>> = {
  NEVER: '不重置',
  YEARLY: '每年重置',
  MONTHLY: '每月重置',
  DAILY: '每日重置',
};

export const RESET_POLICY_OPTIONS = Object.entries(RESET_POLICY_LABELS).map(([value, label]) => ({ value, label }));

/** A template waits for confirmation while the rule has a pending version (also the voucher number template, whose finance confirmation no role can give in 1a). */
export function isPendingConfirmation(rule: NumberingRule): boolean {
  return rule.pending !== undefined || rule.confirmationStatus === 'PENDING_CONFIRMATION';
}

/**
 * The author cannot confirm their own version, and a version that already has its confirmation (a voucher number template then waits for the finance
 * confirmation, which no role can give in 1a) offers nothing; the server checks again (PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR, _CONFIRMATION_REPEATED).
 */
export function canOfferConfirmation(rule: NumberingRule, userId: string | undefined, mayApprove: boolean): boolean {
  const pending = rule.pending;
  if (!mayApprove || pending === undefined || userId === undefined) return false;
  return pending.confirmedBy === undefined && pending.authorId !== userId;
}

export interface RuleDraft {
  pattern: string;
  resetPolicy: string;
  startValue: string;
  effectiveFrom: string;
}

/** The author starts from the version that waits for confirmation, or else from the one in force, and always fills in the date it takes effect. */
export function draftOf(rule: NumberingRule): RuleDraft {
  const source = rule.pending ?? rule;
  return { pattern: source.pattern, resetPolicy: source.resetPolicy, startValue: String(source.startValue), effectiveFrom: '' };
}

export type DraftProblems = Partial<Record<keyof RuleDraft, string>>;

/** ISO dates compare as text; `today` is the business date (Shanghai). */
export function draftProblems(draft: RuleDraft, today: string): DraftProblems {
  const problems: DraftProblems = {};
  if (draft.pattern.trim() === '') problems.pattern = '请填写编号模板';
  if (!/^\d+$/.test(draft.startValue) || Number(draft.startValue) < 1) problems.startValue = '起始流水号须为正整数';
  if (draft.effectiveFrom === '') problems.effectiveFrom = '请填写生效日期';
  else if (draft.effectiveFrom < today) problems.effectiveFrom = '生效日期不能早于今天';
  return problems;
}
