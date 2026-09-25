# Hollowbell — design draft

A new boss for Minecraft Java 1.21.1 with Fabric, in its own jar like Furrowmaw.
He's made from **JJ's KillzAI Hollowbell build** the same way the Mountain was: the
build is turned into a voxel model, block for block, so every voxel wears the real
Minecraft block texture from the build.

Status: **built, version 1.0.0** (see `release/`).

---

## The build

Where it is: the KillzAI behavior pack for Bedrock,
`...\Minecraft Bedrock\Users\Shared\games\com.mojang\behavior_packs\KillzAI\`.
`functions\killzai\hollowbell.mcfunction` loads 34 pieces from
`structures\killzai\hollowbell_*.mcstructure`.

- 656,901 blocks, 201 x 204 x 201.
- Pictures of it are in `reference\`. `read.js` in there reads the pieces and puts
  them together, and `render.js` draws the pictures.

### What it looks like, part by part

A big green jellyfish standing on its own hanging strands.

**1. The bell** (top 66 blocks, about 180 wide)
- A dome split into **about 24 ribs** of oxidized copper and oxidized cut copper,
  running from the crown down to the rim.
- The panels between the ribs are glass: clear, white, light grey and yellow stained
  glass, with some grey concrete panels. Lower down there's a ring of lime glass.
- **The crown** on top: a round cap of glowstone, yellow stained glass and ochre
  froglight.
- **Five glowing yellow spots** spread around the top of the dome, made of glowstone
  and yellow glass.
- Wavy **teal veins** of cyan glass run across the dome.
- **The dome is hollow**, and the glass lets you see into it.
- **The rim** is a thick band of weathered copper and bone block.

**2. The arms** (8 of them)
- Big, heavy, lobed arms of weathered and oxidized copper, speckled all over with
  bone and calcite dots.
- Each has **bright lime loops** on it: rings of lime glass around a pale middle.
- They come out from under the rim and curl down and out, almost to the ground.

**3. The strands** (about 60)
- Pale strands of bone block and calcite hanging from under the bell straight down to
  the ground, some of them grown together into sheets.
- Little bits of verdant froglight glow inside them.

**4. The pods**
- Grey hooded pods hanging among the strands: grey and light grey concrete, dark
  holes of black concrete like eyes, and a green mossy bottom.

**5. The egg clumps**
- Lumpy clusters stuck to the strands: grey concrete, honeycomb, dripstone and tinted
  glass, grey speckled with orange.

**6. The ground patch** (bottom 6 layers, about 200 wide)
- A flat patch of end stone and end bricks with moss, green terracotta, a calcite
  middle and winding grey trails.
- This is the ground he stands on, not part of his body. In the mod it isn't part of
  the mob. It could become the mark he leaves on the ground where he's been resting
  (see open questions).

### Biggest blocks

| Blocks | Count |
|---|---|
| bone block | 106,135 |
| oxidized copper | 82,049 |
| weathered copper | 75,272 |
| calcite | 66,678 |
| lime stained glass | 45,768 |
| oxidized cut copper | 32,992 |
| end stone (ground) | 30,303 |
| glass | 26,441 |

Full list in `reference\block_counts.txt`.

---

## How he moves

He's the build, brought to life. Like a jellyfish, with the Hanged Tide moves on top.

- **He drifts.** He floats slowly over the land. The strands hang under him and their
  ends drag along the ground, flattening plants.
- **The bell pulses.** Every few seconds the dome squeezes in and relaxes, and each
  pulse pushes him along. The ribs pull together and spread apart.
- **The arms swing** slowly with each pulse, like they're swimming.
- **The strands sway** and trail behind him when he moves, and swing back when he
  stops.
- **The pods and egg clumps** jiggle on the strands.
- **At night** the crown, the five spots and the froglight in the strands glow.

## What he does

**Strands**
- **Sting.** Touching a strand stings: poison and slowness.
- **Grab and lift.** A strand wraps around a player or mob and slowly pulls it up
  into the dome. Hit the strand enough and it lets go.
- **Harvest.** He does it to anything: cows, villagers, even trees get pulled up and
  hang inside the dome, and you can see them through the glass.
- **Curtain.** The strands close in around you like a cage and pull tight.
- **Sweep.** He swings and all the strands sweep a wide arc across the ground.

**Arms**
- **Arm slam.** One of the eight arms lifts and slaps down on the ground.
- **Arm wrap.** An arm curls around you and squeezes.

**Bell**
- **Pulse wave.** A hard pulse sends a shock wave out: knockback and nausea, and it
  knocks flying players out of the air.
- **Drop.** He stops floating and the whole bell comes down and slams the ground.
  Then he rises back up slowly. While he's down, the dome and crown are in reach.
- **Shed.** Egg clumps break off and hatch into small jellies (Bellings) that drift
  after you and sting.

**Inside the dome**
If he lifts you all the way in, you're inside the dome with whatever else he's
caught. You take damage over time, but the five glowing spots and the crown are right
above you. Hit them from inside for extra damage. Hurt him enough and he drops you.

## Fighting him

- **Weak spots:**
  - The **pods** among the strands can be reached from the ground. Each popped pod
    stays dark, like the Mountain's eyes.
  - The **five glowing spots** and the **crown** take the most damage.
- The copper and bone take very little.
- **Boss bars:** his health, and how many pods are left.
- Below half health the lime loops on his arms turn red and he pulses more often.
- **When he dies** he stops floating, the bell sinks down onto its strands, the
  strands buckle, and he folds down onto the ground. Then he sinks away.

## Moods and control (same as the others)

- Calm, hunting and guardian, a spawn egg for each, plus a small one.
- A **Hollowbell Codex** book to control him, laid out like the Mountain's and
  Furrowmaw's: orders, attacks, mood, safe list, and riding and being him.
- Riding: sitting on the crown.

## Commands (cheats on)

Same pattern as Furrowmaw: `/hollowbell summon [calm/hunting/guardian] [size]`,
`/hollowbell do <move>`, `/hollowbell list`, `/hollowbell mood`, `/hollowbell size`,
`/hollowbell hurt`, `/hollowbell heal`, `/hollowbell kill`, `/hollowbell remove`,
`/hollowbell popped <n>` for testing.

## What he drops (first ideas)

- **Stinger**: a whip-like weapon that poisons and slows.
- **Bell glass**: craft it into armor.
- **Pods**: for the codex and other recipes.
- A **crown** trophy block that glows.

---

## Setup (for the build)

- Mod id `hollowbell`, package `net.jj.hollowbell`, author JJ, first version 1.0.0.
- Same versions as the others: Minecraft 1.21.1, Fabric loader 0.19.5,
  Fabric API 0.116.17+1.21.1, Java 21.
- The model is converted from the build the Mountain's way: a `hollowbell_model.bin`
  plus a `hollowbell_rig.json` that says which voxels are the bell, which arm, which
  strand, which pod and which egg clump.
- Block names in the build are Bedrock names. They need mapping to Java names and
  textures, e.g. `concrete[color=silver]` → `light_gray_concrete`,
  `stained_hardened_clay` → terracotta.
- The jar goes in `.minecraft\mods` and `mountain-server\mods`.
- Plain, simple wording in the README and in game.

## Decisions (JJ, 2026-09-24)

1. **The ground patch is dropped.** Leave the bottom 6 layers (end stone, end bricks,
   moss, green terracotta, the calcite middle and the grey trails) out of the model.
   The strands end at the ground.
2. **Add the 128 threads going up**, and make them fit the build's style:
   - Built from the same blocks as the build: pale bone block and calcite strands
     like the hanging ones, with thin oxidized copper bands every so often and small
     verdant froglight bits glowing inside, like the strands.
   - They come out of the top of the dome **between the copper ribs and around the
     crown**, spread evenly, then rise straight up, getting thinner.
   - They fade out about 100 blocks above the crown, so they seem to go up into
     nothing, and the fade also keeps the frame rate up.
   - They sway gently and lean the way he's drifting, like he's hanging from them.
   - They can be hit and cut. Each cut thread makes him hang lower and tilt toward
     that side. Cut enough and he sinks right down and can't rise until they grow
     back. A third boss bar counts the threads still holding.
3. **See-through glass: yes.** The renderer has to draw the glass voxels see-through,
   with the right stained-glass colours, so you can see into the hollow dome and see
   whatever he's caught.
4. **Copy Pitchgut's code: yes.** Reuse Pitchgut's voxel renderer, codex book, safe
   list and ride/"being him" controls so Hollowbell matches the other two. The
   Pitchgut source is in `pitchgut-src\` for reference only. Copy what's needed into
   `net.jj.hollowbell` and never change `pitchgut-src\` itself.

## Guesses made while building

Things the design didn't say, and what was picked. Change any of them if they're wrong.

- **The ground patch** is cut off below the strands: every end stone, end brick, moss, green terracotta and
  grey trail block goes, and the calcite and bone of the ground is kept only straight under a strand (so each
  strand carries on down to the ground). Arm tips that touch the ground are kept.
- **The five glowing spots.** The build has four big yellow glass balls inside the dome, one under each yellow
  patch you see from above, plus a tall glowing vase of glowstone hanging under the crown in the middle. The
  four balls (with the patch over each) are spots 1 to 4, and the vase is spot 5.
- **The middle of the strands.** Under the vase there's a branching pale stalk with the pods hanging on it.
  It's treated as strands, and it hangs from the vase.
- **Strands:** the build splits into 80 strands (a few of them are the joined-up sheets), not 60.
- **Pods** are the hollow glass balls with black and tinted glass eyes in the middle (23 of them). The grey
  lumps of concrete, honeycomb and dripstone are the egg clumps (90 of them).
- **The 128 threads:** 32 in a ring round the crown and 96 between the 24 copper ribs (4 rings of 24). Thick
  (a plus shape) at the bottom, then 2 by 2, then 1 wide. Bone and calcite, an oxidized copper band every 13
  to 16 blocks, a few verdant froglight bits. Full strength up to 45 blocks above the crown, then fading out
  to nothing at 100 above the crown.
- **Hitting him** works on the real blocks: a swing or an arrow lands on the exact block you aimed at, and
  that block's part decides the damage: crown x3 (x4 from inside), spots x2.5 (x3.5 from inside), pods x1,
  egg clumps x0.3, threads x0.2, all the copper and bone x0.1. On the ground (dropped or sunk) everything
  counts 1.3 times.
- **Pods** each take a share of his health when they pop (1.5% of his max) and drop a pod item.
- **Threads** take about 3 good hits to cut. They start growing back after 45 seconds and take 20 more to
  grow. Cut 6 in 10 and he sinks; he rises again once half of them hold.
- **Grabs** let go after 4 hits on the strand (or arm, for the wrap). A pull up takes about 10 seconds.
- **Inside the dome** you hang in one of 14 places: the first four are right under the glowing balls, so
  a spot is in reach over your head. Damage over time is 2 every 2 seconds plus poison. Hurting him by 4% of
  his health from inside makes him let every player go. Too small (under 0.07 size) and there's no inside.
- **Trees** he harvests hang in the dome as block displays for 3 minutes, then drop as logs.
- **The crown when he's down.** Even flat on the ground the crown is still high up (about 80 blocks at full
  size), so the dome and rim are what's in reach; the crown and spots can be shot with arrows. There's no
  walking on the dome.
- **Riding:** "Sit on his crown" in the book makes him come over; a strand lifts you onto the crown and you
  drive him, like the Mountain's "being him" (keys 1-9 for his 9 moves, G to get off).
- **The book:** the nearest Hollowbell within 600 blocks listens to whoever carries it (anywhere in the
  pack). There's no "only one book" rule like Pitchgut's; it works like Furrowmaw's.
- **Drops:** the Stinger (netherite-strength sword that poisons and slows), 8-16 bell glass, 3-6 pods, the
  crown block, plus copper, bone and froglight. Bell glass armor is like diamond with more toughness. A pod
  in a glass bottle makes a long poison potion.
- **Bellings** live 3 minutes, have 8 health and sting for 2 plus poison. Shed egg clumps grow back after
  5 minutes.
- **Advancements:** "Rung Out" for killing one, "Under Glass" for being pulled into the dome.
