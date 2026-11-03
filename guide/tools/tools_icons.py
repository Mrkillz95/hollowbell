# (Part 6) The admin book's Server tools pictures: 16x16 each, in the same pixel style and palette as the section
# icons (tools/admin_textures.py), written to src/main/resources/assets/jj_giants/textures/gui/admin/tools_icons.png.
# The order is ToolsArt.NAMES in the client code.
#   python tools/tools_icons.py [--preview DIR]
import os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from admin_textures import hexc, save, K  # noqa: E402

ICONS = {}


def icon(name, rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    ICONS[name] = (rows, pal)


icon('tools', [  # a hammer crossed with a wrench
    "................",
    ".KKK.......KKK..",
    "KgggK.....KiiK..",
    "KgggK....KiKKiK.",
    ".KgggK...KiK.KK.",
    "..KggK..KiiK....",
    "...KKbK.KiK.....",
    ".....KbKiK......",
    "......KbK.......",
    ".....KiKbK......",
    "....KiK.KbK.....",
    "...KiK...KbK....",
    "..KiK.....KbK...",
    ".KiiK......KbK..",
    ".KKK........KK..",
    "................"], {'K': K, 'g': '#9a9aa6', 'i': '#c8c8d0', 'b': '#8a5a30'})
icon('t_now', [  # pause bars in a red seal
    "................",
    ".....KKKKKK.....",
    "...KKrrrrrrKK...",
    "..KrrrrrrrrrrK..",
    "..KrrwwrrwwrrK..",
    ".KrrrwwrrwwrrrK.",
    ".KrrrwwrrwwrrrK.",
    ".KrrrwwrrwwrrrK.",
    ".KrrrwwrrwwrrrK.",
    ".KrrrwwrrwwrrrK.",
    "..KrrwwrrwwrrK..",
    "..KrrrrrrrrrrK..",
    "...KKrrrrrrKK...",
    ".....KKKKKK.....",
    "................",
    "................"], {'K': K, 'r': '#b83020', 'w': '#f4eee0'})
icon('t_schedules', [  # a calendar page
    "................",
    "...K..K..K..K...",
    "..KKKKKKKKKKKK..",
    "..KrrrrrrrrrrK..",
    "..KrrrrrrrrrrK..",
    "..KKKKKKKKKKKK..",
    "..KpppppppppppK.",
    "..KpKpKpKpKppK..",
    "..KppppppppppK..",
    "..KpKpKpKgKppK..",
    "..KppppppppppK..",
    "..KpKpKpKpKppK..",
    "..KppppppppppK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................"], {'K': K, 'r': '#b04030', 'p': '#f0e4c4', 'g': '#e8c048'})
icon('t_areas', [  # a fenced ring
    "................",
    "......KKKK......",
    "....KKggggKK....",
    "...KggggggggK...",
    "..KggKKKKKKggK..",
    "..KgK......KgK..",
    ".KgK..rrrr..KgK.",
    ".KgK.rRRRRr.KgK.",
    ".KgK.rRRRRr.KgK.",
    ".KgK..rrrr..KgK.",
    "..KgK......KgK..",
    "..KggKKKKKKggK..",
    "...KggggggggK...",
    "....KKggggKK....",
    "......KKKK......",
    "................"], {'K': K, 'g': '#8a6a40', 'r': '#c03020', 'R': '#e86040'})
icon('t_map', [  # a folded map with a red cross
    "................",
    "................",
    ".KKKKKKKKKKKKKK.",
    ".KpppKqqqKpppK..",
    ".KpgpKqqqKpppK..",
    ".KpggKqbqKpprK..",
    ".KpgpKqbbKprprK.",
    ".KpppKqbqKpprK..",
    ".KppgKqqqKpppK..",
    ".KpggKqqqKpgpK..",
    ".KpppKqqbKpppK..",
    ".KpppKqqqKpppK..",
    ".KKKKKKKKKKKKKK.",
    "................",
    "................",
    "................"], {'K': K, 'p': '#ecdcb4', 'q': '#d4c094', 'g': '#5aa03a', 'b': '#3a7ad0', 'r': '#d0203a'})
icon('t_stats', [  # a bar chart
    "................",
    "................",
    "...........KKK..",
    "...........KgK..",
    ".......KKK.KgK..",
    ".......KbK.KgK..",
    ".......KbK.KgK..",
    "...KKK.KbK.KgK..",
    "...KrK.KbK.KgK..",
    "...KrK.KbK.KgK..",
    "...KrK.KbK.KgK..",
    "...KrK.KbK.KgK..",
    "..KKKKKKKKKKKKK.",
    "................",
    "................",
    "................"], {'K': K, 'r': '#d04040', 'b': '#3a7ad0', 'g': '#5aa03a'})
icon('t_alerts', [  # a bell with a red mark
    "................",
    ".......KK.......",
    "......KyyK......",
    ".....KyyyyK.....",
    "....KyyyyyyK....",
    "....KyyyyyyK....",
    "....KyyyyyyK....",
    "...KyyyyyyyyK...",
    "...KyyyyyyyyK...",
    "..KyyyyyyyyyyK..",
    "..KKKKKKKKKKKK..",
    ".......KK.......",
    "......KyyK......",
    ".......KK.......",
    "................",
    "................"], {'K': K, 'y': '#e8c048'})
icon('t_respawns', [  # an hourglass
    "................",
    "...KKKKKKKKKK...",
    "...KbbbbbbbbK...",
    "....KssssssK....",
    "....KssssssK....",
    ".....KssssK.....",
    "......KssK......",
    ".......KK.......",
    "......K..K......",
    ".....K.ss.K.....",
    "....K.ssss.K....",
    "....KssssssK....",
    "...KbbbbbbbbK...",
    "...KKKKKKKKKK...",
    "................",
    "................"], {'K': K, 'b': '#8a5a30', 's': '#e8c870'})
icon('t_server', [  # a sealed letter (the world's profile file)
    "................",
    "................",
    ".KKKKKKKKKKKKKK.",
    ".KpKppppppppKpK.",
    ".KppKppppppKppK.",
    ".KpppKppppKpppK.",
    ".KppppKKKKppppK.",
    ".KpppppKKpppppK.",
    ".KppppKrrKppppK.",
    ".KpppKrRRrKpppK.",
    ".KppppKrrKppppK.",
    ".KpppppKKpppppK.",
    ".KKKKKKKKKKKKKK.",
    "................",
    "................",
    "................"], {'K': K, 'p': '#f0e4c4', 'r': '#b02030', 'R': '#e05060'})
icon('t_events', [  # a war horn
    "................",
    "................",
    "............KK..",
    "..........KKyK..",
    "........KKyyyK..",
    "......KKyyyyyK..",
    "..KKKKyyyyyyyK..",
    ".KbbbKyyyyyyyK..",
    ".KbbbKyyyyyyyK..",
    "..KKKKyyyyyyyK..",
    "......KKyyyyyK..",
    "........KKyyyK..",
    "..........KKyK..",
    "............KK..",
    "................",
    "................"], {'K': K, 'y': '#d8b060', 'b': '#8a5a30'})

ORDER = ['tools', 't_now', 't_schedules', 't_areas', 't_map', 't_stats', 't_alerts', 't_respawns', 't_server', 't_events']


def strip():
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
    save(strip(), 'gui/admin/tools_icons.png', preview, 6)
    print('written: gui/admin/tools_icons.png')
