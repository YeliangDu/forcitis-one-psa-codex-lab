import { operations } from '@forcitis/api';
import type { DocumentType } from '@forcitis/core';
import type { RuleDraft } from '../model/numbering-rule';

export function fetchNumberingRules() {
  return operations.getPlatformNumberingRules();
}

export function fetchNumberingRule(documentType: DocumentType) {
  return operations.getPlatformNumberingRulesByDocumentType({ path: { documentType } });
}

export async function saveNumberingRule(documentType: DocumentType, draft: RuleDraft): Promise<void> {
  await operations.putPlatformNumberingRulesByDocumentType({
    path: { documentType },
    body: { pattern: draft.pattern.trim(), resetPolicy: draft.resetPolicy, startValue: Number(draft.startValue) },
  });
}
