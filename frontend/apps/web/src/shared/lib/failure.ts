export interface Failure {
  status: number | undefined;
  code: string | undefined;
  body: unknown;
}

export function failureOf(error: unknown): Failure {
  if (typeof error !== 'object' || error === null) return { status: undefined, code: undefined, body: undefined };
  const record = error as Record<string, unknown>;
  return {
    status: typeof record['status'] === 'number' ? record['status'] : undefined,
    code: typeof record['code'] === 'string' ? record['code'] : undefined,
    body: record['body'],
  };
}

export function messageForStatus(status: number | undefined): string {
  if (status === undefined) return '无法连接服务器，请检查网络后重试';
  if (status === 400 || status === 422) return '提交的内容不正确';
  if (status === 404) return '找不到对应的数据，可能已被处理或删除';
  if (status === 409) return '当前状态不允许此操作，请刷新后重试';
  if (status >= 500) return '服务暂时不可用，请稍后重试';
  return '操作失败，请稍后重试';
}

export function describeFailure(error: unknown, byCode: Readonly<Record<string, string>> = {}): string {
  const { status, code } = failureOf(error);
  return (code === undefined ? undefined : byCode[code]) ?? messageForStatus(status);
}
