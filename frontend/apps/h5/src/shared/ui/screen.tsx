import { Text, View } from '@tarojs/components';
import type { ReactNode } from 'react';
import './screen.css';

export function Screen({ children }: { children: ReactNode }) {
  return <View className="screen">{children}</View>;
}

export function ScreenTitle({ text, aside }: { text: string; aside?: ReactNode }) {
  return (
    <View className="screen-title">
      <Text className="screen-title__text">{text}</Text>
      {aside}
    </View>
  );
}

export function Card({ children }: { children: ReactNode }) {
  return <View className="card">{children}</View>;
}

export interface FieldRow {
  label: string;
  value: string;
}

export function FieldList({ rows }: { rows: readonly FieldRow[] }) {
  return (
    <View className="field-list">
      {rows.map((row) => (
        <View key={row.label} className="field-list__row">
          <Text className="field-list__label">{row.label}</Text>
          <Text className="field-list__value">{row.value}</Text>
        </View>
      ))}
    </View>
  );
}
