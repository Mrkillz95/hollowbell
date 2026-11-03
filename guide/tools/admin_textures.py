# The admin book's own pictures (ADMIN_BOOK.md, JJ Nov 2, Part 2C item 7): its item (16x16, in the giants' book-cover
# style: leather, gold trim, a crown and key with the eight giants' marks round it) and the screen's parchment, leather
# frame, tabs and section icons. Writes into src/main/resources/assets/jj_giants/textures/ and, with --preview DIR,
# enlarged copies to look at.
#   python tools/admin_textures.py [--preview DIR]
import os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(HERE, 'src', 'main', 'resources', 'assets', 'jj_giants', 'textures')


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c, d, t):
    return tuple(int(round(c[i] + (d[i] - c[i]) * t)) for i in range(3)) + (c[3],)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


# the eight giants' marks: each one's own colour (from his book cover)
MARKS = {
    'pitchgut': '#e8a0a8', 'furrowmaw': '#e04a1c', 'cerberus': '#58b4f0', 'hollowbell': '#40d0c0',
    'lanternwillow': '#7ccc3c', 'wreckback': '#f0ece0', 'swarmforge': '#f0961e', 'gloamwick': '#b896ff',
}

# ---------------------------------------------------------------- the item
OUT = hexc('#140a0c')
LEATHER = hexc('#4a1426')
LEATHER_D = hexc('#34101c')
LEATHER_L = hexc('#62203a')
SPINE = hexc('#2a0a14')
GOLD = hexc('#e8b840')
GOLD_D = hexc('#a87818')
GOLD_L = hexc('#fff0a0')
PAGE = hexc('#e4d8b8')
PAGE_D = hexc('#c4b48e')
RUBY = hexc('#d0203a')


def item():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    # the cover: cols 1..12, rows 0..13 (as Wreckback's), outline, spine on the left, pages right and under
    for y in range(0, 14):
        for x in range(1, 13):
            p[x, y] = LEATHER
    for x in range(2, 13):
        p[x, 0] = OUT
    for y in range(1, 14):
        p[1, y] = OUT
    for y in range(1, 13):
        p[2, y] = SPINE
    # pages: col 13 and row 14
    for y in range(3, 14):
        p[13, y] = PAGE if y % 2 else PAGE_D
    for x in range(2, 14):
        p[x, 14] = PAGE_D if x % 2 else PAGE
    p[2, 14] = OUT
    # gold trim round the cover (inset one), bright corners
    for x in range(3, 13):
        p[x, 1] = GOLD
        p[x, 13] = GOLD_D
    for y in range(1, 14):
        p[3, y] = GOLD_D
        p[12, y] = GOLD
    for (x, y) in ((3, 1), (12, 1), (3, 13), (12, 13)):
        p[x, y] = GOLD_L
    # leather texture inside
    rnd = random.Random(7)
    for y in range(2, 13):
        for x in range(4, 12):
            r = rnd.random()
            p[x, y] = LEATHER_L if r < 0.12 else LEATHER_D if r < 0.3 else LEATHER
    # the crown (cols 5..10, rows 3..5)
    crown = ["#.##.#", "######", "#RGGR#"]
    crown = ["G.GG.G", "GGGGGG", "GgRRgG"]
    for j, row in enumerate(crown):
        for i, ch in enumerate(row):
            x, y = 5 + i, 3 + j
            if ch == 'G': p[x, y] = GOLD
            elif ch == 'g': p[x, y] = GOLD_D
            elif ch == 'R': p[x, y] = RUBY
    p[5, 3] = GOLD_L
    p[10, 3] = GOLD_L
    # the key under it: a ring bow (rows 7..9), a two-wide shaft, a tooth on the right
    for (x, y) in ((6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9)):
        p[x, y] = GOLD
    p[6, 7] = GOLD_L
    p[7, 8] = LEATHER_D
    p[8, 8] = LEATHER_D
    for y in range(10, 13):
        p[7, y] = GOLD
        p[8, y] = GOLD_D
    p[9, 11] = GOLD
    p[9, 12] = GOLD_D
    # the eight giants' marks, like gems set down both sides of the cover
    order = ['pitchgut', 'furrowmaw', 'cerberus', 'hollowbell', 'lanternwillow', 'wreckback', 'swarmforge',
             'gloamwick']
    spots = [(4, 6), (4, 8), (4, 10), (4, 12), (11, 6), (11, 8), (11, 10), (11, 12)]
    for k, (x, y) in zip(order, spots):
        p[x, y] = hexc(MARKS[k])
    return im


