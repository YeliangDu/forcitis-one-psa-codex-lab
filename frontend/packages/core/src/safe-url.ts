/** A link target that came from the server is only followed when it is an absolute https address (no javascript:, data: or relative tricks). */
export function safeExternalUrl(value: string | null | undefined): string | undefined {
  if (value === null || value === undefined || value.trim() === '') return undefined;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}

export function safeInternalPath(value: string | null | undefined): string | undefined {
  if (value === null || value === undefined) return undefined;
  return value.startsWith('/') && !value.startsWith('//') && !value.includes('\\') ? value : undefined;
}
