import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const api = join(dirname(fileURLToPath(import.meta.url)), '..', 'packages', 'api');
const command = '请在仓库根运行 bash scripts/generate-api.sh（需 Docker 和已安装的前端依赖）；已有本次导出的 openapi.json 时在 frontend/ 运行 pnpm generate:api。';

export function requireOpenApi() {
  const path = join(api, 'openapi.json');
  if (!existsSync(path)) throw new Error(`缺少 ${path}；${command}`);
  return JSON.parse(readFileSync(path, 'utf8'));
}

export function requireGeneratedApi() {
  requireOpenApi();
  for (const file of ['schema.ts', 'validators.ts', 'operations.ts']) {
    const path = join(api, 'src', 'generated', file);
    if (!existsSync(path) || readFileSync(path, 'utf8').length === 0) throw new Error(`缺少生成产物 ${path}；${command}`);
  }
}

if (process.argv.includes('--check')) requireGeneratedApi();
