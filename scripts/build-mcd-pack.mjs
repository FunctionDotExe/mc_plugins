/**
 * Regenerates resourcepack-mcd/ from vendored 3D weapon model packs.
 *
 * Two upstream packs feed it:
 *   - "MC Dungeons Weapons" ships Blockbench models under the `mcd` namespace.
 *   - "Blades Of Majestica" ships Blockbench models under `minecraft`, selected
 *     by custom_name overrides. Those overrides are NOT vendored (they would
 *     repaint vanilla swords); only the models/textures they point at are, and
 *     they get rewritten into a private `bom` namespace.
 *
 * Both sets are vendored verbatim, and one `weaponsplugin:<weapon_id>` item
 * definition per weapon points at the model that weapon should wear. The plugin
 * only has to set the item_model component to `weaponsplugin:<id>` (see
 * Weapon.TEXTURED_IDS).
 *
 * Usage:
 *   node scripts/build-mcd-pack.mjs [--mcd <extracted-mcd-pack>] [--bom <extracted-bom-pack>]
 *
 * With no flags it rebuilds in place from the assets already vendored in
 * resourcepack-mcd/, so the mappings below stay the single source of truth.
 */
import { cpSync, mkdirSync, rmSync, writeFileSync, readFileSync, existsSync, renameSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const OUT = join(ROOT, 'resourcepack-mcd');
const TMP = join(ROOT, '.resourcepack-mcd-build');

const PACK_NAME = 'FunctionsFunnyWeapons';

/**
 * weapon id -> MCD model path (relative to `mcd:item/`).
 *
 * Matched on silhouette first, then theme/palette. Every entry here is unique:
 * weapons that used to double up on an MCD model were moved to BOM_MAPPING.
 */
const MCD_MAPPING = {
  anglers_hook: 'maces/flail',
  apotheosis: 'swords/heartstealer',
  arcane_staff: 'staffs/battlestaff',
  ballista_crossbow: 'crossbows/doom_crossbow',
  blastcaller: 'crossbows/firebolt_thrower',
  blood_reaper: 'others/jailors_scythe',
  celestial_bow: 'bows/sabrewing',
  chrono_blade: 'swords/rapier',
  cinder_cleaver: 'axes/firebrand',
  cryoclasm: 'swords/freezing_foil',
  dawnbreaker: 'swords/hawkbrand',
  dragon_fang: 'axes/cursed_axe',
  dreadlance: 'spears/grave_bane',
  duskfall_mace: 'maces/great_hammer',
  earthbreaker_axe: 'axes/highland_axe',
  excavators_pick: 'others/diamond_pickaxe',
  exsanguinator: 'knifes/dagger',
  flame_katana: 'swords/masters_katana',
  frost_scythe: 'others/frost_scythe',
  glacial_scepter: 'staffs/growing_staff',
  hive_breaker: 'swords/beestinger',
  hunters_crossbow: 'crossbows/slayer_crossbow',
  ironclaw_knuckles: 'knifes/nightmares_bite',
  kings_judgment: 'swords/claymore',
  legionnaires_pike: 'spears/fortune_spear',
  lunar_blade: 'swords/cutlass',
  maelstrom_trident: 'spears/glaive',
  meteor_maul: 'maces/suns_grace',
  mournsong: 'knifes/soul_knife',
  necromancer_staff: 'staffs/battlestaff_of_terror',
  nullblade: 'swords/dark_katana',
  sakura_blade: 'swords/dancers_sword',
  serpentfang_crossbow: 'crossbows/corrupted_crossbow',
  shadow_daggers: 'knifes/moon_daggers',
  solar_greatsword: 'swords/truthseeker',
  starbreaker: 'swords/starless_sword',
  starfang: 'knifes/eternal_knife',
  storm_chakrams: 'knifes/the_last_laugh_silver',
  stormbreaker: 'swords/broadsword',
  stormreach_halberd: 'axes/whirlwind',
  tearfall: 'knifes/fangs_of_frost',
  tempest_maul: 'maces/hammer_of_gravity',
  thunder_hammer: 'maces/stormlander',
  venomtip_javelin: 'spears/venom_glaive',
  vitriol: 'knifes/the_last_laugh_gold',
  void_blade: 'swords/nameless_blade',
  wind_spear: 'spears/whispering_spear',
  wyrmscale_bow: 'bows/red_snake',
};

/**
 * weapon id -> Blades Of Majestica model name (relative to `bom:item/`).
 *
 * These are the weapons MCD had no distinct silhouette for — they used to share
 * a model with a thematic sibling (two tridents on one glaive, three poison
 * polearms on one venom glaive, and so on). BOM has the missing shapes.
 */
const BOM_MAPPING = {
  anvilfall: 'powerfusehammer',
  chainwhip: 'ribboncleaver',
  plague_scythe: 'greenscythe',
  rotscourge: 'sculkscythe',
  soulcrown: 'soul_collector',
  soulharvester: 'soulharvester',
  spikequake_warpick: 'mjolnir',
  spinelash: 'scissorblade',
  tidal_trident: 'aquantictrident',
};

const args = process.argv.slice(2);
const mcdSrc = flag('--mcd');
const bomSrc = flag('--bom');

const mcdAssets = mcdSrc ? join(mcdSrc, 'assets', 'mcd') : join(OUT, 'assets', 'mcd');
if (!existsSync(mcdAssets)) throw new Error(`No MCD assets at ${mcdAssets}`);

// A fresh BOM pack keeps its models under `minecraft`; a rebuild reads back the
// already-namespaced copy this script wrote last time.
const bomAssets = bomSrc ? join(bomSrc, 'assets', 'minecraft') : join(OUT, 'assets', 'bom');
if (!existsSync(bomAssets)) throw new Error(`No BOM assets at ${bomAssets}`);
const bomNeedsRewrite = Boolean(bomSrc);

// Every mapped model must exist, or the item renders as a purple/black cube.
const missing = [
  ...Object.entries(MCD_MAPPING)
    .filter(([, m]) => !existsSync(join(mcdAssets, 'models', 'item', `${m}.json`)))
    .map(([id, m]) => `${id} -> mcd:item/${m}`),
  ...Object.entries(BOM_MAPPING)
    .filter(([, m]) => !existsSync(join(bomAssets, 'models', 'item', `${m}.json`)))
    .map(([id, m]) => `${id} -> bom:item/${m}`),
];
if (missing.length) throw new Error(`Missing models:\n${missing.join('\n')}`);

const clash = Object.keys(BOM_MAPPING).filter((id) => id in MCD_MAPPING);
if (clash.length) throw new Error(`Weapon mapped twice: ${clash.join(', ')}`);

rmSync(TMP, { recursive: true, force: true });
mkdirSync(TMP, { recursive: true });

cpSync(join(mcdAssets, 'models'), join(TMP, 'assets', 'mcd', 'models'), { recursive: true });
cpSync(join(mcdAssets, 'textures'), join(TMP, 'assets', 'mcd', 'textures'), { recursive: true });

vendorBom();

const itemsDir = join(TMP, 'assets', 'weaponsplugin', 'items');
mkdirSync(itemsDir, { recursive: true });
for (const [id, model] of Object.entries(MCD_MAPPING)) writeItem(id, `mcd:item/${model}`);
for (const [id, model] of Object.entries(BOM_MAPPING)) writeItem(id, `bom:item/${model}`);

// Track the source packs' format window so the pack never loads as
// "incompatible" against the assets it vendors. MCD is the narrower of the two.
const srcMeta = mcdSrc
  ? JSON.parse(readFileSync(join(mcdSrc, 'pack.mcmeta'), 'utf8'))
  : JSON.parse(readFileSync(join(OUT, 'pack.mcmeta'), 'utf8'));
writeFileSync(join(TMP, 'pack.mcmeta'), JSON.stringify({
  pack: {
    min_format: srcMeta.pack.min_format ?? srcMeta.pack.pack_format,
    max_format: srcMeta.pack.max_format ?? srcMeta.pack.pack_format,
    description: `${PACK_NAME} - 3D weapon models`,
  },
}, null, 2) + '\n');

// Credit the original pack authors alongside the vendored assets.
for (const [name, from] of [['pack.png', mcdSrc ?? OUT], ['Credits.txt', mcdSrc ?? OUT]]) {
  if (existsSync(join(from, name))) cpSync(join(from, name), join(TMP, name));
}
writeFileSync(join(TMP, 'Credits-BladesOfMajestica.txt'),
  'Weapon models under assets/bom/ are from the "Blades Of Majestica" resource pack,\n'
  + 'renamespaced from minecraft: to bom: so they no longer override vanilla items.\n'
  + 'All model and texture credit belongs to that pack\'s authors.\n');

rmSync(OUT, { recursive: true, force: true });
renameSync(TMP, OUT);

const total = Object.keys(MCD_MAPPING).length + Object.keys(BOM_MAPPING).length;
console.log(`Wrote ${total} item definitions (${Object.keys(MCD_MAPPING).length} MCD, ${Object.keys(BOM_MAPPING).length} BOM) to ${OUT}`);

function writeItem(id, model) {
  writeFileSync(join(itemsDir, `${id}.json`), JSON.stringify({
    model: { type: 'minecraft:model', model },
  }, null, 2) + '\n');
}

/**
 * Copies just the BOM models this pack maps, plus the textures they reference,
 * into assets/bom/. Texture references are relative (`item/foo`), so on import
 * they get an explicit `bom:` prefix; otherwise the game resolves them against
 * minecraft: and renders nothing.
 */
function vendorBom() {
  const modelsOut = join(TMP, 'assets', 'bom', 'models', 'item');
  const texOut = join(TMP, 'assets', 'bom', 'textures', 'item');
  mkdirSync(modelsOut, { recursive: true });
  mkdirSync(texOut, { recursive: true });

  const textures = new Set();
  for (const model of new Set(Object.values(BOM_MAPPING))) {
    const raw = readFileSync(join(bomAssets, 'models', 'item', `${model}.json`), 'utf8');
    const json = JSON.parse(raw);
    for (const [slot, value] of Object.entries(json.textures ?? {})) {
      if (typeof value !== 'string' || value.startsWith('#')) continue;
      const bare = value.replace(/^(minecraft:|bom:)/, '');
      textures.add(bare);
      json.textures[slot] = bomNeedsRewrite ? `bom:${bare}` : value;
    }
    writeFileSync(join(modelsOut, `${model}.json`), JSON.stringify(json, null, 2) + '\n');
  }

  for (const tex of textures) {
    if (!tex.startsWith('item/')) throw new Error(`Unexpected BOM texture path: ${tex}`);
    const name = tex.slice('item/'.length);
    const dir = dirname(name);
    if (dir !== '.') mkdirSync(join(texOut, dir), { recursive: true });
    for (const suffix of ['.png', '.png.mcmeta']) {
      const from = join(bomAssets, 'textures', `${tex}${suffix}`);
      if (existsSync(from)) cpSync(from, join(texOut, `${name}${suffix}`));
      else if (suffix === '.png') throw new Error(`Missing BOM texture: ${tex}`);
    }
  }

  console.log(`Vendored ${new Set(Object.values(BOM_MAPPING)).size} BOM models / ${textures.size} textures`);
}

function flag(name) {
  const i = args.indexOf(name);
  if (i === -1) return null;
  const value = args[i + 1];
  if (!value) throw new Error(`${name} needs a path`);
  return value;
}
