# (Part 10A) The admin book's pictures for the Minigames page (the page itself, and one per mode), in the same
# pixel style as tools/systems_icons_p9.py. Writes src/main/resources/assets/jj_giants/textures/gui/admin/
# icons_p10a.png; with --preview DIR also a big copy to look at.
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


icon('minigames', [  # two crossed swords, points up
    "KK............KK",
    "KsK..........KsK",
    ".KsK........KsK.",
    "..KsK......KsK..",
    "...KsK....KsK...",
    "....KsK..KsK....",
    ".....KsKKsK.....",
    "......KssK......",
    "......KssK......",
    ".....KsKKsK.....",
    "...KgKsK..KsKgK.",
    "..KggK....KggK..",
    ".KbKgK....KgKbK.",
    "KbK.K......K.KbK",
    "KK............KK",
    "................"], {'K': K, 's': '#d8dce0', 'g': '#c89a30', 'b': '#6a4020'})
icon('mg_royale', [  # a crown inside a closing ring
    "....KKKKKKKK....",
    "..KKrrrrrrrrKK..",
    ".Krr........rrK.",
    ".Kr..........rK.",
    "Kr..K..K...K..rK",
    "Kr..KyKyK.KyK.rK",
    "Kr..KyyyyKyyK.rK",
    "Kr..KyyyyyyyK.rK",
    "Kr..KyrybyryK.rK",
    "Kr..KyyyyyyyK.rK",
    "Kr..KKKKKKKKK.rK",
    ".Kr..........rK.",
    ".Krr........rrK.",
    "..KKrrrrrrrrKK..",
    "....KKKKKKKK....",
    "................"], {'K': K, 'r': '#c03030', 'y': '#e8c040', 'b': '#4a7ad0'})
icon('mg_bossrush', [  # a giant's skull with an hourglass
    "................",
    "....KKKKKKK.....",
    "...KbbbbbbbK....",
    "..KbbbbbbbbbK...",
    "..KbKKbbbKKbK...",
    "..KbKKbbbKKbK...",
    "..KbbbbKbbbbK...",
    "...KbbbbbbbK....",
    "...KbKbKbKbK....",
    "....KKKKKKK.KKK.",
    "...........KyyyK",
    "............KyK.",
    ".............K..",
    "............KyK.",
    "...........KyyyK",
    "............KKK."], {'K': K, 'b': '#e8e0c8', 'y': '#e0b040'})
icon('mg_siege', [  # a keep's tower with a flag
    "........Kr......",
    "........Krr.....",
    "........Krrr....",
    "........K.......",
    "...K.K.KK.K.K...",
    "...KKKKKKKKKK...",
    "...KssssssssK...",
    "...KsssKKsssK...",
    "...KsssKKsssK...",
    "...KssssssssK...",
    "...KssssssssK...",
    "...KsssKKsssK...",
    "...KssKddKssK...",
    "...KssKddKssK...",
    "..KKKKKKKKKKKK..",
    "................"], {'K': K, 'r': '#c03030', 's': '#9a9a9a', 'd': '#5a3a20'})
icon('mg_escort', [  # a shield over a winding road
    "................",
    "....KKKKKKKK....",
    "....KbbbybbK....",
    "....KbbbybbK....",
    "....KyyyyyyK....",
    "....KbbbybbK....",
    ".....KbbybK.....",
    "......KbbK......",
    ".......KK.......",
    "..........ttt...",
    "........ttt.....",
    "......ttt.......",
    "....ttt.........",
    "...tt...........",
    "..tt............",
    ".tt............."], {'K': K, 'b': '#3a6ab0', 'y': '#e8c040', 't': '#b08a50'})
icon('mg_legends', [  # a gold star with rising steps
    ".......KK.......",
    "......KyyK......",
    "......KyyK......",
    "KKKKKKKyyKKKKKKK",
    "KyyyyyyyyyyyyyyK",
    ".KyyyyyyyyyyyyK.",
    "..KKyyyyyyyyKK..",
    "....KyyyyyyK....",
    "...KyyyKKyyyK...",
    "...KyyK..KyyK...",
    "..KyyK....KyyK..",
    "..KKK......KKK..",
    "................",
    ".........KK.KK..",
    "......KK.KK.KK..",
    "...KK.KK.KK.KK.."], {'K': K, 'y': '#e8c040'})
