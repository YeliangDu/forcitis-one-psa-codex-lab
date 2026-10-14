import { DEFAULT_PAGE_LIMIT, collectPages, operations } from '@forcitis/api';
import type { RuleDraft } from '../model/numbering-rule';

/** The server may list a document type this client does not know; the path parameter type is the closed list, so the one narrowing happens here. */
type DocumentTypeParam = NonNullable<Parameters<typeof operations.putPlatformNumberingRulesByDocumentType>[0]>['path']['documentType'];

export function fetchNumberingRules() {
  return collectPages((cursor) => operations.getPlatformNumberingRules({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

export function fetchNumberingRule(documentType: string) {
  return operations.getPlatformNumberingRulesByDocumentType({ path: { documentType: documentType as DocumentTypeParam } });
}

/** `version` is versionOf(the rule as read for editing). */
export async function saveNumberingRule(documentType: string, draft: RuleDraft, version: string | undefined): Promise<void> {
  await operations.putPlatformNumberingRulesByDocumentType({
    path: { documentType: documentType as DocumentTypeParam },
    body: { pattern: draft.pattern.trim(), resetPolicy: draft.resetPolicy, startValue: Number(draft.startValue), effectiveFrom: draft.effectiveFrom },
  }, { ifMatch: version });
}

/** Confirms the template version the user was shown; a version that has changed since answers 409 (PLATFORM_NUMBERING_VERSION_MISMATCH). */
export async function confirmNumberingRule(documentType: string, templateVersion: number, idempotencyKey: string): Promise<void> {
  await operations.postPlatformNumberingRulesByDocumentTypeConfirm({ path: { documentType: documentType as DocumentTypeParam }, body: { templateVersion } }, { idempotencyKey });
}
