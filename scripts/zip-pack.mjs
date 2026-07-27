/**
 * Zips a resource pack directory into a Minecraft-loadable .zip.
 *
 * Why not Compress-Archive / ZipFile.CreateFromDirectory: on Windows both write
 * entry names with backslashes ("assets\mcd\..."). Minecraft resolves pack
 * resources by forward-slash path, so such a zip loads as an empty pack. This
 * writer always emits forward slashes.
 *
 * Usage:
 *   node scripts/zip-pack.mjs [<pack-dir>] [-o <out.zip>]
 *
 * Defaults to resourcepack-mcd/ -> FunctionsFunnyWeapons.zip at the repo root.
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { deflateRawSync, crc32 } from 'node:zlib';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const args = process.argv.slice(2);
const outFlag = args.indexOf('-o');
const outPath = outFlag === -1
  ? join(ROOT, 'FunctionsFunnyWeapons.zip')
  : resolve(args[outFlag + 1]);
const srcDir = resolve(args.find((a, i) => !a.startsWith('-') && i !== outFlag + 1) ?? join(ROOT, 'resourcepack-mcd'));

/** Every file under dir, as forward-slash paths relative to dir. */
function walk(dir, prefix = '') {
  const out = [];
  for (const name of readdirSync(dir).sort()) {
    const full = join(dir, name);
    const rel = prefix ? `${prefix}/${name}` : name;
    if (statSync(full).isDirectory()) out.push(...walk(full, rel));
    else out.push({ name: rel, data: readFileSync(full) });
  }
  return out;
}

// DOS timestamp: fixed 1980-01-01 so identical input yields an identical zip.
const DOS_TIME = 0;
const DOS_DATE = (1 << 5) | 1; // month 1, day 1, year 1980

const entries = walk(srcDir);
if (!entries.some((e) => e.name === 'pack.mcmeta')) {
  throw new Error(`No pack.mcmeta at the root of ${srcDir} — Minecraft would reject this pack`);
}

const locals = [];
const centrals = [];
let offset = 0;

for (const { name, data } of entries) {
  const nameBuf = Buffer.from(name, 'utf8');
  const deflated = deflateRawSync(data, { level: 9 });
  // Only deflate when it actually helps; otherwise store.
  const useDeflate = deflated.length < data.length;
  const body = useDeflate ? deflated : data;
  const method = useDeflate ? 8 : 0;
  const sum = crc32(data);

  const local = Buffer.alloc(30 + nameBuf.length);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(20, 4); // version needed
  local.writeUInt16LE(0x0800, 6); // UTF-8 names
  local.writeUInt16LE(method, 8);
  local.writeUInt16LE(DOS_TIME, 10);
  local.writeUInt16LE(DOS_DATE, 12);
  local.writeUInt32LE(sum, 14);
  local.writeUInt32LE(body.length, 18);
  local.writeUInt32LE(data.length, 22);
  local.writeUInt16LE(nameBuf.length, 26);
  nameBuf.copy(local, 30);
  locals.push(local, body);

  const central = Buffer.alloc(46 + nameBuf.length);
  central.writeUInt32LE(0x02014b50, 0);
  central.writeUInt16LE(20, 4); // version made by
  central.writeUInt16LE(20, 6); // version needed
  central.writeUInt16LE(0x0800, 8);
  central.writeUInt16LE(method, 10);
  central.writeUInt16LE(DOS_TIME, 12);
  central.writeUInt16LE(DOS_DATE, 14);
  central.writeUInt32LE(sum, 16);
  central.writeUInt32LE(body.length, 20);
  central.writeUInt32LE(data.length, 24);
  central.writeUInt16LE(nameBuf.length, 28);
  central.writeUInt32LE(offset, 42);
  nameBuf.copy(central, 46);
  centrals.push(central);

  offset += local.length + body.length;
}

const centralBuf = Buffer.concat(centrals);
const end = Buffer.alloc(22);
end.writeUInt32LE(0x06054b50, 0);
end.writeUInt16LE(entries.length, 8);
end.writeUInt16LE(entries.length, 10);
end.writeUInt32LE(centralBuf.length, 12);
end.writeUInt32LE(offset, 16);

writeFileSync(outPath, Buffer.concat([...locals, centralBuf, end]));
console.log(`Wrote ${outPath} (${entries.length} files, ${statSync(outPath).size} bytes)`);
