import { describe, expect, it } from 'vitest';
import { publicPathOf } from './public-path.mjs';

describe('H5 的子路径', () => {
  it('默认是 /h5/，可以在构建时指定另一个子路径', () => {
    expect(publicPathOf({})).toBe('/h5/');
    expect(publicPathOf({ H5_PUBLIC_PATH: '/apps/one-h5/' })).toBe('/apps/one-h5/');
  });

  it('不合法的值让构建失败，而不是生成指向错误位置的资源地址', () => {
    for (const bad of ['', '/', 'h5/', '/h5', '//h5/', '/h5//x/', '/../', '/./', '/h5/../x/', 'https://x.example/h5/', '/h 5/', '/h5/?x=1']) {
      expect(() => publicPathOf({ H5_PUBLIC_PATH: bad }), bad).toThrow(/H5_PUBLIC_PATH/);
    }
  });
});
