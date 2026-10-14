// Chinese compact money: < 1万 as is, < 1亿 in 万, otherwise in 亿. Two decimals, trailing zeros trimmed.
const WAN = 10_000;
const YI = 100_000_000;

function trim(value: number): string {
  return value.toFixed(2).replace(/\.?0+$/, '');
}

export function formatMoneyCompact(amount: number): string {
  const sign = amount < 0 ? '-' : '';
  const abs = Math.abs(amount);
  if (abs >= YI) return `${sign}${trim(abs / YI)}亿`;
  if (abs >= WAN) return `${sign}${trim(abs / WAN)}万`;
  return `${sign}${trim(abs)}`;
}
