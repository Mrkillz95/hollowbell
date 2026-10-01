# Makes the giants' 64x64 pictures for the guide's list from the autotest shots (tools/autotest_portraits.txt):
#   python3 tools/portraits.py <screenshots dir>
# Furrowmaw dives under the ground in a flat test world, so his comes from his own mod's picture of him asleep.
import sys, os
from PIL import Image
here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
shots = sys.argv[1]
# centre x, centre y, square size, in the 1280x720 shot
BOX = {'cerberus': (625, 370, 620), 'hollowbell': (640, 415, 500), 'lanternwillow': (645, 385, 480), 'pitchgut': (665, 370, 380),
       'furrowmaw': (636, 300, 560)}
SRC = {'furrowmaw': '/home/user/furrowmaw/pictures/sleep/sleep_curled.png'}
for key, (cx, cy, size) in BOX.items():
    src = SRC.get(key, os.path.join(shots, f'portrait_{key}.png'))
    im = Image.open(src).convert('RGB')
    h = size // 2
    crop = im.crop((cx - h, cy - h, cx + h, cy + h)).resize((64, 64), Image.LANCZOS)
    crop.save(os.path.join(here, f'src/main/resources/assets/jj_giants/textures/gui/giant/{key}.png'))
    im.save(os.path.join(here, f'pictures/portraits/{key}_shot.png')) if key not in SRC else None
    print(key, src)
