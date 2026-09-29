#!/usr/bin/env python3
"""
Draws the four blocks of his ground, the Bell Hollows (16x16 each), and a sheet of them in pictures/blocks/.

    python3 tools/block_textures.py

Everything comes from fixed numbers (a small hash, no randomness), so running it again gives the same files.
  bell_calcite   calcite with pale green veins, like the ribs of his bell
  tendril_glass  see-through green glass with glowing veins (it glows a little in game)
  bell_shard     cracked, cloudy pale glass from an old bell
  spore_moss     a thin layer of moss with pale glowing spores (drawn as a carpet in game)
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hollowbell', 'textures', 'block')
PICS = os.path.join(ROOT, 'pictures', 'blocks')


def h(x, y, salt):
    """0..1 from a cell and a salt, the same every time"""
    n = (x * 374761393 + y * 668265263 + salt * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def smooth(x, y, salt, cell):
    """soft value noise that wraps round the 16 pixels, so the texture tiles"""
    n = 16 // cell
    gx, gy = x / cell, y / cell
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    def v(i, j):
        return h(i % n, j % n, salt)
    a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * fx
    b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * fx
    return a + (b - a) * fy


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def vein(x, y, salt, width):
    """1 on a winding thin line that wraps round the tile, 0 away from it"""
    w = 3.2 * math.sin((y + 0.5) / 16 * 2 * math.pi + salt) + 2.0 * math.sin((y + 0.5) / 16 * 4 * math.pi + salt * 2.1)
    for off in (-16, 0, 16):
        d = abs(x + 0.5 - (8 + w + (salt * 5) % 16 - 8 + off))
        if d < width:
            return 1 - d / width
    return 0.0


def bell_calcite():
    img = Image.new('RGBA', (16, 16))
    base, lo, hi = (223, 225, 219), (196, 199, 193), (240, 242, 237)
    green, green_hi = (170, 214, 170), (206, 238, 196)
    for y in range(16):
        for x in range(16):
            n = smooth(x, y, 3, 4) * 0.6 + h(x, y, 4) * 0.4
            c = mix(lo, base, n * 1.6) if n < 0.62 else mix(base, hi, (n - 0.62) * 2.6)
            v = vein(x, y, 1.3, 1.1)
            if v > 0:
                c = mix(c, green_hi if v > 0.5 else green, 0.45 + 0.4 * v)
            if h(x, y, 9) > 0.992:
                c = (122, 176, 150)                     # a fleck of old copper
            img.putpixel((x, y), c + (255,))
    return img


def glass_frame(img, rim, rim_a):
    """the light edge every glass block has, broken here and there"""
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            if h(x, y, 21) > 0.18:
                img.putpixel((x, y), rim + (rim_a,))


def tendril_glass():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    tint, deep = (104, 190, 92), (70, 150, 74)
    glow, glow_hi = (178, 240, 120), (226, 255, 178)
    for y in range(16):
        for x in range(16):
            n = smooth(x, y, 7, 8)
            c = mix(deep, tint, n)
            a = 84 + int(36 * n)
            v = max(vein(x, y, 2.2, 1.2), 0.9 * vein(y, x, 5.3, 1.0))
            if v > 0.05:
                c = mix(glow, glow_hi, v)
                a = 190 + int(60 * v)
            img.putpixel((x, y), c + (a,))
    glass_frame(img, (190, 236, 160), 210)
    return img


def bell_shard():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cloud, clear = (214, 236, 222), (170, 212, 186)
    for y in range(16):
        for x in range(16):
            n = smooth(x, y, 11, 4) * 0.7 + smooth(x, y, 12, 8) * 0.3
            c = mix(clear, cloud, n)
            a = 70 + int(120 * n)
            img.putpixel((x, y), c + (a,))
    d = ImageDraw.Draw(img)
    # cracks: a few straight broken lines from a point, the way glass breaks
    ox, oy = 6, 9
    for k, ang in enumerate((0.3, 1.5, 2.6, 3.9, 5.1)):
        ln = 5 + int(h(k, 1, 13) * 7)
        x1, y1 = ox + math.cos(ang) * ln, oy + math.sin(ang) * ln
        d.line((ox, oy, x1, y1), fill=(246, 252, 248, 235), width=1)
        mx, my = (ox + x1) / 2, (oy + y1) / 2
        d.line((mx, my, mx + math.cos(ang + 0.9) * 3, my + math.sin(ang + 0.9) * 3), fill=(236, 246, 240, 200), width=1)
    glass_frame(img, (226, 242, 232), 200)
    return img


def spore_moss():
    img = Image.new('RGBA', (16, 16))
    lo, base, hi = (78, 104, 46), (104, 134, 60), (132, 164, 78)
    spore, spore_hi = (214, 236, 150), (246, 252, 204)
    for y in range(16):
        for x in range(16):
            n = smooth(x, y, 17, 4) * 0.55 + h(x, y, 18) * 0.45
            c = mix(lo, base, n * 1.7) if n < 0.6 else mix(base, hi, (n - 0.6) * 2.5)
            r = h(x, y, 19)
            if r > 0.93:
                c = spore_hi if r > 0.975 else spore
            img.putpixel((x, y), c + (255,))
    return img


def loot_cache_side():
    img = bell_calcite()
    d = ImageDraw.Draw(img)
    # a bone band round the middle with a glowing seam, and dark corners like a chest
    for x in range(16):
        for y in (6, 7, 8, 9):
            img.putpixel((x, y), (226, 220, 198, 255) if y in (6, 9) else (170, 236, 150, 255))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        img.putpixel((x, y), (120, 130, 124, 255))
    d.rectangle((6, 6, 9, 9), outline=(96, 150, 110, 255))
    return img


def loot_cache_top():
    img = bell_calcite()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 4.2:
                img.putpixel((x, y), mix((236, 255, 214), (150, 226, 150), r / 4.2) + (255,))
            elif r < 5.2:
                img.putpixel((x, y), (226, 220, 198, 255))
    return img


def cube(tex, size=96):
    """a little iso picture of the block, for the sheet"""
    t = tex.convert('RGBA')
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    s = size / 32.0
    def face(pix, k, fn):
        for v in range(16):
            for u in range(16):
                c = pix.getpixel((u, v))
                col = shade(c[:3], k) + (c[3],)
                pts = [fn(u + du, v + dv) for du, dv in ((0, 0), (1, 0), (1, 1), (0, 1))]
                ImageDraw.Draw(out).polygon([(px * s, py * s) for px, py in pts], fill=col)
    face(t, 1.0, lambda u, v: (16 + (u - v), 2 + (u + v) / 2))
    face(t, 0.78, lambda u, v: (0 + u, 10 + u / 2 + v))
    face(t, 0.6, lambda u, v: (16 + u, 18 - u / 2 + v))
    return out


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(PICS, exist_ok=True)
    blocks = [('bell_calcite', bell_calcite()), ('tendril_glass', tendril_glass()),
              ('bell_shard', bell_shard()), ('spore_moss', spore_moss()),
              ('loot_cache_side', loot_cache_side()), ('loot_cache_top', loot_cache_top())]
    for name, img in blocks:
        img.save(os.path.join(TEX, name + '.png'))
    # the sheet: each texture big, and a cube of it, on a pale backdrop with a darker strip so glass shows
    cell = 160
    sheet = Image.new('RGBA', (cell * len(blocks), 300), (236, 238, 232, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(blocks):
        x0 = i * cell
        for k in range(0, 128, 16):                  # a dark checker under each, so the glass shows through
            for j in range(0, 128, 16):
                if (k + j) // 16 % 2 == 0:
                    d.rectangle((x0 + 16 + k, 8 + j, x0 + 31 + k, 23 + j), fill=(64, 72, 68, 255))
        big = img.resize((128, 128), Image.NEAREST)
        sheet.alpha_composite(big, (x0 + 16, 8))
        sheet.alpha_composite(cube(img, 96), (x0 + 32, 160))
        d.text((x0 + 16, 272), name.replace('_', ' '), fill=(40, 44, 42, 255))
    sheet.save(os.path.join(PICS, 'blocks.png'))
    print('wrote', len(blocks), 'textures and pictures/blocks/blocks.png')


if __name__ == '__main__':
    main()
