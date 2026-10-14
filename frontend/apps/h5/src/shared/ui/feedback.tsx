import { Text, View } from '@tarojs/components';
import type { ReactNode } from 'react';
import './feedback.css';

export type Tone = 'posted' | 'pending' | 'rejected' | 'neutral' | 'info';

export function Tag({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <Text className={`tag tag--${tone}`}>{children}</Text>;
}

export function Notice({ tone, title, children }: { tone: 'error' | 'warning'; title: string; children?: ReactNode }) {
  return (
    <View className={`notice notice--${tone}`}>
      <Text className="notice__title">{title}</Text>
      {children === undefined ? null : <Text className="notice__body">{children}</Text>}
    </View>
  );
}

export function LoadingHint({ label }: { label: string }) {
  return (
    <View className="loading-hint">
      <Text>{label}</Text>
    </View>
  );
}

export function EmptyHint({ text }: { text: string }) {
  return (
    <View className="empty-hint">
      <Text>{text}</Text>
    </View>
  );
}
