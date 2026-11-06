export interface SelectOption {
  value: string;
  label: string;
}

/** The options of a select from a list of items, with one leading option of value '' (for example "全部" or "请选择") when `empty` is given. */
export function optionsOf<T>(items: readonly T[], valueOf: (item: T) => string, labelOf: (item: T) => string, empty?: string): SelectOption[] {
  const options = items.map((item) => ({ value: valueOf(item), label: labelOf(item) }));
  return empty === undefined ? options : [{ value: '', label: empty }, ...options];
}
