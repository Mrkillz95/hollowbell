#!/usr/bin/env python3
"""
Draws the bell glass armor and the finder: item icons (16x16) and the two worn layers (64x32), plus preview
pictures in pictures/armor/ so they can be looked at without the game.

    python3 tools/armor_textures.py

Everything is worked out from fixed numbers (no randomness), so running it again gives the same files.
The colours are taken from his own blocks: calcite and verdant froglight for the pale glass, lime stained glass
for its green, oxidized and weathered copper for the seams, bone block for the ribs, raw copper for the rivets.
The glass keeps its alpha: he is drawn see-through in game, and so is his armor.
"""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hollowbell', 'textures')
PICS = os.path.join(ROOT, 'pictures', 'armor')

# ---------------------------------------------------------------- colours, from his blocks
GLASS_HI = (238, 252, 240)      # froglight's light side
GLASS = (206, 234, 208)         # froglight / calcite, a little lime
GLASS_MID = (178, 219, 186)
GLASS_LO = (146, 199, 160)      # lime glass seen through calcite
GLASS_DEEP = (118, 176, 140)
A_HI, A_GLASS, A_LO = 205, 125, 160          # how much of the glass you see (0 clear .. 255 solid)

CU_HI = (110, 197, 159)         # oxidized copper, light
CU = (82, 162, 132)             # oxidized copper
CU_LO = (57, 110, 89)           # oxidized copper, dark
CU_OLD = (108, 153, 110)        # weathered copper
RIVET = (192, 107, 79)          # raw copper, a few warm dots
RIVET_HI = (227, 130, 108)
BONE = (229, 225, 207)          # the ribs
BONE_LO = (200, 196, 176)

AMETHYST = (154, 92, 198)
AMETHYST_HI = (207, 160, 243)
NEEDLE_RED = (170, 60, 50)


def h(x, y, k=0):
    """a fixed little hash, 0..1"""
    n = (x * 374761393 + y * 668265263 + k * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3))


# ---------------------------------------------------------------- the worn layers

