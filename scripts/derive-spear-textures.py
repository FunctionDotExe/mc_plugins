"""
Derives the five Copper-Age spear sprites the plugin ships from the vendored MCD spear art.

The MCD pack has exactly five spear silhouettes and all five were already spoken for
(dreadlance, legionnaires_pike, maelstrom_trident, venomtip_javelin, wind_spear), so the
spear family added on top of it wears palette-shifted copies instead of new silhouettes:
same pixels, same Blockbench display transforms, a different metal. Each derived sprite
keeps its source's shading and alpha — only hue/saturation/value move.

Run after a fresh `build-mcd-pack.mjs --mcd <upstream>` import, which replaces the whole
vendored `mcd` texture tree and therefore drops these:

    python scripts/derive-spear-textures.py     # needs Pillow

Writes resourcepack-mcd/assets/mcd/textures/item/spears/<name>.png plus a model json per
sprite cloned from the source model with its layer0 repointed.
"""
import colorsys
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MCD = os.path.join(ROOT, 'resourcepack-mcd', 'assets', 'mcd')
TEXTURES = os.path.join(MCD, 'textures', 'item', 'spears')
MODELS = os.path.join(MCD, 'models', 'item', 'spears')

# weapon id -> (source sprite, hue, saturation scale, value scale).
# A bare hue number rotates the source's own hue; ('=', deg) forces an absolute hue, which is
# what a source whose colour is mostly in a few pixels needs to land on a specific metal.
JOBS = {
    # rust-free iron: the barbed pike, stripped of Fortune Spear's gold.
    'harrowpike': ('fortune_spear', 10, 0.16, 0.95),
    # hemp and brass — a pike you tie things to.
    'tetherpike': ('whispering_spear', -170, 0.95, 1.05),
    # copper, because the whole weapon is a lightning rod.
    'arcpike': ('glaive', ('=', 22), 1.25, 1.10),
    # cold steel over slate, for the one that drops the floor on you.
    'sunderpike': ('grave_bane', ('=', 210), 0.30, 0.95),
    # end-crystal violet.
    'crystalpike': ('venom_glaive', -145, 0.70, 1.05),
}


def shift(image, hue, sat, val):
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h = (hue[1] / 360.0) % 1.0 if isinstance(hue, tuple) else (h + hue / 360.0) % 1.0
            s = min(1.0, max(0.0, s * sat))
            v = min(1.0, max(0.0, v * val))
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            pixels[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)


def main():
    from PIL import Image

    for name, (source, hue, sat, val) in JOBS.items():
        image = Image.open(os.path.join(TEXTURES, source + '.png')).convert('RGBA')
        shift(image, hue, sat, val)
        image.save(os.path.join(TEXTURES, name + '.png'))

        with open(os.path.join(MODELS, source + '.json'), encoding='utf8') as handle:
            model = json.load(handle)
        model['textures'] = {'layer0': 'mcd:item/spears/' + name}
        with open(os.path.join(MODELS, name + '.json'), 'w', encoding='utf8') as handle:
            json.dump(model, handle, indent='\t')
            handle.write('\n')

        print('{} <- {} {}x{}'.format(name, source, image.width, image.height))


if __name__ == '__main__':
    main()
