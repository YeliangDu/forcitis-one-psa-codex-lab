import { describe, expect, it } from 'vitest';
import { optionsOf } from './options';

const units = [{ id: 'u1', name: '研发部' }, { id: 'u2', name: '财务部' }];

describe('optionsOf', () => {
  it('maps each item to a value and a label in order', () => {
    expect(optionsOf(units, (unit) => unit.id, (unit) => unit.name)).toEqual([
      { value: 'u1', label: '研发部' },
      { value: 'u2', label: '财务部' },
    ]);
  });

  it('puts one option of the empty value in front when an empty label is given', () => {
    expect(optionsOf(units, (unit) => unit.id, (unit) => unit.name, '全部')).toEqual([
      { value: '', label: '全部' },
      { value: 'u1', label: '研发部' },
      { value: 'u2', label: '财务部' },
    ]);
  });

  it('still offers the empty option when there are no items', () => {
    expect(optionsOf([], () => '', () => '', '请选择')).toEqual([{ value: '', label: '请选择' }]);
  });

  it('reads a label table entry by entry', () => {
    expect(optionsOf(Object.entries({ OPEN: '开放', CLOSED: '关闭' }), ([value]) => value, ([, label]) => label, '全部').map((option) => option.value)).toEqual(['', 'OPEN', 'CLOSED']);
  });
});
