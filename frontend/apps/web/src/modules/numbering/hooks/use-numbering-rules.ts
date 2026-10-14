import { type DocumentType, isVersionConflict } from '@forcitis/core';
import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchNumberingRule, fetchNumberingRules, saveNumberingRule } from '../api/numbering-api';
import type { RuleDraft } from '../model/numbering-rule';

const KEY = ['numbering', 'rules'] as const;

export function useNumberingRules() {
  return useQuery({ queryKey: KEY, queryFn: fetchNumberingRules });
}

/** Read again every time the editor opens: the save must carry the version of this very reading (If-Match). */
export function useNumberingRuleForEdit(documentType: DocumentType | undefined) {
  return useQuery({
    queryKey: [...KEY, documentType],
    queryFn: documentType === undefined ? skipToken : () => fetchNumberingRule(documentType),
    gcTime: 0,
    refetchOnMount: 'always',
    structuralSharing: false,
  });
}

export function useSaveNumberingRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ documentType, draft, version }: { documentType: DocumentType; draft: RuleDraft; version: string | undefined }) => saveNumberingRule(documentType, draft, version),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
    onError: (error) => {
      if (isVersionConflict(error)) void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
