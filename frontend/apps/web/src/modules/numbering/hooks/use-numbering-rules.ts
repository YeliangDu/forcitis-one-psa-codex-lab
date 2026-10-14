import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchNumberingRules, saveNumberingRule } from '../api/numbering-api';
import type { RuleDraft } from '../model/numbering-rule';

const KEY = ['numbering', 'rules'] as const;

export function useNumberingRules() {
  return useQuery({ queryKey: KEY, queryFn: fetchNumberingRules });
}

export function useSaveNumberingRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ documentType, draft }: { documentType: string; draft: RuleDraft }) => saveNumberingRule(documentType, draft),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  });
}
