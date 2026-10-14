import { ApiError, type ErrorCode, MissingVersionError, ResponseShapeError } from '@forcitis/api';
import { ERROR_MESSAGES } from './error-messages';

export interface Failure {
  status: number | undefined;
  code: ErrorCode | undefined;
  legacyLoginUrl: string | undefined;
  malformedAnswer: boolean;
}

export function failureOf(error: unknown): Failure {
  if (error instanceof ApiError) return { status: error.status, code: error.code, legacyLoginUrl: error.problem?.legacyLoginUrl, malformedAnswer: false };
  return { status: undefined, code: undefined, legacyLoginUrl: undefined, malformedAnswer: error instanceof ResponseShapeError };
}

export function messageForStatus(status: number | undefined): string {
  if (status === undefined) return '无法连接服务器，请检查网络后重试';
  if (status === 401) return '登录已失效，请重新登录';
  if (status === 403) return '没有权限执行这个操作';
  if (status === 400 || status === 422) return '填写的内容不正确';
  if (status === 404) return '找不到对应的数据，可能已被处理或删除';
  if (status === 409) return '当前状态不允许这个操作，请刷新后重试';
  if (status === 412 || status === 428) return '内容刚刚被其他人修改，请刷新后重试';
  if (status >= 500) return '服务暂时不可用，请稍后重试';
  return '操作失败，请稍后重试';
}

export function describeFailure(error: unknown): string {
  if (error instanceof MissingVersionError) return ERROR_MESSAGES.PLATFORM_WEB_VERSION_REQUIRED ?? messageForStatus(428);
  const { status, code, malformedAnswer } = failureOf(error);
  if (malformedAnswer) return '服务返回的数据与约定不符，请稍后重试';
  return (code === undefined ? null : ERROR_MESSAGES[code]) ?? messageForStatus(status);
}

export function isVersionConflict(error: unknown): boolean {
  const { status, code } = failureOf(error);
  return status === 412 || code === 'PLATFORM_WEB_VERSION_MISMATCH';
}
