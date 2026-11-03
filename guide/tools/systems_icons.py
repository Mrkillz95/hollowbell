# (Part 8) The admin book's Systems page pictures, in the same pixel style as tools/admin_textures.py and
# tools/fun_textures.py. Writes src/main/resources/assets/jj_giants/textures/gui/admin/icons_p8.png; with
# --preview DIR also a big copy to look at.
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


icon('systems', [  # the sun and the moon round a sprout: the world's own turning
    "................",
    "..KKK......KKK..",
    ".KyyyK....KmmK..",
    ".KyYyK...KmmK...",
    ".KyyyK...KmmK...",
    "..KKK.....KmmK..",
    "...........KKK..",
    ".......KK.......",
    "......KggK......",
    ".....KgKKgK.....",
    "......KggK......",
    ".......Kw.......",
    ".......Kw.......",
    "....KKKKKKKK....",
    "...KddddddddK...",
    "....KKKKKKKK...."], {'K': K, 'y': '#e8b838', 'Y': '#fff0a0', 'm': '#c8d0e8', 'g': '#58a040',
                          'w': '#3a7a30', 'd': '#7a5030'})
icon('herds', [  # three beasts walking together, the leader in front
    "................",
    "..........KK....",
    ".........KbbKK..",
    "...KK...KbbbbbK.",
    "..KbbKK.KbKKbK..",
    "..KbbbbbK.......",
    "..KbKKbK..KK....",
    ".........KbbKK..",
    "....KKKKKbbbbbK.",
    "...KbbbbbbbbbbK.",
    "..KbbbbbbbbbbK..",
    "..KbbbbbbbbbK...",
    "..KbK.KbK.KbK...",
    "..KbK.KbK.KbK...",
    "..KK..KK..KK....",
    "................"], {'K': K, 'b': '#8a6a48'})
icon('growth', [  # a small figure and a big one, an arrow up
    "................",
    "..........KK....",
    ".........KggK...",
    "..KK.....KggK...",
    ".KyyK...KggggK..",
    "KyyyyK..KggggK..",
    ".KyyK...KggggK..",
    "..KK.....KggK...",
    ".........KggK...",
    "....K....KggK...",
    "...KaK...KggK...",
    "..KaaaK..KggK...",
    "...KaK...KggK...",
    "...KaK..KggggK..",
    "...KK...KKKKKK..",
    "................"], {'K': K, 'y': '#c8a070', 'g': '#a87848', 'a': '#3a9a40'})
icon('hunger', [  # a drumstick
    "................",
    "........KKKK....",
    ".......KmmmmK...",
    "......KmmmMmmK..",
    "......KmmMmmmK..",
    ".....KmmmmmmmK..",
    ".....KmmmmmmK...",
    "....KKmmmmmK....",
    "...KwKKmmmK.....",
    "..KwwK.KKK......",
    ".KwwK...........",
    "KwwK............",
    "KwK.............",
    ".K..............",
    "................",
    "................"], {'K': K, 'm': '#b85a30', 'M': '#e08a50', 'w': '#f0ead8'})
icon('loyalty', [  # a heart
    "................",
    "................",
    "..KKK....KKK....",
    ".KrrrK..KrrrK...",
    "KrrRrrKKrrrrrK..",
    "KrRrrrrrrrrrrK..",
    "KrrrrrrrrrrrrK..",
    "KrrrrrrrrrrrrK..",
    ".KrrrrrrrrrrK...",
    "..KrrrrrrrrK....",
    "...KrrrrrrK.....",
    "....KrrrrK......",
    ".....KrrK.......",
    "......KK........",
    "................",
    "................"], {'K': K, 'r': '#c83030', 'R': '#f08080'})
icon('grief', [  # bricks with a sprout growing back
    "................",
    ".......KK.......",
    "......KggK......",
    ".....KgKKgK.....",
    "......KggK......",
    ".......Kg.......",
    ".KKKKKKKKKKKKKK.",
    ".KbbbbKbbbbbbbK.",
    ".KbbbbKbbbbbbbK.",
    ".KKKKKKKKKKKKKK.",
    ".KbbbbbbbKbbbbK.",
    ".KbbbbbbbKbbbbK.",
    ".KKKKKKKKKKKKKK.",
    ".KbbbKbbbbbbbbK.",
    ".KKKKKKKKKKKKKK.",
    "................"], {'K': K, 'g': '#58a040', 'b': '#a85a40'})
