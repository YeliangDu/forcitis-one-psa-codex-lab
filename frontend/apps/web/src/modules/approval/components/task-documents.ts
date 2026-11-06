import type { ComponentType } from 'react';

export interface TaskDocumentProps {
  documentId: string;
}

const registry = new Map<string, ComponentType<TaskDocumentProps>>();

/**
 * A business module shows its own document on the approval task page by registering a read-only component for its document type
 * (from its own index.ts). Approval actions, history and permissions stay in the task page.
 */
export function registerTaskDocument(documentType: string, component: ComponentType<TaskDocumentProps>): void {
  if (registry.has(documentType)) throw new Error(`task document type ${documentType} is already registered`);
  registry.set(documentType, component);
}

export function taskDocumentOf(documentType: string): ComponentType<TaskDocumentProps> | undefined {
  return registry.get(documentType);
}
