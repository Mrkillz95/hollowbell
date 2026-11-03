# (Part 9) The admin book's pictures for new systems B (villages, followers, weather, underworld, sacred ground,
# mirror, chaos, and the heading), in the same pixel style as tools/systems_icons.py. Writes
# src/main/resources/assets/jj_giants/textures/gui/admin/icons_p9.png; with --preview DIR also a big copy to look at.
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(HERE, 'src', 'main', 'resources', 'assets', 'jj_giants', 'textures')
K = '#2a1a10'


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


ICONS = {}


def icon(name, rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
    ICONS[name] = (rows, pal)


icon('villages', [  # a village house with its bell
    "................",
    ".......KK.......",
    "......KrrK......",
    ".....KrrrrK.....",
    "....KrrrrrrK....",
    "...KrrrrrrrrK...",
    "..KKKKKKKKKKKK..",
    "...KwwwwwwwwK...",
    "...KwKKwwyywK...",
    "...KwKbKwyywK...",
    "...KwKKwwyywK...",
    "...KwwwwwwwwK...",
    "...KwwwKKwwwK...",
    "...KwwwKdKwwK...",
    "...KKKKKKKKKK...",
    "................"], {'K': K, 'r': '#a84a30', 'w': '#d8c090', 'y': '#e8c040', 'b': '#7aa8d8', 'd': '#6a4020'})
icon('followers', [  # a band of creatures behind their giant
    "................",
    "..KK............",
    ".KggK...........",
    ".KgKK..KK.......",
    ".KggK.KggK......",
    "..KK..KgKK..KK..",
    ".KggK.KggK.KggK.",
    ".KggK..KK..KgKK.",
    ".KggK.KggK.KggK.",
    ".K..K.KggK..KK..",
    ".K..K.KggK.KggK.",
    "......K..K.KggK.",
    "......K..K.KggK.",
    "...........K..K.",
    "...........K..K.",
    "................"], {'K': K, 'g': '#5a8a4a'})
icon('weather', [  # a storm cloud with lightning and rain
    "................",
    "......KKKK......",
    "....KKccccKK....",
    "...KccccccccK...",
    ".KKccccCcccccK..",
    "KccccccccCccccK.",
    "KcccCccccccccCK.",
    ".KKKKKKKKKKKKK..",
    "..b...KyK..b....",
    ".b...KyK..b..b..",
    "....KyyyK.......",
    "..b..KyK...b....",
    ".b..KyK...b..b..",
    "....KK..........",
    "..b.....b.......",
    "................"], {'K': K, 'c': '#7a8090', 'C': '#9aa0b0', 'y': '#f0e060', 'b': '#5a80c0'})
icon('underworld', [  # a nether portal over netherrack
    "................",
    "...KKKKKKKKKK...",
    "...KoooooooooK..",
    "...KoppppppoK...",
    "...KopPpppPoK...",
    "...KoppPpppoK...",
    "...KopppPppoK...",
    "...KoppppPpoK...",
    "...KopPpppPoK...",
    "...KoppppppoK...",
    "...KooooooooK...",
    "..KKKKKKKKKKKK..",
    ".KnnNnnnnNnnnnK.",
    ".KnNnnnNnnnNnnK.",
    ".KKKKKKKKKKKKKK.",
    "................"], {'K': K, 'o': '#2a1a3a', 'p': '#8a3ad0', 'P': '#c070f0', 'n': '#8a2a2a', 'N': '#b84040'})
icon('sacred', [  # a pickaxe struck through: no breaking here
    "................",
    ".....KKKKKK.....",
    "...KKrrrrrrKK...",
    "..KrrKKKKKKrrK..",
    "..KrK.KKKKKrrK..",
    ".KrK..KssssKrrK.",
    ".KrK....Kh.KrrK.",
    ".KrK...KhKrrKrK.",
    ".KrK..KhKrrK.rK.",
    ".KrK.KhKrrK..rK.",
    ".KrrKhKrrK..KrK.",
    "..KrrKrrK..KrK..",
    "..KrrrrKKKKrrK..",
    "...KKrrrrrrKK...",
    ".....KKKKKK.....",
    "................"], {'K': K, 'r': '#c03030', 's': '#9a9aa8', 'h': '#8a6030'})
icon('mirror', [  # a hand mirror with a shine
    "................",
    ".....KKKKK......",
    "....KgggggK.....",
    "...KgmmmmmgK....",
    "...KgmWmmmgK....",
    "...KgmmWmmgK....",
    "...KgmmmmmgK....",
    "...KgmmmmmgK....",
    "....KgmmmgK.....",
    ".....KgggK......",
    "......KhK.......",
    "......KhK.......",
    "......KhK.......",
    "......KhK.......",
    "......KKK.......",
    "................"], {'K': K, 'g': '#d8a838', 'm': '#a8c8e0', 'W': '#ffffff', 'h': '#8a5a30'})
icon('chaos', [  # two dice tumbling
    "................",
    "..KKKKKK........",
    ".KwwwwwwK.......",
    ".KwkwwwwK.......",
    ".KwwwwwwK.......",
    ".KwwwwkwK.......",
    ".KwwwwwwK.KKKK..",
    "..KKKKKKKKwwwwK.",
    ".........KwkwkwK",
    ".........KwwwwwK",
    ".........KwwkwwK",
    ".........KwwwwwK",
    ".........KwkwkwK",
    "..........KwwwwK",
    "...........KKKK.",
    "................"], {'K': K, 'w': '#f0ead8', 'k': '#c03030'})
icon('systems_b', [  # a tree on a hill under a cloud: the world around them
    "........KKKK....",
    "......KKccccK...",
    ".....KccccccK...",
    "......KKKKKK....",
    "................",
    "......KKKK......",
    ".....KggggK.....",
    "....KggGgggK....",
    "....KgGgggGK....",
    ".....KggggK.....",
    ".......Kt.......",
    ".......Kt.......",
    "....KKKKKKKK....",
    "..KKhhhhhhhhKK..",
    ".KhhhhhhhhhhhhK.",
    "KKKKKKKKKKKKKKKK"], {'K': K, 'c': '#d8dce8', 'g': '#4a8a3a', 'G': '#6aaa4a', 't': '#7a5030',
                          'h': '#8a9a4a'})

ORDER = ['villages', 'followers', 'weather', 'underworld', 'sacred', 'mirror', 'chaos', 'systems_b']


def icons():
    im = Image.new('RGBA', (16 * len(ORDER), 16), (0, 0, 0, 0))
    p = im.load()
    for n, name in enumerate(ORDER):
        rows, pal = ICONS[name]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    p[n * 16 + x, y] = hexc(pal[ch])
    return im


if __name__ == '__main__':
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    im = icons()
    path = os.path.join(TEX, 'gui', 'admin', 'icons_p9.png')
    im.save(path)
    if preview:
        os.makedirs(preview, exist_ok=True)
        im.resize((im.width * 8, im.height * 8), Image.NEAREST).save(os.path.join(preview, 'icons_p9_x8.png'))
    print('written', path)
