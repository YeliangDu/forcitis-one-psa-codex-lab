import { operations } from '@forcitis/api';
import type { RuleDraft } from '../model/numbering-rule';

export function fetchNumberingRules() {
  return operations.getPlatformNumberingRules();
}

type DocumentTypeParam = NonNullable<Parameters<typeof operations.putPlatformNumberingRulesByDocumentType>[0]>['path']['documentType'];

export async function saveNumberingRule(documentType: string, draft: RuleDraft): Promise<void> {
  await operations.putPlatformNumberingRulesByDocumentType({
    path: { documentType: documentType as DocumentTypeParam },
    body: { pattern: draft.pattern.trim(), resetPolicy: draft.resetPolicy, startValue: Number(draft.startValue) },
  });
}
