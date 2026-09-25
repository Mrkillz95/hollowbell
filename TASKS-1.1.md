# Hollowbell 1.1 — what JJ asked for

JJ played 1.0.0 and wants these changes. Do all of them, then release
`hollowbell-1.1.0.jar` the usual way (CLAUDE.md step 11: build, every gametest
passing, jar and README in `release/`, delete the 1.0.0 jar from `release/` in the same
commit, push). JJ said: "do anything you think will make him improved, no need to ask."
Write down any calls you make under a new "Guesses made for 1.1" heading in
DESIGN.md.

Keep committing and pushing after each item so nothing is lost.

## 1. Remove the threads on top

Take out all 128 threads that go up from the dome, with their rendering, hitboxes,
boss bar, `cutthreads`/`mendthreads` commands, book lines and README parts.

Cutting threads was how you brought him down, so replace that with something that
comes from the build. Suggestion: pop enough **pods** (say a third of them) and he
loses his lift and sinks to the ground for a while, dome in reach. They grow back
slowly and he rises again. The boss bar that counted threads now counts pods.

## 2. Real flying, and swimming like a jellyfish

- He flies properly in 3D. He picks a height, climbs and sinks, flies over hills
  and into valleys, follows a target up into the air (players on elytra, on
  pillars, flying mobs), and comes down to grab things. No more stuck at one height
  or gliding flat.
- **Going up, he swims like a jellyfish:** the bell squeezes in hard (narrower and
  taller, rim pulled in), he shoots up on that push, then the bell opens back out
  and he drifts up slower until the next push. The rhythm speeds up the harder he
  climbs.
- **While he climbs**, the arms and strands stream down under him in a tight bundle.
  **Sinking**, the bell opens wide like a parachute and the arms and strands float
  up and spread out. **Drifting level**, slow, gentle pulses.
- Turning is smooth and heavy, with the bell leaning into the direction he's going.
- He never clips into the ground or through terrain, and keeps his strand ends near
  the ground when he's grabbing or harvesting.

## 3. As many moves as the Mountain, at different strengths

The Mountain has 16 attacks. Give Hollowbell **at least 16**, keeping the 9 he has.
Sort them into **light, medium and heavy**, the way the Mountain's powerful moves
cost more "wind" (see `ATTACK_WIND` in `pitchgut-src/.../CodexOrders.java`):
- **Light:** quick and cheap, short cooldown, small damage, used often.
- **Medium:** a clear warning, then a real hit.
- **Heavy:** rare, a long wind-up you can see and hear coming, big damage and big
  area. He's worn out and slower for a few seconds afterwards.

Every move needs a clear tell (animation, sound, glow) so you can dodge it. It has to
work from the book, from `/hollowbell do <move>`, and in the "being him" keys. Some
ideas (use, change or replace them):
- **Sting volley** (light): the strand ends flick stingers at you.
- **Strand lash** (light): one strand whips sideways.
- **Glow flash** (light/medium): every glowing part flares at once, blindness,
  worse at night.
- **Spore cloud** (medium): egg clumps burst into a drifting poison cloud.
- **Pod burst** (medium): pods spray poison goo down.
- **Egg rain** (medium): egg clumps drop and hatch into Bellings when they land.
- **Whirlpool** (heavy): he spins and the strands pull everything in a wide ring
  toward the middle under him.
- **Sky dive** (heavy): he climbs very high, then dives bell-first and slams down
  with a huge shockwave.
- **Deep toll** (heavy): a long, building toll that ends in a massive shock ring
  that knocks things far and breaks glass and leaves.

## 4. A detail on/off command

`/hollowbell detail on` and `/hollowbell detail off` (and `/hollowbell detail` on its
own says which it is). It's for JJ's PC, so it's a **client** setting saved in the
config.
- **Off:** a simpler, lighter model (merged blocks, no small bits), fewer particles,
  no light glow effects, fewer strand segments, and the far-away simple version
  kicks in closer. He should still look like himself.
- **On:** full detail, as now.

## 5. A little less cluttered

He should still clearly be the KillzAI build, just cleaner:
- Fewer egg clumps (about a third of the 90), kept in clear groups, not scattered.
- Fewer of the single stray blocks and speckles on the arms and strands. Keep the
  lime loops and the pattern, but drop maybe half of the random dots.
- Clean up floating bits that aren't attached to anything.
- Keep all 8 arms, the dome, the crown, the spots and the pods.

Put before/after pictures in `reference/converted/` (e.g. `v11_before.png` /
`v11_after.png`).

## 6. Sounds

He needs his own sounds. Vanilla sounds are fine as a base, pitched and layered
(e.g. bell, conduit, amethyst, elder guardian, warden heartbeat, ambient cave,
water). Or make new `.ogg` files with code (the environment has full internet, so
ffmpeg or oggenc can be installed). **Don't copy sounds from other games or mods.**
At least:
- a low ambient hum and wet drifting sound
- the pulse whoosh each time the bell squeezes
- a deep bell toll (for his toll moves)
- strands sliding, the grab, the sting
- hurt, heavy hurt, and death
- pod pop, egg clump burst, Bellings chirping
- muffled heartbeat and echo while you're inside the dome
- a warning sound for each heavy move

Add a `sounds.json` with subtitles, and scale the volume and pitch with his size.

## 7. Better animation

- Strands and arms move with weight: they lag behind, swing, overshoot and settle,
  like chains in water (verlet or spring chains), not stiff rotations.
- The bell squashes and stretches with each pulse; the rim ripples.
- Pods bob, egg clumps wobble, the glowing parts pulse slowly.
- Every move eases in and out of his resting pose. Nothing snaps. (Furrowmaw's 1.1
  "no more snapping" notes in `reference/Furrowmaw README.md` are a good guide.)
- A nice idle: slow breathing pulse, arms curling a little, strands swaying.
- A proper death: the glow goes out, the bell folds, he sinks and settles.

## 8. Anything else that makes him better

Your call. Examples: performance at full size, smoother network sync, a better
spawn (he rises up out of the ground or comes down from the clouds), more lines
in the book, better drops. Put it all in the README's "What's new in 1.1".

## Checking your work

- Gametests for the new things (flying up and down, every new move starts and ends,
  pods bring him down, detail on/off, sounds registered).
- In-game pictures (the autotest harness) of: climbing mid-pulse, sinking with the
  bell open, a few heavy moves, detail on vs off. Commit them to
  `reference/converted/`.
- README updated: "What's new in 1.1" at the top, moves table with light / medium /
  heavy, new commands.
