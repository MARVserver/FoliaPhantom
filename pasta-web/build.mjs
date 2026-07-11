import { gunzipSync } from 'node:zlib';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const root = resolve(import.meta.dirname);
const output = resolve(root, 'dist');
const partNames = ['00', '01', '02', '03'].map((suffix) => `deploy-bundle.part${suffix}`);
const encoded = (await Promise.all(
  partNames.map((name) => readFile(resolve(root, name), 'utf8')),
)).join('').trim();
const manifest = JSON.parse(gunzipSync(Buffer.from(encoded, 'base64')).toString('utf8'));

await rm(output, { recursive: true, force: true });
for (const [relativePath, base64] of Object.entries(manifest)) {
  const target = resolve(output, relativePath);
  if (!target.startsWith(`${output}/`) && target !== output) {
    throw new Error(`Unsafe artifact path: ${relativePath}`);
  }
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, Buffer.from(base64, 'base64'));
}
console.log(`Built Pasta Web with ${Object.keys(manifest).length} static files.`);
