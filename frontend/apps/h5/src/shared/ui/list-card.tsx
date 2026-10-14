import { Text, View } from '@tarojs/components';
import type { ReactNode } from 'react';
import './list-card.css';

export interface ListCardProps {
  title: string;
  subtitle: string;
  note?: string;
  footnote: string;
  tag: ReactNode;
  badge?: string | undefined;
  onOpen: (() => void) | undefined;
}

export function ListCard({ title, subtitle, note, footnote, tag, badge, onOpen }: ListCardProps) {
  return (
    <View className={`list-card${onOpen === undefined ? ' list-card--inert' : ''}`} {...(onOpen === undefined ? {} : { onClick: onOpen })}>
      <View className="list-card__head">
        <Text className="list-card__title">{title}</Text>
        {tag}
      </View>
      <Text className="list-card__line">{subtitle}</Text>
      {note === undefined ? null : <Text className="list-card__line">{note}</Text>}
      <View className="list-card__foot">
        <Text className="list-card__line">{footnote}</Text>
        {badge === undefined ? null : <Text className="list-card__badge">{badge}</Text>}
      </View>
    </View>
  );
}
