import { spawn } from 'node:child_process';

const steps = [
  'check:generated',
  'typecheck',
  'check:lint-config',
  'lint',
  'check:structure',
  'check:copy',
  'test:scripts',
  'test',
  'build',
];

const run = (name) =>
  new Promise((resolve) => {
    const started = Date.now();
    const child = spawn('pnpm', ['run', name], { stdio: ['ignore', 'pipe', 'pipe'], shell: process.platform === 'win32' });
    const chunks = [];
    child.stdout.on('data', (data) => chunks.push(data));
    child.stderr.on('data', (data) => chunks.push(data));
    child.on('close', (code) => {
      const seconds = ((Date.now() - started) / 1000).toFixed(1);
      process.stdout.write(`\n::group::${code === 0 ? 'ok  ' : 'FAIL'} ${name} (${seconds}s)\n${Buffer.concat(chunks)}\n::endgroup::\n`);
      resolve({ name, code });
    });
  });

// Consumers run in parallel only after the current backend export has produced all three files.
const generated = await run('generate:api');
if (generated.code !== 0) process.exit(1);
const results = await Promise.all(steps.map(run));
const failed = results.filter((result) => result.code !== 0).map((result) => result.name);
if (failed.length > 0) {
  console.error(`verify failed: ${failed.join(', ')}`);
  process.exit(1);
}
console.log(`verify passed: ${steps.length} steps`);
