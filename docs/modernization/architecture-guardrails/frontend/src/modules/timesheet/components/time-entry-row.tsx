import { formatHours } from '@/shared/lib/format-hours';
import './time-entry-row.css';

export function TimeEntryRow({ hours }: { hours: number }) {
  return <span className="time-entry-row">{formatHours(hours)}</span>;
}
