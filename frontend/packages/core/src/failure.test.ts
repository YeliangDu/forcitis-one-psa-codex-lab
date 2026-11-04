import { ApiError, type ErrorCode, MissingVersionError, ResponseShapeError } from '@forcitis/api';
import { describe, expect, expectTypeOf, it } from 'vitest';
import { ERROR_MESSAGES } from './error-messages';
import { describeFailure, failureOf, isVersionConflict } from './failure';

function apiError(status: number, errorCode: string, extra: Record<string, unknown> = {}) {
  return new ApiError(status, { status, errorCode, ...extra } as ConstructorParameters<typeof ApiError>[1], undefined);
}

describe('describeFailure', () => {
  it('服务返回的数据与接口约定不符时，给出专门的提示，而不是说无法连接', () => {
    expect(describeFailure(new ResponseShapeError('GET /x', ['a: required']))).toBe('服务返回的数据与约定不符，请稍后重试');
  });

  it('按错误码给出业务文案，没有专用文案的错误码按状态码给通用文案', () => {
    expect(describeFailure(apiError(409, 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED'))).toBe('审批任务已被其他人认领');
    expect(describeFailure(apiError(409, 'PLATFORM_APPROVAL_NO_ROUTE'))).toBe('当前状态不允许这个操作，请刷新后重试');
    expect(describeFailure(apiError(503, 'PLATFORM_WEB_UNEXPECTED'))).toBe('服务暂时不可用，请稍后重试');
  });

  it('错误体不是 ProblemDetail 时，只按状态码说话', () => {
    expect(describeFailure(new ApiError(502, undefined, '<html>'))).toBe('服务暂时不可用，请稍后重试');
    expect(describeFailure(new ApiError(401, undefined, undefined))).toBe('登录已失效，请重新登录');
  });

  it('网络不通时提示检查网络', () => {
    expect(describeFailure(new TypeError('Failed to fetch'))).toBe('无法连接服务器，请检查网络后重试');
  });

  it('带版本的更新被拒绝（412）时提示刷新，没有读过资源就更新时也提示刷新', () => {
    expect(describeFailure(apiError(412, 'PLATFORM_WEB_VERSION_MISMATCH'))).toBe('内容刚刚被其他人修改，请刷新后重试');
    expect(describeFailure(new ApiError(412, undefined, undefined))).toBe('内容刚刚被其他人修改，请刷新后重试');
    expect(describeFailure(new MissingVersionError('PUT /x'))).toBe('页面内容已过期，请刷新后重试');
  });

  it('读不出状态时不抛错', () => {
    expect(failureOf('boom')).toEqual({ status: undefined, code: undefined, legacyLoginUrl: undefined, malformedAnswer: false });
  });

  it('服务端新加了客户端还不认识的错误码时，仍按状态码提示，并保留回到老系统登录的地址', () => {
    const unknown = new ApiError(503, { status: 503, errorCode: 'PLATFORM_FUTURE_CODE', legacyLoginUrl: 'https://old.example.com/login' }, undefined);
    expect(describeFailure(unknown)).toBe('服务暂时不可用，请稍后重试');
    expect(failureOf(unknown)).toMatchObject({ status: 503, code: undefined, legacyLoginUrl: 'https://old.example.com/login' });
  });

  it('登录失败时带出回到老系统登录的地址', () => {
    expect(failureOf(apiError(503, 'PLATFORM_IDENTITY_TICKET_INVALID', { legacyLoginUrl: 'https://old.example.com/login' })).legacyLoginUrl).toBe('https://old.example.com/login');
  });
});

describe('isVersionConflict', () => {
  it('只在版本不一致时为真', () => {
    expect(isVersionConflict(apiError(412, 'PLATFORM_WEB_VERSION_MISMATCH'))).toBe(true);
    expect(isVersionConflict(new ApiError(412, undefined, undefined))).toBe(true);
    expect(isVersionConflict(apiError(409, 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED'))).toBe(false);
  });
});

describe('ERROR_MESSAGES', () => {
  it('每个错误码都必须有一条记录：漏掉一个就无法通过类型检查', () => {
    expectTypeOf<keyof typeof ERROR_MESSAGES>().toEqualTypeOf<ErrorCode>();
    // @ts-expect-error a table that misses an error code does not satisfy Record<ErrorCode, ...>
    const incomplete: Readonly<Record<ErrorCode, string | null>> = { PLATFORM_WEB_UNEXPECTED: null };
    expect(Object.keys(incomplete)).toHaveLength(1);
  });

  it('文案不为空，没有专用文案的明确写为 null', () => {
    for (const message of Object.values(ERROR_MESSAGES)) {
      if (message !== null) expect(message.trim()).not.toBe('');
    }
  });
});