icon('mg_possession', [  # a soul over a giant's eye
    "......KKKK......",
    ".....KwwwwK.....",
    "....KwKwwKwK....",
    "....KwwwwwwK....",
    "....KwwKKwwK....",
    ".....KwwwwK.....",
    "......KwwK......",
    ".......KK.......",
    "..KKKKKKKKKKKK..",
    ".KggggggggggggK.",
    "KggggKKKKKggggK.",
    "KgggKrrrrrKgggK.",
    "KgggKrrKrrKgggK.",
    ".KgggKKKKKgggK..",
    "..KKggggggggKK..",
    "....KKKKKKKK...."], {'K': K, 'w': '#a8e0f0', 'g': '#d8d0b8', 'r': '#c04030'})
icon('mg_rivals', [  # two banners, red and blue, facing
    "................",
    ".K............K.",
    ".KKrrr....bbbKK.",
    ".KrrrrK..KbbbbK.",
    ".KrryrK..KbwbbK.",
    ".KrrrrK..KbbbbK.",
    ".KrrrrK..KbbbbK.",
    ".KrrrK....KbbbK.",
    ".KrrK......KbbK.",
    ".KrK........KbK.",
    ".KK..........KK.",
    ".K............K.",
    ".K............K.",
    ".K............K.",
    "KKK..........KKK",
    "................"], {'K': K, 'r': '#c03030', 'b': '#3a6ab0', 'y': '#e8c040', 'w': '#e8e8e8'})
icon('mg_bounty', [  # a wanted scroll and a coin
    "................",
    "..KKKKKKKKKK....",
    ".KpppppppppK....",
    "..KpKKKKKKpK....",
    "..KpKggggKpK....",
    "..KpKgKKgKpK....",
    "..KpKggggKpK....",
    "..KpKKKKKKpK....",
    "..KppppppppK....",
    "..KpKKKKKKpK....",
    "..KppppppppK.KKK",
    "..KpKKKKKpKKyyyK",
    "..KppppppK.KyKyK",
    ".KppppppppKKyyyK",
    "..KKKKKKKKK.KKK.",
    "................"], {'K': K, 'p': '#e8d8b0', 'g': '#8a6a40', 'y': '#e8c040'})
icon('mg_kaiju', [  # two claws clashing, a spark between
    "................",
    "K..............K",
    "KK............KK",
    ".KK...y..y...KK.",
    "..KK...yy...KK..",
    "...KgK.yy.KrK...",
    "...KggKyyKrrK...",
    "....KggKKrrK....",
    "....KgggKrrK....",
    "...KggggKrrrK...",
    "..KgggKK.KrrrK..",
    ".KgggK....KrrrK.",
    ".KggK......KrrK.",
    ".KKK........KKK.",
    "................",
    "................"], {'K': K, 'g': '#5a8a4a', 'r': '#a84a30', 'y': '#f0e060'})
icon('mg_onelife', [  # one heart, a "1" on it
    "................",
    "..KKKK....KKKK..",
    ".KrrrrK..KrrrrK.",
    "KrrrrrrKKrrrrrrK",
    "KrrrrrwwwrrrrrrK",
    "KrrrrwwwwrrrrrrK",
    "KrrrrrrwwrrrrrrK",
    ".KrrrrrwwrrrrrK.",
    "..KrrrrwwrrrrK..",
    "...KrrwwwwrrK...",
    "....KrrrrrrK....",
    ".....KrrrrK.....",
    "......KrrK......",
    ".......KK.......",
    "................",
    "................"], {'K': K, 'r': '#c03030', 'w': '#f0f0f0'})
icon('mg_speedrun', [  # an hourglass with a running clock hand
    "..KKKKKKKKKKKK..",
    "..KwwwwwwwwwwK..",
    "...KyyyyyyyyK...",
    "...KyyyyyyyyK...",
    "....KyyyyyyK....",
    ".....KyyyyK.....",
    "......KyyK......",
    ".......KK.......",
    "......K..K......",
    ".....K.yy.K.....",
    "....K..yy..K....",
    "...K..yyyy..K...",
    "...KyyyyyyyyK...",
    "...KyyyyyyyyK...",
    "..KwwwwwwwwwwK..",
    "..KKKKKKKKKKKK.."], {'K': K, 'w': '#b08a50', 'y': '#e8c060'})


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
    out = os.path.join(TEX, 'gui', 'admin', 'icons_p10a.png')
    strip.save(out)
    print('wrote', out, ORDER)
    if '--preview' in sys.argv:
        d = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(d, exist_ok=True)
        strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(d, 'icons_p10a.png'))


main()
