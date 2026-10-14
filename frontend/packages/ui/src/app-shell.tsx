import { Layout, Menu } from 'antd';
import type { ComponentProps, ReactNode } from 'react';
import { Button } from './button';
import './app-shell.css';

export interface NavEntry {
  key: string;
  label: string;
  path?: string;
  children?: readonly NavEntry[];
}

interface AppShellProps {
  brand: string;
  navigation: readonly NavEntry[];
  activePath: string;
  onNavigate: (path: string) => void;
  userName: string;
  userDetail?: string;
  logoutText: string;
  onLogout: () => void;
  children: ReactNode;
}

type MenuItems = NonNullable<ComponentProps<typeof Menu>['items']>;

function toMenuItems(entries: readonly NavEntry[]): MenuItems {
  return entries.map((entry) => entry.children !== undefined && entry.children.length > 0
    ? { key: entry.key, label: entry.label, children: toMenuItems(entry.children) }
    : { key: entry.key, label: entry.label });
}

function findByKey(entries: readonly NavEntry[], key: string): NavEntry | undefined {
  for (const entry of entries) {
    if (entry.key === key) return entry;
    const nested = findByKey(entry.children ?? [], key);
    if (nested !== undefined) return nested;
  }
  return undefined;
}

function keyOfPath(entries: readonly NavEntry[], path: string): string | undefined {
  for (const entry of entries) {
    if (entry.path !== undefined && (path === entry.path || path.startsWith(`${entry.path}/`))) return entry.key;
    const nested = keyOfPath(entry.children ?? [], path);
    if (nested !== undefined) return nested;
  }
  return undefined;
}

function allKeys(entries: readonly NavEntry[]): string[] {
  return entries.flatMap((entry) => [entry.key, ...allKeys(entry.children ?? [])]);
}

export function AppShell({ brand, navigation, activePath, onNavigate, userName, userDetail, logoutText, onLogout, children }: AppShellProps) {
  const activeKey = keyOfPath(navigation, activePath);
  return (
    <Layout className="app-shell">
      <Layout.Header className="app-shell__header">
        <span className="app-shell__brand">{brand}</span>
        <span className="app-shell__user">
          <span>{userName}</span>
          {userDetail === undefined ? null : <span className="app-shell__user-detail">{userDetail}</span>}
          <Button variant="default" onClick={onLogout}>{logoutText}</Button>
        </span>
      </Layout.Header>
      <Layout>
        <Layout.Sider width={220} theme="light" className="app-shell__sider">
          <Menu
            mode="inline"
            items={toMenuItems(navigation)}
            defaultOpenKeys={allKeys(navigation)}
            selectedKeys={activeKey === undefined ? [] : [activeKey]}
            onClick={({ key }) => {
              const target = findByKey(navigation, key);
              if (target?.path !== undefined) onNavigate(target.path);
            }}
          />
        </Layout.Sider>
        <Layout.Content className="app-shell__content">{children}</Layout.Content>
      </Layout>
    </Layout>
  );
}