# ---------------------------------------------------------------- the screen
def noise_tile(w, h, base, var, seed, fibres=0):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h), base)
    p = im.load()
    for y in range(h):
        for x in range(w):
            f = 1 + (rnd.random() - 0.5) * var
            p[x, y] = shade(base, f)
    # soft blotches that wrap (tileable)
    for _ in range(w * h // 300):
        cx, cy, r = rnd.randrange(w), rnd.randrange(h), rnd.randint(3, 9)
        f = 1 + (rnd.random() - 0.55) * var * 0.9
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    x, y = (cx + dx) % w, (cy + dy) % h
                    p[x, y] = shade(p[x, y], 1 + (f - 1) * (1 - (dx * dx + dy * dy) / (r * r + 1)))
    for _ in range(fibres):
        x, y = rnd.randrange(w), rnd.randrange(h)
        ln, dx = rnd.randint(3, 8), rnd.choice((-1, 1))
        for i in range(ln):
            xx, yy = (x + i) % w, (y + (i * dx) // 4) % h
            p[xx, yy] = shade(p[xx, yy], 0.94)
    return im


def parchment():
    return noise_tile(64, 64, hexc('#ecdcb4'), 0.05, 11, fibres=40)


def index_paper():
    return noise_tile(64, 64, hexc('#d9c597'), 0.06, 12, fibres=30)


def frame():
    """40x40, 9-slice with a 10 px edge: crimson leather, a gold rule inside, gold corner pieces"""
    S, E = 40, 10
    lea = noise_tile(S, S, LEATHER, 0.16, 21)
    p = lea.load()
    # darker toward the outside, a little lighter toward the page
    for y in range(S):
        for x in range(S):
            d = min(x, y, S - 1 - x, S - 1 - y)
            if d < E:
                p[x, y] = shade(p[x, y], 0.78 + 0.22 * d / E)
    # outline
    for i in range(S):
        for (x, y) in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            p[x, y] = OUT
    # a gold rule 4 in, and a thin dark line inside it
    def rect(o, c):
        for i in range(o, S - o):
            for (x, y) in ((i, o), (i, S - 1 - o), (o, i), (S - 1 - o, i)):
                p[x, y] = c
    rect(3, GOLD_D)
    for i in range(4, S - 4):
        p[i, 3] = GOLD
        p[3, i] = GOLD
    rect(4, shade(LEATHER_D, 0.8))
    # the page's own edge (inner border of the 9-slice): dark line, then a gold hairline
    rect(E - 2, SPINE)
    rect(E - 1, GOLD_D)
    # the corners: a gold bracket with a ruby
    corner = ["GGGGg.",
              "GLRRG.",
              "GRRRG.",
              "GRRLG.",
              "gGGGg.",
              "......"]
    for (ox, oy, fx, fy) in ((1, 1, 1, 1), (S - 2, 1, -1, 1), (1, S - 2, 1, -1), (S - 2, S - 2, -1, -1)):
        for j, row in enumerate(corner):
            for i, ch in enumerate(row):
                x, y = ox + fx * i, oy + fy * j
                if ch == 'G': p[x, y] = GOLD
                elif ch == 'g': p[x, y] = GOLD_D
                elif ch == 'L': p[x, y] = GOLD_L
                elif ch == 'R': p[x, y] = RUBY
    return lea


def tabs():
    """32x32: a tab (top half: not picked, bottom half: picked), 9-slice edge 4"""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    p = im.load()
    for half, (fill, edge, top) in enumerate(((hexc('#d2bc8a'), hexc('#8a6a40'), hexc('#c0a670')),
                                              (hexc('#f4e6c2'), hexc('#7a2a1a'), GOLD))):
        oy = half * 16
        for y in range(16):
            for x in range(32):
                p[x, oy + y] = fill
        for x in range(32):
            p[x, oy] = edge
            p[x, oy + 1] = top
        for y in range(16):
            p[0, oy + y] = edge
            p[31, oy + y] = edge
        # rounded top corners
        for (x, y) in ((0, 0), (31, 0)):
            p[x, oy + y] = (0, 0, 0, 0)
        if half == 0:
            for x in range(32):
                p[x, oy + 15] = edge
    return im


# 16x16 section icons, drawn as little pictures (pixel strings, a palette each)
ICONS = {}


def icon(name, rows, pal):
    ICONS[name] = (rows, pal)


K = '#2a1a10'
icon('stats', [  # a heart and a sword
    "................",
    "..KK.KK.........",
    ".KrrKrrK........",
    ".KrRrrrK.....KK.",
    ".KrrrrrK....KwK.",
    "..KrrrK....KwK..",
    "...KrK....KwK...",
    "....K....KwK....",
    "........KwK.....",
    "...KK..KwK......",
    "...KgK.KK.......",
    "....KgKK........",
    "....KKgK........",
    "...KbKKgK.......",
    "...KK..KK.......",
    "................"], {'K': K, 'r': '#d03030', 'R': '#ff9090', 'w': '#e0e8f0', 'g': '#c09030', 'b': '#704020'})
icon('moves', [  # a claw slash
    "................",
    "...........K....",
    "..........KoK...",
    ".....K...KoK....",
    "....KoK.KoK..K..",
    "...KoK.KoK..KoK.",
    "..KoK.KoK..KoK..",
    ".KoK.KoK..KoK...",
    ".KK.KoK..KoK....",
    "...KoK..KoK.....",
    "...KK..KoK......",
    "......KoK.......",
    "......KK........",
    "................",
    "................",
    "................"], {'K': K, 'o': '#e07020'})
icon('spawning', [  # an egg with spots
    "................",
    "......KKKK......",
    ".....KeeeeK.....",
    "....KeeseeeK....",
    "....KeeeeseK....",
    "...KeseeeeeeK...",
    "...KeeeeseeeK...",
    "...KeeeeeeeeK...",
    "...KeeseeeseK...",
    "...KeeeeeeeeK...",
    "....KeeeeseK....",
    "....KdeeeedK....",
    ".....KKKKKK.....",
    "................",
    "................",
    "................"], {'K': K, 'e': '#f0e6c8', 's': '#5a8a3a', 'd': '#c8b890'})
icon('behaviour', [  # a face: mood
    "................",
    ".....KKKKKK.....",
    "...KKyyyyyyKK...",
    "..KyyyyyyyyyyK..",
    "..KyyKyyyyKyyK..",
    ".KyyyKyyyyKyyyK.",
    ".KyyyyyyyyyyyyK.",
    ".KyyyyyyyyyyyyK.",
    ".KyyKyyyyyyKyyK.",
    ".KyyyKKyyKKyyyK.",
    "..KyyyyKKyyyyK..",
    "..KyyyyyyyyyyK..",
    "...KKyyyyyyKK...",
    ".....KKKKKK.....",
    "................",
    "................"], {'K': K, 'y': '#e8c048'})
icon('healing', [  # a red cross in a heart
    "................",
    "..KKK....KKK....",
    ".KrrrK..KrrrK...",
    "KrrrrrKKrrrrrK..",
    "KrrrrrwwrrrrrK..",
    "KrrrrrwwrrrrrK..",
    "KrrrwwwwwwrrrK..",
    ".KrrwwwwwwrrK...",
    "..KrrrwwrrrK....",
    "...KrrwwrrK.....",
    "....KrrrrK......",
    ".....KrrK.......",
    "......KK........",
    "................",
    "................",
    "................"], {'K': K, 'r': '#d04040', 'w': '#ffffff'})
icon('taming', [  # a closed book with a gold clasp
    "................",
    "..KKKKKKKKKK....",
    "..KbbbbbbbbbK...",
    "..KbbbbbbbbbpK..",
    "..KbbggggbbbpK..",
    "..KbbgbbgbbbpK..",
    "..KbbggggbbbpK..",
    "..KbbbbbbbbGpK..",
    "..KbbbbbbbbGpK..",
    "..KbbbbbbbbbpK..",
    "..KbbbbbbbbbpK..",
    "..KbbbbbbbbbpK..",
    "..KKKKKKKKKKpK..",
    "...KpppppppppK..",
    "....KKKKKKKKKK..",
    "................"], {'K': K, 'b': '#3a6a8a', 'g': '#e8c048', 'G': '#e8c048', 'p': '#f0e4c4'})
icon('loot', [  # a chest
    "................",
    "................",
    "..KKKKKKKKKKKK..",
    "..KwwwwwwwwwwK..",
    ".KwwwwwwwwwwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KKKKKKggKKKKKK.",
    ".KwwwwwgGgwwwwK.",
    ".KwwwwwgggwwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KddddddddddddK.",
    ".KKKKKKKKKKKKKK.",
    "................",
    "................",
    "................"], {'K': K, 'w': '#a8743c', 'd': '#7a5028', 'g': '#e8c048', 'G': '#fff0a0'})
icon('players', [  # a player's head
    "................",
    "....KKKKKKKK....",
    "....KhhhhhhK....",
    "....KhhhhhhK....",
    "....KssssssK....",
    "....KsWksWkK....",
    "....KssssssK....",
    "....KssmmssK....",
    "....KKKKKKKK....",
    "...KccccccccK...",
    "..KccccccccccK..",
    "..KccKccccKccK..",
    "..KccKccccKccK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................"], {'K': K, 'h': '#4a2e18', 's': '#d8a07a', 'W': '#ffffff', 'k': '#3050a0', 'm': '#a05040',
                         'c': '#3aa0b0'})
icon('effects', [  # a music note and sparkles
    "................",
    ".........KKKKK..",
    "........KpppppK.",
    "........KpKKKpK.",
    "........KpK.KpK.",
    "..y.....KpK.KpK.",
    ".yYy....KpK.KpK.",
    "..y...KKKpK.KpK.",
    ".....KpppK.KKpK.",
    ".....KpppKKpppK.",
    "......KKK.KpppK.",
    "...........KKK..",
    "..y.............",
    ".yYy.....y......",
    "..y.....yYy.....",
    ".........y......"], {'K': K, 'p': '#7a48c8', 'y': '#e8c048', 'Y': '#fff8c0'})
icon('worldgen', [  # a little world: hills and a sun
    "................",
    "..........yy....",
    ".........yYYy...",
    ".........yYYy...",
    "..........yy....",
    "................",
    "....KK..........",
    "...KggK...KK....",
    "..KggggK.KggK...",
    ".KggggggKggggK..",
    "KggGgggggggggGK.",
    "KddddddddddddddK",
    "KddsddddsdddddsK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
    "................"], {'K': K, 'g': '#5aa03a', 'G': '#8ad05a', 'd': '#8a5a30', 's': '#a8a8a8', 'y': '#e8c048',
                         'Y': '#fff8c0'})
icon('misc', [  # a gear
    "................",
    "......KKKK......",
    "...KK.KiiK.KK...",
    "..KiiKKiiKKiiK..",
    "..KiiiiiiiiiiK..",
    "...KiiKKKKiiK...",
    ".KKKiK....KiKKK.",
    ".KiiiK....KiiiK.",
    ".KiiiK....KiiiK.",
    ".KKKiK....KiKKK.",
    "...KiiKKKKiiK...",
    "..KiiiiiiiiiiK..",
    "..KiiKKiiKKiiK..",
    "...KK.KiiK.KK...",
    "......KKKK......",
    "................"], {'K': K, 'i': '#9a9aa6'})
icon('home', [  # a house
    "................",
    ".......KK.......",
    "......KrrK..KK..",
    ".....KrrrrK.KK..",
    "....KrrrrrrKKK..",
    "...KrrrrrrrrKK..",
    "..KrrrrrrrrrrK..",
    ".KKKKKKKKKKKKKK.",
    "..KwwwwwwwwwwK..",
    "..KwKKwwwKKKwK..",
    "..KwKbwwwKgKwK..",
    "..KwKKwwwKKKwK..",
    "..KwwwwwwKdKwK..",
    "..KwwwwwwKdKwK..",
    "..KKKKKKKKKKKK..",
    "................"], {'K': K, 'r': '#b04030', 'w': '#e8d8b0', 'b': '#80b8e8', 'g': '#80b8e8', 'd': '#7a5028'})
icon('live', [  # an eye
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKwwwwwwKK...",
    "..KwwwKKKKwwwK..",
    ".KwwwKbbbbKwwwK.",
    "KwwwwKbKKbKwwwwK",
    "KwwwwKbKKbKwwwwK",
    ".KwwwKbbbbKwwwK.",
    "..KwwwKKKKwwwK..",
    "...KKwwwwwwKK...",
    ".....KKKKKK.....",
    "................",
    "................",
    "................"], {'K': K, 'w': '#f4eee0', 'b': '#3a8a5a'})
icon('presets', [  # a scroll
    "................",
    "..KKKKKKKKKKK...",
    ".KpKppppppppK...",
    ".KpKKKKKKKKKKK..",
    "..KpppppppppK...",
    "..KpllllllppK...",
    "..KpppppppppK...",
    "..KpllllllppK...",
    "..KpppppppppK...",
    "..KpllllpppppK..",
    "..KpppppppppK...",
    "..KKKKKKKKKKKK..",
    "...KpppppppppKK.",
    "...KKKKKKKKKKK..",
    "................",
    "................"], {'K': K, 'p': '#f0e2bc', 'l': '#8a6a48'})
icon('changes', [  # a quill
    "................",
    "............KK..",
    "..........KKwK..",
    ".........KwwwK..",
    "........KwwwK...",
    ".......KwwwK....",
    "......KwwwK.....",
    ".....KwwwK......",
    ".....KwwK.......",
    "....KwKK........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".Kk.............",
    "................",
    "................"], {'K': K, 'w': '#f4f0e4', 'k': '#2050a0'})
icon('all', [  # a crown
    "................",
    "................",
    "................",
    ".g....g....g....",
    ".Kg..gKg..gK....",
    ".KgK.KgK.KgK....",
    ".KggKgggKggK....",
    ".KgggggggggK....",
    ".KgrggbgggrK....",
    ".KgggggggggK....",
    ".KKKKKKKKKKK....",
    "................",
    "................",
    "................",
    "................",
    "................"], {'K': K, 'g': '#e8c048', 'r': '#d0203a', 'b': '#3a7ad0'})
icon('general', [  # balance scales
    "................",
    ".......KK.......",
    ".KKKKKKKKKKKKKK.",
    ".K.....KK.....K.",
    "KyK....KK....KyK",
    "KyK....KK....KyK",
    "yyyy...KK...yyyy",
    "KKKK...KK...KKKK",
    ".......KK.......",
    ".......KK.......",
    ".......KK.......",
    ".....KKKKKK.....",
    "....KyyyyyyK....",
    "....KKKKKKKK....",
    "................",
    "................"], {'K': K, 'y': '#e8c048'})
icon('special', [  # a star
    "................",
    ".......KK.......",
    "......KyyK......",
    "......KyyK......",
    ".KKKKKKyyKKKKKK.",
    ".KyyyyyYYyyyyyK.",
    "..KyyyyYYyyyyK..",
    "...KyyyyyyyyK...",
    "....KyyyyyyK....",
    "...KyyyKKyyyK...",
    "...KyyK..KyyK...",
    "..KyyK....KyyK..",
    "..KKK......KKK..",
    "................",
    "................",
    "................"], {'K': K, 'y': '#e8c048', 'Y': '#fff8c0'})
icon('search', [  # a magnifying glass
    "................",
    "....KKKK........",
    "...KwwwwK.......",
    "..KwwbbwwK......",
    "..KwbwwwwK......",
    "..KwwwwwwK......",
    "..KwwwwwwK......",
    "...KwwwwKK......",
    "....KKKKKgK.....",
    ".........KgK....",
    "..........KgK...",
    "...........KgK..",
    "............KK..",
    "................",
    "................",
    "................"], {'K': K, 'w': '#cfe4f0', 'b': '#ffffff', 'g': '#8a5a30'})

ICON_ORDER = ['stats', 'moves', 'spawning', 'behaviour', 'healing', 'taming', 'loot', 'players', 'effects', 'worldgen',
              'misc', 'home', 'live', 'presets', 'changes', 'all', 'general', 'special', 'search']


def icons():
    im = Image.new('RGBA', (16 * len(ICON_ORDER), 16), (0, 0, 0, 0))
    p = im.load()
    for n, name in enumerate(ICON_ORDER):
        rows, pal = ICONS[name]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    p[n * 16 + x, y] = hexc(pal[ch])
    return im


def flourish():
    """a 64x7 divider: a rubric line with a diamond in the middle"""
    im = Image.new('RGBA', (64, 7), (0, 0, 0, 0))
    p = im.load()
    c, d = hexc('#8a3a1a'), hexc('#c08a3a')
    for x in range(64):
        a = min(x, 63 - x)
        if a < 2:
            continue
        p[x, 3] = c if a > 6 else hexc('#8a3a1a', 160)
    for k in range(4):
        for x in range(32 - k, 32 + k):
            p[x, 3 - (3 - k)] = d
            p[x, 3 + (3 - k)] = d
    for x in range(29, 35):
        p[x, 3] = d
    return im


def save(im, rel, preview, scale):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    if preview:
        os.makedirs(preview, exist_ok=True)
        im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(
            os.path.join(preview, os.path.basename(rel).replace('.png', '_x%d.png' % scale)))


if __name__ == '__main__':
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    save(item(), 'item/admin_book.png', preview, 16)
    save(parchment(), 'gui/admin/parchment.png', preview, 4)
    save(index_paper(), 'gui/admin/index.png', preview, 4)
    save(frame(), 'gui/admin/frame.png', preview, 6)
    save(tabs(), 'gui/admin/tab.png', preview, 6)
    save(icons(), 'gui/admin/icons.png', preview, 4)
    save(flourish(), 'gui/admin/flourish.png', preview, 6)
    print('written to', TEX)
