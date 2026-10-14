import { describe, expect, it } from 'vitest';
import { publicPathOf } from './public-path.mjs';

describe('H5 的子路径', () => {
  it('INT-G3 固定为 /h5/：不设或设为 /h5/ 都得到 /h5/', () => {
    expect(publicPathOf({})).toBe('/h5/');
    expect(publicPathOf({ H5_PUBLIC_PATH: '/h5/' })).toBe('/h5/');
  });

  it('INT-G3 设成别的值让构建失败，而不是悄悄换成另一个子路径', () => {
    for (const other of ['/apps/one-h5/', '', '/', 'h5/', '/h5', '/H5/', '/h5/x/']) {
      expect(() => publicPathOf({ H5_PUBLIC_PATH: other }), other).toThrow(/H5_PUBLIC_PATH/);
    }
  });
});
