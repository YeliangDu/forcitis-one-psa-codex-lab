import type { NavEntry } from '@forcitis/ui';

interface NavigationNode {
  name: string;
  routePath?: string | null | undefined;
  children?: NavigationNode[] | null | undefined;
}

export function toNavEntries(nodes: readonly NavigationNode[], parentKey = ''): NavEntry[] {
  return nodes.map((node) => {
    const key = `${parentKey}/${node.name}`;
    const children = toNavEntries(node.children ?? [], key);
    return {
      key,
      label: node.name,
      ...(node.routePath === null || node.routePath === undefined ? {} : { path: node.routePath }),
      ...(children.length === 0 ? {} : { children }),
    };
  });
}
