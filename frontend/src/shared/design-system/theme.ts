// Bridges FORCITIS one semantic tokens (tokens.css) to the Ant Design theme. Together with index.ts this
// directory is the ONLY place tokens.css is read. antd derives hover/active shades from seed colours and
// cannot parse var() or oklch(), so the semantic tokens are resolved to sRGB hex here, once, at load time.
import type { ThemeConfig } from 'antd';
import tokensCss from './tokens.css?raw';

const declarations = new Map<string, string>();
for (const [, name, value] of tokensCss.matchAll(/--([\w-]+)\s*:\s*([^;}]+)/g)) {
  if (name !== undefined && value !== undefined) declarations.set(name, value.trim());
}

function oklchToHex(lightness: number, chroma: number, hueDegrees: number): string {
  const hue = (hueDegrees * Math.PI) / 180;
  const a = chroma * Math.cos(hue);
  const b = chroma * Math.sin(hue);
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const linear = [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
  const channels = linear.map((c) => {
    const srgb = c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055;
    return Math.round(Math.min(1, Math.max(0, srgb)) * 255).toString(16).padStart(2, '0');
  });
  return `#${channels.join('')}`;
}

/** Resolves a semantic token (e.g. 'intent-primary-bg') through the palette layer to a hex colour antd can derive from. */
function semanticColour(token: string): string {
  let value = declarations.get(token);
  for (let hops = 0; value?.startsWith('var(') && hops < 4; hops++) {
    value = declarations.get(value.replace(/^var\(--([\w-]+)\)$/, '$1'));
  }
  if (value?.startsWith('#')) return value;
  const oklch = value?.match(/^oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)$/);
  if (oklch?.[1] === undefined || oklch[2] === undefined || oklch[3] === undefined) {
    throw new Error(`design-system: --${token} does not resolve to a hex or oklch() palette value (got ${String(value)}).`);
  }
  return oklchToHex(Number(oklch[1]), Number(oklch[2]), Number(oklch[3]));
}

export const antdTheme: ThemeConfig = {
  cssVar: true,
  token: {
    colorPrimary: semanticColour('intent-primary-bg'),
    colorError: semanticColour('status-rejected-solid'),
    colorSuccess: semanticColour('status-posted-solid'),
    colorWarning: semanticColour('status-pending-solid'),
    colorInfo: semanticColour('text-link'),
    colorLink: semanticColour('text-link'),
    colorText: semanticColour('text-primary'),
    colorTextSecondary: semanticColour('text-secondary'),
    colorBorder: semanticColour('border-default'),
    colorBgContainer: semanticColour('surface-raised'),
    colorBgLayout: semanticColour('surface-page'),
    borderRadius: 4,
    fontSize: 14,
  },
};
