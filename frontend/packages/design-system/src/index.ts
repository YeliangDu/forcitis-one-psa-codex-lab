// The ONLY place tokens.css is loaded as a stylesheet. app/main.tsx imports this once; nobody else imports tokens.css.
import './tokens.css';

export { DesignSystemProvider } from './design-system-provider';
export type StatusTone = 'posted' | 'pending' | 'rejected' | 'reversal' | 'neutral' | 'info';
