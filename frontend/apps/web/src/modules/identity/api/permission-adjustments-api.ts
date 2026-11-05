import { DEFAULT_PAGE_LIMIT, checkedPage, operations } from '@forcitis/api';
import type { ReferenceKind } from '../model/permission-adjustment';

export interface AdjustmentFilter {
  status: string;
  kind: string;
}

type Query = NonNullable<NonNullable<Parameters<typeof operations.getPlatformPermissionAdjustments>[0]>['query']>;
type StatusParam = NonNullable<Query['status']>;
type KindParam = NonNullable<Query['kind']>;

export function fetchAdjustmentPage(filter: AdjustmentFilter, cursor: string | undefined) {
  return operations.getPlatformPermissionAdjustments({
    query: {
      limit: DEFAULT_PAGE_LIMIT,
      ...(filter.status === '' ? {} : { status: filter.status as StatusParam }),
      ...(filter.kind === '' ? {} : { kind: filter.kind as KindParam }),
      ...(cursor === undefined ? {} : { cursor }),
    },
  }).then((page) => checkedPage(page, cursor));
}

export function fetchAdjustment(permissionAdjustmentId: string) {
  return operations.getPlatformPermissionAdjustmentsByPermissionAdjustmentId({ path: { permissionAdjustmentId } });
}

/** `version` is versionOf(the item as read): a decision on another version answers 412. A sensitive-role review needs the business owner (typed employee id: choosing from the employee list needs employee:read, which the handling role does not hold); a scope review takes none. */
export async function upholdAdjustment(permissionAdjustmentId: string, version: string | undefined, reason: string, businessOwnerEmployeeId: string): Promise<void> {
  await operations.postPlatformPermissionAdjustmentsByPermissionAdjustmentIdUphold({
    path: { permissionAdjustmentId },
    body: { ...(reason === '' ? {} : { reason }), ...(businessOwnerEmployeeId === '' ? {} : { businessOwnerEmployeeId }) },
  }, { ifMatch: version, keepBusinessForbidden: true });
}

/** Exactly one reference: the authorization request or the audit entry that shows the permissions were adjusted. */
export async function markAdjusted(permissionAdjustmentId: string, version: string | undefined, reference: ReferenceKind, referenceId: string): Promise<void> {
  await operations.postPlatformPermissionAdjustmentsByPermissionAdjustmentIdMarkAdjusted({ path: { permissionAdjustmentId }, body: { [reference]: referenceId } }, { ifMatch: version, keepBusinessForbidden: true });
}