icon('death', [  # a skull
    "................",
    "....KKKKKKK.....",
    "...KwwwwwwwK....",
    "..KwwwwwwwwwK...",
    "..KwwwwwwwwwK...",
    "..KwKKwwwKKwK...",
    "..KwKKwwwKKwK...",
    "..KwwwwKwwwwK...",
    "...KwwwKwwwK....",
    "....KwwwwwK.....",
    "....KwKwKwK.....",
    "....KKKKKKK.....",
    "................",
    "..KK.......KK...",
    "...KK.....KK....",
    "................"], {'K': K, 'w': '#f0ead8'})
icon('playerdeath', [  # a chest swallowed by jaws
    "................",
    "KKK..........KKK",
    "KwKK........KKwK",
    ".KwwK......KwwK.",
    "..KKKKKKKKKKKK..",
    "...KccccccccK...",
    "...KcKKKKKKcK...",
    "...KccccyycccK..",
    "...KccccyycccK..",
    "...KccccccccK...",
    "...KKKKKKKKKK...",
    "..KKKKKKKKKKKK..",
    ".KwwK......KwwK.",
    "KwKK........KKwK",
    "KKK..........KKK",
    "................"], {'K': K, 'w': '#f0ead8', 'c': '#9a6a30', 'y': '#e8b838'})
icon('scaling', [  # a balance, the heavy side down
    "................",
    ".......KK.......",
    ".......KK.......",
    ".KKKKKKKKKK.....",
    ".K....KK..KKKKK.",
    "KyK...KK......K.",
    "KyyK..KK.....KyK",
    "KyyyK.KK....KyyK",
    "KKKKK.KK...KyyyK",
    "......KK...KKKKK",
    "......KK........",
    "......KK........",
    ".....KKKK.......",
    "....KwwwwK......",
    "...KKKKKKKK.....",
    "................"], {'K': K, 'y': '#e8b838', 'w': '#8a5a30'})
icon('weak', [  # a target
    "................",
    ".....KKKKKK.....",
    "...KKrrrrrrKK...",
    "..KrrwwwwwwrrK..",
    "..KrwwrrrrwwrK..",
    ".KrwwrrwwrrwwrK.",
    ".KrwrrwwwwrrwrK.",
    ".KrwrwwRRwwrwrK.",
    ".KrwrwwRRwwrwrK.",
    ".KrwrrwwwwrrwrK.",
    ".KrwwrrwwrrwwrK.",
    "..KrwwrrrrwwrK..",
    "..KrrwwwwwwrrK..",
    "...KKrrrrrrKK...",
    ".....KKKKKK.....",
    "................"], {'K': K, 'r': '#c83030', 'w': '#f0ead8', 'R': '#801818'})
icon('fear', [  # a shadow with glowing eyes
    "................",
    "....KKKKKKK.....",
    "...KssssssssK...",
    "..KssssssssssK..",
    "..KsyysssyysK...",
    "..KsyysssyyssK..",
    "..KssssssssssK..",
    "..KssssKKssssK..",
    "..KsssKsssKssK..",
    "..KssssssssssK..",
    "..KssssssssssK..",
    "..KsKssKssKssK..",
    "..KK.KK.KK.KKK..",
    "................",
    "................",
    "................"], {'K': '#100810', 's': '#3a2a4a', 'y': '#f0e060'})
icon('seasons', [  # a full moon and a falling leaf
    "................",
    "....KKKKK.......",
    "...KmmmmmK......",
    "..KmmMmmmmK.....",
    "..KmmmmmMmK.....",
    "..KmMmmmmmK.....",
    "..KmmmmmmmK.....",
    "...KmmmmmK......",
    "....KKKKK.......",
    ".........KKK....",
    "........KooK....",
    ".......KooooK...",
    "......KooOoK....",
    ".....KKooK......",
    "....KK.KK.......",
    "................"], {'K': K, 'm': '#e8e8f0', 'M': '#b8b8c8', 'o': '#d87a28', 'O': '#a85018'})

ORDER = ['systems', 'herds', 'growth', 'hunger', 'loyalty', 'grief', 'death', 'playerdeath', 'scaling', 'weak',
         'fear', 'seasons']


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
    path = os.path.join(TEX, 'gui', 'admin', 'icons_p8.png')
    im.save(path)
    if preview:
        os.makedirs(preview, exist_ok=True)
        im.resize((im.width * 8, im.height * 8), Image.NEAREST).save(os.path.join(preview, 'icons_p8_x8.png'))
    print('written', path)
