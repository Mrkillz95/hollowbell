Hollowbell 1.9.3: his arms, strands and glowing pods holding together

lab_*.png     the pose lab (./gradlew poseLab -Pshots=1), 1.9.2 on top, 1.9.3 under it.
              Magenta = blocks cut off from the rest of him, red = blocks that touched and came apart.
              *_close.png are the arms and strands up close in their own colours.
ingame_*.png  the game itself (tools/autotest_tentacles.txt), 1.9.2 on the left, 1.9.3 on the right.

What the pose lab counted, over 344 poses (idle, drifting, turning, climbing, sinking, every move at
about ten moments, carrying someone up, asleep, lost his lift, dying):

                                         1.9.2    1.9.3
  blocks cut off, average per pose        1828      804
  pairs come apart by more than 1 block  11641     4705
  pairs come apart by more than 2 blocks  6627     1854
  sharpest bend lying down (degrees)       179       93
  standing still as built: cut off         1468       38

Without the poses where he lies on the ground (drop, lost lift, asleep, dying, sky dive):
  blocks cut off, average per pose        1794      451
  pairs come apart by more than 2 blocks  2806      866

What was wrong:
- Bits of strands, pods and egg clumps only joined to their own part through another part, so they
  floated off when the two swung apart. Each now goes with the part it touches.
- Many middle strands really hang from a pod (the glowing balls) or the end of an arm, but were hung
  from some other strand they touched by a block or two, so they and their pods came away. They now
  hang from the pod or arm, and turn about the spot where they touch it.
- Pods and egg clumps were stuck to one stiff piece of strand and swelled and swung about their top,
  so they came off it. They now hang where the strand is drawn at that spot, and swell about it.
  Six egg clumps that sit on arms hung from strands; they hang from the arm now.
- Arms and strands bent only at a few joints, opening gaps. Up close they are now drawn in slices
  laid along a smooth curve, and come smoothly out of the rim, the vase, a pod or an arm.
- When what a strand hangs from moved fast (an arm's end in the whirlpool) the strand grew longer and
  longer, up to ten times. Fixed.
- Lying down, strands folded back in a zig zag, and strand pieces pointing down were squashed into balls by
  the ground. They now lie out round him and slide along the ground at their full length, and gather
  back in slowly as he rises.
- Joints bent as far as anything pushed them: lying down, strands folded right back through themselves.
  Now each joint may only bend so far from the shape asked of it (eased back a little at a time, held
  outright where it lies on the ground).
