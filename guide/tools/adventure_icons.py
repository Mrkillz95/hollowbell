# (Part 11) The admin book's pictures for the Adventure page, in the same pixel style as tools/minigame_icons.py, and
# the Adventure Wand's item texture. Writes src/main/resources/assets/jj_giants/textures/gui/admin/icons_p11.png and
# textures/item/adventure_wand.png; with --preview DIR also big copies to look at.
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


icon('adventure', [  # an unrolled map with a dotted trail to a red X
    "................",
    ".KKKKKKKKKKKKKK.",
    "KpppppppppppppbK",
    "Kpg.pppppppppbK.",
    "KpgggpppppppppK.",
    "Kppgg.y.pppppppK",
    "Kppppppy.ppppppK",
    "KpppppppypppppK.",
    "Kppppppppy.rpr.K",
    "KpppppppppypprpK",
    "Kppbbppppppprpr.",
    "KpbbbbpppppppppK",
    "Kppbbpppppppppbk",
    ".KpppppppppppbK.",
    "..KKKKKKKKKKKKK.",
    "................"], {'K': K, 'k': K, 'p': '#e8d4a0', 'g': '#5a9a40', 'b': '#4a7ab8', 'y': '#a06a20',
                         'r': '#c02020'})
icon('adv_wand', [  # a staff with a glowing gold star
    "...........y....",
    "..........yWy...",
    "........yyWWWyy.",
    ".........yWWWy..",
    "........yWy.yWy.",
    "........y.KK..y.",
    ".........KbK....",
    "........KbK.....",
    ".......KbK......",
    "......KbK.......",
    ".....KbK........",
    "....KgK.........",
    "...KbK..........",
    "..KbK...........",
    ".KbK............",
    ".KK............."], {'K': K, 'y': '#f0c040', 'W': '#fff4b0', 'b': '#6a4020', 'g': '#c89a30'})
icon('adv_script', [  # a scroll with lines of steps
    "..KKKKKKKKKKKK..",
    ".KwwwwwwwwwwwwK.",
    ".KKKKKKKKKKKKKK.",
    "..KppppppppppK..",
    "..KpKKpKKKKppK..",
    "..KppppppppppK..",
    "..KpKKpKKKKKpK..",
    "..KppppppppppK..",
    "..KpKKpKKKpppK..",
    "..KppppppppppK..",
    "..KpKKpKKKKppK..",
    "..KppppppppppK..",
    ".KKKKKKKKKKKKKK.",
    ".KwwwwwwwwwwwwK.",
    "..KKKKKKKKKKKK..",
    "................"], {'K': K, 'p': '#efe0b8', 'w': '#b08a50'})
icon('adv_hook', [  # a command block with a spark
    "................",
    ".KKKKKKKKKKKKK..",
    ".KooooooooooooK.",
    ".KoKKKKKKKKKKoK.",
    ".KoKddddddddKoK.",
    ".KoKdgdddddKoK..",
    ".KoKddgddddKoK..",
    ".KoKdgdgggdKoK..",
    ".KoKddddddKoK...",
    ".KoKKKKKKKKoK.y.",
    ".KooooooooooK.yy",
    ".KKKKKKKKKKKK.yW",
    "............yyWy",
    "...........yWy..",
    "............y...",
    "................"], {'K': K, 'o': '#c87830', 'd': '#3a3028', 'g': '#e8e0d0', 'y': '#f0c040', 'W': '#fff4b0'})
icon('adv_map', [  # a framed square of land: grass, a tree, water
    "KKKKKKKKKKKKKKKK",
    "KwwwwwwwwwwwwwwK",
    "KwgggggggggggbwK",
    "KwggTggggggbbbwK",
    "KwgTTTgggggbbbwK",
    "KwggtggggggggbwK",
    "KwggtgggssgggggK",
    "KwggggggssssgggK",
    "KwgggggsssgggggK",
    "KwgggggggggggggK",
    "KwdddddddddddddK",
    "KwdddddddddddddK",
    "KwsssssssssssssK",
    "KwwwwwwwwwwwwwwK",
    "KKKKKKKKKKKKKKKK",
    "................"], {'K': K, 'w': '#b08a50', 'g': '#6aa84a', 'T': '#2f6a2a', 't': '#6a4020', 'b': '#4a7ab8',
                         's': '#8a8a8a', 'd': '#8a5a30'})
icon('adv_path', [  # footprints along a dotted path to a flag
    ".........KKKK...",
    ".........KrrrK..",
    ".........KrrK...",
    ".........K......",
    ".......y.K......",
    "......y..K......",
    ".....y...K......",
    "....y.........  ".replace(' ', '.'),
    "...y............",
    "..y.............",
    ".y..............",
    "y...............",
    ".y..............",
    "..y.............",
    "...y............",
    "................"], {'K': K, 'r': '#c02020', 'y': '#a06a20'})
icon('adv_area', [  # a dashed blue box
    "................",
    ".bb.bb.bb.bb.bb.",
    ".b.............b",
    "................",
    ".b............b.",
    ".b............b.",
    "................",
    ".b....KK......b.",
    ".b...KggK.....b.",
    ".....KggK.......",
    ".b....KK......b.",
    ".b............b.",
    "................",
    ".b.............b",
    ".bb.bb.bb.bb.bb.",
    "................"], {'K': K, 'b': '#3a8ae0', 'g': '#e8c060'})
icon('adv_cove', [  # a wreck's mast and sail over waves
    "......K.........",
    "......Kr........",
    "......KKKKK.....",
    "......KwwwwK....",
    "......KwwwwwK...",
    "......KwwwwwK...",
    "......KwwwwK....",
    "......KKKKK.....",
    "..KKKKKKKKKKKK..",
    "..KbbbbbbbbbbK..",
    "...KbbbbbbbbK...",
    "aaaaKKKKKKKKaaaa",
    "aAAaaaAAaaaAAaaa",
    "aaaaaaaaaaaaaaaa",
    ".aAAaaaaAAaaaAa.",
    "................"], {'K': K, 'r': '#202020', 'w': '#efe6d0', 'b': '#7a4a24', 'a': '#3a7ad0', 'A': '#8ac4f0'})

WAND = [  # the item: a dark wood staff, gold bands, a glowing star tip
    "...........y....",
    "..........yWy...",
    "........yyWWWyy.",
    ".........yWWWy..",
    "........yWy.yWy.",
    "........y.KK..y.",
    ".........KgK....",
    "........KbK.....",
    ".......KbK......",
    "......KgK.......",
    ".....KbK........",
    "....KbK.........",
    "...KgK..........",
    "..KbK...........",
    ".KbK............",
    ".KK............."]
WAND_PAL = {'K': '#1a1008', 'y': '#f0c040', 'W': '#fff8c8', 'b': '#5a3418', 'g': '#d8a838'}


def draw(rows, pal):
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
        rows, pal = ICONS[n]
        strip.paste(draw(rows, pal), (16 * i, 0))
    out = os.path.join(TEX, 'gui', 'admin', 'icons_p11.png')
    strip.save(out)
    wand = draw(WAND, WAND_PAL)
    wout = os.path.join(TEX, 'item', 'adventure_wand.png')
    wand.save(wout)
    print('wrote', out, ORDER, wout)
    if '--preview' in sys.argv:
        d = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(d, exist_ok=True)
        strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(d, 'icons_p11.png'))
        wand.resize((128, 128), Image.NEAREST).save(os.path.join(d, 'adventure_wand.png'))


main()
