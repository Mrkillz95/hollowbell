# (Part 12) The admin book's pictures for the Editor and Sound pages and share codes, in the same pixel style as
# tools/minigame_icons.py. Writes src/main/resources/assets/jj_giants/textures/gui/admin/icons_p12.png; with
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
ORDER = []


def icon(name, rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
    ICONS[name] = (rows, pal)
    ORDER.append(name)


icon('editor', [  # a painter's palette with four paints and a brush
    "................",
    "....KKKKKK......",
    "..KKwwwwwwKK....",
    ".KwwbbwwrrwwK...",
    ".KwwbbwwrrwwwK..",
    "Kwwwwwwwwwwwwwk.",
    "KwyywwKKwwwwwK.h",
    "KwyywK..Kwwwk.hK",
    "KwwwwK..Kwwk.hK.",
    "KwwwwwKKwwwkhK..",
    ".KwggwwwwwkhK...",
    ".KwggwwwwkbbK...",
    "..KKwwwwkbbK....",
    "....KKKKKKK.....",
    "................",
    "................"], {'K': K, 'k': '#8a6a40', 'w': '#e8d8b0', 'b': '#4a7ad0', 'r': '#c03030',
                         'y': '#e8c040', 'g': '#4a9a40', 'h': '#a07040'})
icon('sound', [  # a horn with music notes
    "..........K.....",
    "..........KK....",
    "..........KbK...",
    "..........K.K...",
    "........KKK.....",
    "...K...KbbK..K..",
    "..KyK..KKK...KK.",
    ".KyyyK.......KbK",
    "KyyyyyKKKK...K.K",
    "KyyyyyyyyyKKKK..",
    "KyyyyyyyyyyyyK..",
    "KyyyyyKKKKKKyK..",
    ".KyyyK.....KyK..",
    "..KyK......KK...",
    "...K............",
    "................"], {'K': K, 'y': '#e0b040', 'b': '#3a2a50'})
icon('share', [  # two hands of paper passing a code
    "................",
    "..KKKKKKK.......",
    "..KwwwwwKK......",
    "..KwKKKwwKK.....",
    "..KwwwwwwwK.....",
    "..KwKKKKKwK.....",
    "..KwwwwwwwK.KK..",
    "..KwKKKKwwK.KgK.",
    "..KwwwwwwwKKKggK",
    "..KKKKKKKKKgggggK"[0:16],
    "...........KggK.",
    "............KK..",
    "......KKKKKKKKK.",
    "......KwKwKwKwK.",
    "......KKKKKKKKK.",
    "................"], {'K': K, 'w': '#e8d8b0', 'g': '#4a9a40'})


def draw(name):
    rows, pal = ICONS[name]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            im.putpixel((x, y), hexc(pal[ch]))
    return im


def main():
    strip = Image.new('RGBA', (16 * len(ORDER), 16), (0, 0, 0, 0))
    for i, n in enumerate(ORDER):
        strip.paste(draw(n), (16 * i, 0))
    out = os.path.join(TEX, 'gui', 'admin', 'icons_p12.png')
    strip.save(out)
    print('wrote', out, ORDER)
    if '--preview' in sys.argv:
        d = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(d, exist_ok=True)
        strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(d, 'icons_p12.png'))


main()
