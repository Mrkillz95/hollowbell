# (Part 7) The admin book's Fun and World tools pictures and the Biome Brush item, in the same pixel style as
# tools/admin_textures.py. Writes src/main/resources/assets/jj_giants/textures/gui/admin/icons_p7.png and
# textures/item/biome_brush.png; with --preview DIR also big copies to look at.
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
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    ICONS[name] = (rows, pal)


icon('fun', [  # a jester's star with bells
    "................",
    ".......KK.......",
    "......KyyK......",
    "......KyyK......",
    "..KK.KyyyyK.KK..",
    ".KrrKyyYYyyKbbK.",
    ".KrrrKyYYyKbbbK.",
    "..KrrrKyyKbbbK..",
    "...KrrrKKbbbK...",
    "....KrrKKbbK....",
    "....KrK..KbK....",
    "...KgK....KgK...",
    "...KgK....KgK...",
    "....K......K....",
    "................",
    "................"], {'K': K, 'y': '#e8b838', 'Y': '#fff0a0', 'r': '#c83030', 'b': '#3060c0', 'g': '#e0c040'})
icon('world', [  # a hammer over a globe
    "................",
    "..KKKKKK........",
    "..KsssssK.......",
    "..KKKKsK........",
    "......KwK.......",
    ".....KKwKKK.....",
    "...KKgggKbbKK...",
    "..KggbgKwKgbgK..",
    "..KgbbbKwKbbgK..",
    ".KbbbggKwKgbbbK.",
    ".KbgggbKKKbbgbK.",
    ".KbbgbbbbgggbbK.",
    "..KbbbbgggbbbK..",
    "..KKbbbbbbbbKK..",
    "....KKKKKKKK....",
    "................"], {'K': K, 's': '#a0a8b0', 'w': '#8a5a30', 'g': '#58a040', 'b': '#3a78c0'})
icon('watch', [  # an eye
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKwwwwwwKK...",
    "..KwwwKKKKwwwK..",
    ".KwwwKbbbbKwwwK.",
    "KwwwKbbKKbbKwwwK",
    "KwwwKbbKKbbKwwwK",
    ".KwwwKbbbbKwwwK.",
    "..KwwwKKKKwwwK..",
    "...KKwwwwwwKK...",
    ".....KKKKKK.....",
    "................",
    "................",
    "................"], {'K': K, 'w': '#f0ead8', 'b': '#3a78c0'})
icon('fight', [  # two crossed swords
    "................",
    ".KK..........KK.",
    ".KwK........KwK.",
    "..KwK......KwK..",
    "...KwK....KwK...",
    "....KwK..KwK....",
    ".....KwKKwK.....",
    "......KwwK......",
    "......KwwK......",
    ".....KwKKwK.....",
    "...KKgK..KgKK...",
    "..KgKK....KKgK..",
    "...KbK....KbK...",
    "..KbK......KbK..",
    "..KK........KK..",
    "................"], {'K': K, 'w': '#e0e8f0', 'g': '#c09030', 'b': '#704020'})
icon('tag', [  # a label on a string
    "................",
    "..........KK....",
    ".........KsK....",
    "........KsK.....",
    "...KKKKKKK......",
    "..KyyyyyyyK.....",
    ".KyyKKyyyyyK....",
    ".KyyKKyyyyyyK...",
    ".KyyyyyyyyyyyK..",
    ".KyrrrrrryyyyK..",
    ".KyyyyyyyyyyK...",
    ".KyrrrrryyyK....",
    "..KyyyyyyyK.....",
    "...KKKKKKK......",
    "................",
    "................"], {'K': K, 'y': '#e8c860', 's': '#a08060', 'r': '#a04020'})
icon('brush', [  # a paint brush with green paint
    "................",
    "............KK..",
    "...........KwK..",
    "..........KwK...",
    ".........KwK....",
    "........KwK.....",
    ".......KwK......",
    "......KsK.......",
    ".....KssK.......",
    "....KgggK.......",
    "...KgGgK........",
    "..KgGgK.........",
    "..KggK..........",
    ".KgK............",
    ".KK.............",
    "................"], {'K': K, 'w': '#a06a3a', 's': '#b0b8c0', 'g': '#3a9a40', 'G': '#8ad070'})
icon('build', [  # an arena: a ring wall with pillars and lanterns
    "................",
    "..y..........y..",
    ".KyK........KyK.",
    ".KsK..KKKK..KsK.",
    ".KsKKKssssKKKsK.",
    ".KsssssssssssKs.",
    ".KsKKKKKKKKKKsK.",
    ".Ks..........sK.",
    ".Ks..dddddd..sK.",
    ".Ks.dddddddd.sK.",
    ".Ks..dddddd..sK.",
    ".KsKKKKKKKKKKsK.",
    ".KsssssssssssK..",
    ".KKKKKKKKKKKKK..",
    "................",
    "................"], {'K': K, 's': '#9a9a9a', 'y': '#f0c040', 'd': '#c0b090'})
icon('mix', [  # two arrows going round each other
    "................",
    "......KKKK......",
    "....KKbbbbKK....",
    "...KbbKKKKbbK...",
    "..KbbK....KbK...",
    "..KbK....KbbbK..",
    "..KbK.....KbK...",
    "................",
    "...KoK.....KoK..",
    "..KoooK....KoK..",
    "...KoK....KooK..",
    "...KooK..KooK...",
    "....KooKKooK....",
    ".....KKooKK.....",
    ".......KK.......",
    "................"], {'K': K, 'b': '#3a78c0', 'o': '#e07020'})

ORDER = ['fun', 'world', 'watch', 'fight', 'tag', 'brush', 'build', 'mix']


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


def brush_item():
    """the Biome Brush: a gilt-banded brush, its bristles dipped in the giants' colours"""
    rows = [
        "................",
        "...........KK...",
        "..........KwwK..",
        ".........KwwK...",
        "........KwwK....",
        ".......KwwK.....",
        "......KggK......",
        ".....KgGgK......",
        "....KaabK.......",
        "...KaabbK.......",
        "..KccabK........",
        "..KcddK.........",
        ".KeedK..........",
        ".KeeK...........",
        "..KK............",
        "................"]
    pal = {'K': '#1a1008', 'w': '#7a4a24', 'g': '#d8a830', 'G': '#fff0a0', 'a': '#3a9a40', 'b': '#8ad070',
           'c': '#c83030', 'd': '#e07020', 'e': '#6a40b0'}
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                p[x, y] = hexc(pal[ch])
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
    save(icons(), 'gui/admin/icons_p7.png', preview, 6)
    save(brush_item(), 'item/biome_brush.png', preview, 16)
    print('written to', TEX)
