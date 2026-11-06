import type { Schema } from '@forcitis/api';

export type OrgUnit = Schema<'OrgUnitResponse'>;

export interface OrgUnitNode extends OrgUnit {
  children?: OrgUnitNode[];
}

export function buildOrgTree(units: readonly OrgUnit[]): OrgUnitNode[] {
  const nodes = new Map<string, OrgUnitNode>(units.map((unit) => [unit.orgUnitId, { ...unit }]));
  const roots: OrgUnitNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentOrgUnitId == null ? undefined : nodes.get(node.parentOrgUnitId);
    if (parent === undefined) {
      roots.push(node);
    } else {
      parent.children = [...(parent.children ?? []), node];
    }
  }
  const bySortOrder = (a: OrgUnitNode, b: OrgUnitNode) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
  const sortTree = (list: OrgUnitNode[]): OrgUnitNode[] => list.sort(bySortOrder).map((node) => {
    if (node.children !== undefined) node.children = sortTree(node.children);
    return node;
  });
  return sortTree(roots);
}

export const ORG_UNIT_STATUS_LABELS: Readonly<Record<string, string>> = {
  ACTIVE: '有效',
  REVOKED: '已撤销',
};

export const ORG_UNIT_STATUS_TONES: Readonly<Record<string, 'posted'>> = { ACTIVE: 'posted' };
