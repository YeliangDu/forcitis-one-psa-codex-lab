import { operations } from '@forcitis/api';
import { z } from 'zod';
import { type RuleDraft, numberingRuleSchema } from '../model/numbering-rule';

export async function fetchNumberingRules() {
  return z.array(numberingRuleSchema).parse(await operations.getPlatformNumberingRules());
}

type DocumentTypeParam = NonNullable<Parameters<typeof operations.putPlatformNumberingRulesByDocumentType>[0]>['path']['documentType'];

export async function saveNumberingRule(documentType: string, draft: RuleDraft): Promise<void> {
  await operations.putPlatformNumberingRulesByDocumentType({
    path: { documentType: documentType as DocumentTypeParam },
    body: { pattern: draft.pattern.trim(), resetPolicy: draft.resetPolicy, startValue: Number(draft.startValue) },
  });
}
