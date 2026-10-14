// Generated from OpenAPI (openapi-typescript). Do not edit by hand.
export interface TimesheetResponse {
  id: number;
  workDate: string;
  status: 'DRAFT' | 'IN_APPROVAL' | 'REJECTED' | 'APPROVED' | 'CANCELLED';
}

export interface TaskPackageResponse {
  id: number;
  remainingHours: number;
}
