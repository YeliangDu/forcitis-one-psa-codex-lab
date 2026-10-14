// The ONLY place that talks HTTP. Every response is parsed by its Zod schema, so the UI never sees unchecked data.
import type { z } from 'zod';

export async function getJson<S extends z.ZodType>(path: string, schema: S): Promise<z.infer<S>> {
  const response = await fetch(path, { headers: { accept: 'application/json' } });
  if (!response.ok) throw new Error(`GET ${path} failed: ${response.status}`);
  return schema.parse(await response.json());
}
