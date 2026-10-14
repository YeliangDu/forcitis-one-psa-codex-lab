// The ONLY place tokens.css is loaded. app/main.ts imports this once; nobody else imports tokens.css.
import './tokens.css';

export type StatusTone = 'posted' | 'pending' | 'rejected' | 'reversal' | 'neutral' | 'info';
