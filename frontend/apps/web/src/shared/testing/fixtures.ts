export function currentUserBody(permissionCodes: string[], navigation: unknown[] = []) {
  return {
    userId: '1001',
    displayName: '张三',
    orgUnitId: '10',
    orgUnitName: '交付一部',
    permissionCodes,
    navigation,
    identityConfirmed: true,
  };
}
