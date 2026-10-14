import { useRemainingHours } from '@/modules/delivery';
import { Button } from '@/shared/ui/button';
import { PageTitle } from '@/shared/ui/page-title';
import { StatusBadge } from '@/shared/ui/status-badge';
import { TimeEntryRow } from '../components/time-entry-row';

export function TimesheetPage() {
  const remainingHours = useRemainingHours(1);
  return (
    <section>
      <PageTitle text="工时填报" />
      <StatusBadge tone="pending">审批中</StatusBadge>
      <TimeEntryRow hours={remainingHours ?? 0} />
      <Button>提交</Button>
    </section>
  );
}
