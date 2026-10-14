import { failureOf, isVersionConflict, useIntentKeys, useVersionedQuery } from '@forcitis/core';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { confirmNumberingRule, fetchNumberingRule, fetchNumberingRules, saveNumberingRule } from '../api/numbering-api';
import type { RuleDraft } from '../model/numbering-rule';

const KEY = ['numbering', 'rules'] as const;

export function useNumberingRules() {
  return useQuery({ queryKey: KEY, queryFn: fetchNumberingRules });
}

/** Read again every time the editor opens: the save must carry the version of this very reading (If-Match). */
export function useNumberingRuleForEdit(documentType: string | undefined) {
  return useVersionedQuery({
    queryKey: [...KEY, documentType],
    queryFn: documentType === undefined ? skipToken : () => fetchNumberingRule(documentType),
    gcTime: 0,
    refetchOnMount: 'always',
  });
}

export function useSaveNumberingRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ documentType, draft, version }: { documentType: string; draft: RuleDraft; version: string | undefined }) => saveNumberingRule(documentType, draft, version),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
    onError: (error) => {
      if (isVersionConflict(error)) void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}

/** A refused version (409) reads the rules again, so the user sees what the template is now. */
export function useConfirmNumberingRule() {
  const queryClient = useQueryClient();
  const keys = useIntentKeys();
  return useMutation({
    mutationFn: ({ documentType, templateVersion }: { documentType: string; templateVersion: number }) => confirmNumberingRule(documentType, templateVersion, keys.keyFor(`confirm:${documentType}:${templateVersion}`)),
    onSuccess: () => {
      keys.done();
      return queryClient.invalidateQueries({ queryKey: KEY });
    },
    onError: (error) => {
      if (failureOf(error).status !== 409) return;
      keys.done();
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