def glass_px(fx, fy, w, hgt, seed):
    """one pixel of a glass pane inside a face: lighter at the top, a clean streak of light, darker low down"""
    t = fy / max(1, hgt - 1)
    base = mix(GLASS, GLASS_LO, t * 0.85)
    a = int(A_GLASS + (A_LO - A_GLASS) * t)
    # the streak of light across the pane, top left to bottom right
    d = (fx - fy * 0.8) - w * 0.15
    if -0.6 <= d <= 0.6 and fy < hgt * 0.75:
        return rgba(GLASS_HI, A_HI)
    if 0.6 < d <= 1.6 and fy < hgt * 0.75:
        return rgba(mix(GLASS_HI, GLASS, 0.5), (A_HI + A_GLASS) // 2)
    # the odd bubble in the glass
    if h(fx, fy, seed) > 0.93:
        return rgba(mix(base, GLASS_HI, 0.55), a + 15)
    if h(fx, fy, seed + 7) > 0.9:
        return rgba(mix(base, GLASS_DEEP, 0.35), a + 10)
    return rgba(base, a)


def seam_px(fx, fy, seed, dark=False):
    """copper band: weathered in places, a warm rivet here and there"""
    r = h(fx, fy, seed + 3)
    if r > 0.96:
        return rgba(RIVET_HI if h(fx, fy, seed + 5) > 0.5 else RIVET)
    if r > 0.88:
        return rgba(CU_OLD)
    return rgba(CU_LO if dark else CU)


def face(img, x0, y0, w, hgt, seed, bands=(), ribs=(), frame=True, rows=None, top_rim=True):
    """
    Paints one face of a box on the layer: glass panes framed in copper, with copper bands across (rows)
    and bone ribs down (columns). rows=(a, b) paints only those rows of the face (for the boots and the belt).
    """
    r0, r1 = rows if rows else (0, hgt)
    for fy in range(r0, r1):
        for fx in range(w):
            edge_l, edge_r = fx == 0, fx == w - 1
            edge_t, edge_b = fy == r0, fy == r1 - 1
            if frame and (edge_l or edge_r or edge_b or (edge_t and top_rim)):
                c = seam_px(fx, fy, seed, dark=edge_b or edge_r)
                if edge_t and not (edge_l or edge_r):
                    c = rgba(CU_HI)                       # light catches the top rim
            elif fy in bands:
                c = seam_px(fx, fy, seed + 11)
            elif fx in ribs:
                c = rgba(BONE if fy % 3 else BONE_LO)
            else:
                c = glass_px(fx - 1, fy - r0 - 1, w - 2, r1 - r0 - 2, seed)
                # a shade just inside the frame at the bottom and the right, so it reads as a pane set in metal
                if frame and (fy == r1 - 2 or fx == w - 2):
                    c = rgba(mix(c[:3], GLASS_DEEP, 0.45), min(255, c[3] + 25))
            img.putpixel((x0 + fx, y0 + fy), c)


def box(img, u, v, w, hgt, d, seed, **kw):
    """the six faces of a box at texture spot (u, v): width w, height hgt, depth d (the usual unwrap)"""
    top_kw = dict(kw); top_kw.pop('rows', None); top_kw.pop('bands', None); top_kw.pop('ribs', None)
    sides = [(u, v + d, d, hgt), (u + d, v + d, w, hgt), (u + d + w, v + d, d, hgt), (u + d + w + d, v + d, w, hgt)]
    for i, (x, y, fw, fh) in enumerate(sides):
        face(img, x, y, fw, fh, seed + i * 17, **kw)
    if not kw.get('rows'):
        face(img, u + d, v, w, d, seed + 101, **top_kw)          # top
        face(img, u + d + w, v, w, d, seed + 103, **top_kw)      # bottom


def layer_1():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    # helmet: a small bell of glass, a copper band round the brow, one bone rib down the middle of the face
    box(img, 0, 0, 8, 8, 8, 1, bands=(5,), ribs=())
    for y in range(9, 13):                                   # a thin rib over the brow, down to the band
        img.putpixel((8 + 3, y), rgba(BONE_LO)); img.putpixel((8 + 4, y), rgba(BONE))
    # chestplate: the body, bone ribs down from the collar like his, two copper bands
    box(img, 16, 16, 8, 12, 4, 21, bands=(6,), ribs=())
    for y in range(21, 26):
        for x in (20 + 2, 20 + 5, 32 + 2, 32 + 5):           # ribs down from the collar, front and back, like his
            img.putpixel((x, y), rgba(BONE if y % 3 else BONE_LO))
    # arms: glass sleeves with a copper cuff and a shoulder band
    box(img, 40, 16, 4, 12, 4, 41, bands=(9,), ribs=())
    # boots: only the bottom of the legs, a copper toe band
    box(img, 0, 16, 4, 12, 4, 61, bands=(9,), rows=(6, 12))
    face(img, 8, 16, 4, 4, 67)                               # the soles
    return img


def layer_2():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    # leggings: the belt (lower part of the body) and both legs, a copper knee band
    box(img, 16, 16, 8, 12, 4, 81, bands=(), rows=(7, 12))
    box(img, 0, 16, 4, 12, 4, 91, bands=(6,), ribs=())
    return img


# ---------------------------------------------------------------- item icons

ICONS = {
    'bell_glass_helmet': [
        '................',
        '................',
        '......####......',
        '....########....',
        '...##########...',
        '...##########...',
        '..############..',
        '..############..',
        '..####....####..',
        '..###......###..',
        '..##........##..',
        '................',
        '................',
        '................',
        '................',
        '................'],
    'bell_glass_chestplate': [
        '................',
        '.#####....#####.',
        '.######..######.',
        '.##############.',
        '.##############.',
        '.##############.',
        '..############..',
        '...##########...',
        '...##########...',
        '...##########...',
        '...##########...',
        '...##########...',
        '...##########...',
        '...##########...',
        '................',
        '................'],
    'bell_glass_leggings': [
        '................',
        '................',
        '...##########...',
        '...##########...',
        '...##########...',
        '...####..####...',
        '...####..####...',
        '...###....###...',
        '...###....###...',
        '...###....###...',
        '...###....###...',
        '...###....###...',
        '...###....###...',
        '...###....###...',
        '................',
        '................'],
    'bell_glass_boots': [
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '...####..####...',
        '...####..####...',
        '...####..####...',
        '...####..####...',
        '..#####..#####..',
        '.######..######.',
        '.######..######.',
        '................',
        '................',
        '................'],
}
# rows that get a copper band, and columns that get a bone rib, per piece
BANDS = {'bell_glass_helmet': (7,), 'bell_glass_chestplate': (3, 7), 'bell_glass_leggings': (2, 9), 'bell_glass_boots': (10,)}
RIBS = {'bell_glass_helmet': (), 'bell_glass_chestplate': (6, 9), 'bell_glass_leggings': (), 'bell_glass_boots': ()}


def icon(name):
    rows = ICONS[name]
    m = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and rows[y][x] == '#'
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ys = [y for y in range(16) if '#' in rows[y]]
    top, bot = min(ys), max(ys)
    for y in range(16):
        for x in range(16):
            if not m(x, y):
                continue
            outline = not (m(x - 1, y) and m(x + 1, y) and m(x, y - 1) and m(x, y + 1))
            lit = not m(x, y - 1) or not m(x - 1, y)                 # facing up-left, towards the light
            t = (y - top) / max(1, bot - top)
            if outline:
                c = rgba(CU_HI if lit and y < top + 3 else (CU if lit else CU_LO))
            elif y in BANDS[name]:
                c = rgba(RIVET if (x * 5 + y) % 7 == 0 else (CU if (x + y) % 3 else CU_OLD))
            elif x in RIBS[name]:
                c = rgba(BONE if y % 3 else BONE_LO)
            else:
                base = mix(GLASS, GLASS_LO, t)
                a = int(A_GLASS + 30 + (A_LO - A_GLASS) * t)
                # shade next to the outline at the bottom and right; the light streak up and to the left
                if not m(x + 1, y + 1) or not m(x, y + 2):
                    c = rgba(mix(base, GLASS_DEEP, 0.6), min(255, a + 30))
                elif (x - top) - (y - top) in (-1, 0) and y < top + (bot - top) * 0.6:
                    c = rgba(GLASS_HI, A_HI + 20)
                elif lit or not m(x - 1, y - 1):
                    c = rgba(mix(base, GLASS_HI, 0.5), a + 15)
                else:
                    c = rgba(base, a)
            img.putpixel((x, y), c)
    return img


def finder():
    """a compass under a glass dome in a copper ring, an amethyst needle"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d > 7.3:
                continue
            if d > 6.2:                                            # the copper ring
                lit = (x - cx) + (y - cy) < 0
                c = rgba(CU_HI if lit and d < 6.9 else (CU if lit else CU_LO))
                if (x * 3 + y * 5) % 11 == 0:
                    c = rgba(RIVET)
            elif d > 5.4:                                          # the bone rim inside it
                c = rgba(BONE if (x - cx) + (y - cy) < 2 else BONE_LO)
            else:                                                  # the face, seen through glass
                t = (y - cy + 5) / 10
                c = rgba(mix(GLASS, GLASS_LO, t), 235)
                if x + y in (8, 9) and y < 7:
                    c = rgba(GLASS_HI, 250)
            img.putpixel((x, y), c)
    # the needle: amethyst end points north-east, a red-copper tail
    for i, (x, y) in enumerate([(11, 4), (10, 5), (9, 6), (8, 7)]):
        img.putpixel((x, y), rgba(AMETHYST_HI if i == 0 else AMETHYST))
    img.putpixel((10, 4), rgba(AMETHYST))
    img.putpixel((11, 5), rgba(AMETHYST))
    for (x, y) in [(7, 8), (6, 9), (5, 10)]:
        img.putpixel((x, y), rgba(NEEDLE_RED))
    img.putpixel((7, 7), rgba(CU_LO))                              # the pin
    img.putpixel((8, 8), rgba(CU_LO))
    return img


# ---------------------------------------------------------------- pictures to look at

SKIN = (196, 150, 118)
SHIRT = (70, 110, 170)
PANTS = (60, 60, 110)
EYE = (40, 40, 60)
BG = (46, 52, 60)


def mannequin():
    """a plain grey stand-in for a player skin, 64x32"""
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(64):
            if y < 16 and x >= 32:
                continue
            c = SKIN
            if y >= 16 and 16 <= x < 56:
                c = SHIRT if not (x >= 40 and y >= 28) else SKIN      # shirt, bare hands
            if y >= 16 and x < 16:
                c = PANTS
            img.putpixel((x, y), rgba(c))
    for x in (10, 13):                                            # two eyes, so the face shows through
        img.putpixel((x, 12), rgba(EYE))
    return img


def crop(img, x, y, w, hgt, flip=False):
    c = img.crop((x, y, x + w, y + hgt))
    return c.transpose(Image.FLIP_LEFT_RIGHT) if flip else c


def figure(tex, back=False):
    """a player seen straight on (or from behind), 16x32, cut from a 64x32 texture"""
    fig = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    if not back:
        parts = [((8, 8, 8, 8), (4, 0), False),          # head front
                 ((20, 20, 8, 12), (4, 8), False),       # body front
                 ((44, 20, 4, 12), (0, 8), False),       # right arm front
                 ((44, 20, 4, 12), (12, 8), True),       # left arm (mirrored)
                 ((4, 20, 4, 12), (4, 20), False),       # right leg front
                 ((4, 20, 4, 12), (8, 20), True)]        # left leg
    else:
        parts = [((24, 8, 8, 8), (4, 0), False),
                 ((32, 20, 8, 12), (4, 8), False),
                 ((52, 20, 4, 12), (0, 8), True),
                 ((52, 20, 4, 12), (12, 8), False),
                 ((12, 20, 4, 12), (4, 20), True),
                 ((12, 20, 4, 12), (8, 20), False)]
    for (x, y, w, hgt), at, flip in parts:
        fig.alpha_composite(crop(tex, x, y, w, hgt, flip), at)
    return fig


def big(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def on_bg(img, pad=0, bg=BG):
    out = Image.new('RGBA', (img.width + pad * 2, img.height + pad * 2), rgba(bg))
    out.alpha_composite(img, (pad, pad))
    return out


def main():
    os.makedirs(os.path.join(TEX, 'item'), exist_ok=True)
    os.makedirs(os.path.join(TEX, 'models', 'armor'), exist_ok=True)
    os.makedirs(PICS, exist_ok=True)
    l1, l2 = layer_1(), layer_2()
    l1.save(os.path.join(TEX, 'models', 'armor', 'bell_glass_layer_1.png'))
    l2.save(os.path.join(TEX, 'models', 'armor', 'bell_glass_layer_2.png'))
    icons = {n: icon(n) for n in ICONS}
    icons['hollowbell_finder'] = finder()
    for n, im in icons.items():
        im.save(os.path.join(TEX, 'item', n + '.png'))

    # the icons, 8 times the size, on a dark and a light ground (the glass shows the ground through it)
    row = Image.new('RGBA', (len(icons) * 144 + 16, 2 * 144 + 16), rgba(BG))
    for i, im in enumerate(icons.values()):
        row.alpha_composite(on_bg(big(im, 8), 4), (16 + i * 144, 8))
        row.alpha_composite(on_bg(big(im, 8), 4, (198, 198, 198)), (16 + i * 144, 152))
    row.save(os.path.join(PICS, 'icons.png'))

    # the layers themselves, 8 times the size
    for n, l in (('layer_1', l1), ('layer_2', l2)):
        on_bg(big(l, 8), 8).save(os.path.join(PICS, n + '.png'))

    # the full set on a stand-in player, front and back: the leggings under, the rest over
    skin = mannequin()
    worn = skin.copy()
    worn.alpha_composite(l2)
    worn.alpha_composite(l1)
    sheet = Image.new('RGBA', (4 * 16 * 10 + 5 * 24, 32 * 10 + 48), rgba(BG))
    for i, (tex, back) in enumerate([(skin, False), (worn, False), (worn, True), (skin, True)]):
        sheet.alpha_composite(big(figure(tex, back), 10), (24 + i * (160 + 24), 24))
    sheet.save(os.path.join(PICS, 'worn_front_back.png'))
    print('bell glass armor and finder textures written; previews in pictures/armor/')


if __name__ == '__main__':
    main()
