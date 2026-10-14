import { describe, expect, it } from 'vitest';
import { antdTheme } from './theme';

describe('antd theme from the semantic tokens', () => {
  it('maps every seed colour to a resolved sRGB hex value', () => {
    const colours = Object.entries(antdTheme.token ?? {}).filter(([key]) => key.startsWith('color'));
    expect(colours.length).toBeGreaterThan(5);
    for (const [, value] of colours) {
      expect(value).toMatch(/^#[0-9a-f]{6}$/);
    }
  });
});
