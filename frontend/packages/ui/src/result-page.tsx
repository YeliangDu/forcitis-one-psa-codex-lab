import { Result } from 'antd';
import type { ReactNode } from 'react';

type ResultKind = 'forbidden' | 'expired' | 'not-found' | 'error' | 'info';

const STATUS = { forbidden: '403', expired: 'warning', 'not-found': '404', error: 'error', info: 'info' } as const;

interface ResultPageProps {
  kind: ResultKind;
  title: string;
  description?: string;
  actions?: ReactNode;
}

export function ResultPage({ kind, title, description, actions }: ResultPageProps) {
  return <Result status={STATUS[kind]} title={title} {...(description === undefined ? {} : { subTitle: description })} {...(actions === undefined ? {} : { extra: actions })} />;
}
