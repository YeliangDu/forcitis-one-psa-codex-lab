import { Button, Text, Textarea, View } from '@tarojs/components';
import type { ReactNode } from 'react';
import './actions.css';

export interface ActionButtonProps {
  children: ReactNode;
  variant?: 'primary' | 'default' | 'danger';
  disabled?: boolean;
  loading?: boolean;
  onClick?: () => void;
}

/** Taro's Button writes `disabled="false"` as an attribute when it is passed false, and its stylesheet then dims the button; pass the flags only when set. */
export function ActionButton({ children, variant = 'primary', disabled = false, loading = false, onClick }: ActionButtonProps) {
  return (
    <Button
      className={`action-button action-button--${variant}`}
      {...(disabled ? { disabled } : {})}
      {...(loading ? { loading } : {})}
      {...(onClick === undefined ? {} : { onClick })}
    >
      {children}
    </Button>
  );
}

export function ActionBar({ children }: { children: ReactNode }) {
  return <View className="action-bar">{children}</View>;
}

export function ButtonRow({ children }: { children: ReactNode }) {
  return <View className="button-row">{children}</View>;
}

export interface SegmentOption {
  key: string;
  label: string;
}

export function Segments({ options, active, onChange }: { options: readonly SegmentOption[]; active: string; onChange: (key: string) => void }) {
  return (
    <View className="segments">
      {options.map((option) => (
        <Button key={option.key} className={`segments__item${option.key === active ? ' segments__item--active' : ''}`} onClick={() => onChange(option.key)}>
          {option.label}
        </Button>
      ))}
    </View>
  );
}

export interface BottomSheetProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function BottomSheet({ open, title, onClose, children }: BottomSheetProps) {
  if (!open) return null;
  return (
    <View className="bottom-sheet">
      <View className="bottom-sheet__backdrop" onClick={onClose} />
      <View className="bottom-sheet__panel">
        <Text className="bottom-sheet__title">{title}</Text>
        {children}
      </View>
    </View>
  );
}

export interface TextAreaFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function TextAreaField({ label, value, onChange, placeholder = '' }: TextAreaFieldProps) {
  return (
    <View className="text-area-field">
      <Text className="text-area-field__label">{label}</Text>
      <Textarea className="text-area-field__input" value={value} placeholder={placeholder} maxlength={500} onInput={(event) => onChange(event.detail.value)} />
    </View>
  );
}
