# Hollowbell — instructions for Claude

You're building **Hollowbell**, a boss mob mod for Minecraft **Java 1.21.1 / Fabric**,
for JJ. It's the third one: the first two are Pitchgut (was "The Mountain That
Breathes") and Furrowmaw. Read `DESIGN.md` before anything else. It's the spec, and
the "Decisions" section at the bottom is final.

## The job

Build the whole mod from `DESIGN.md`, test it, and leave a finished, playable jar in
`release/`. JJ wants it playable the moment it's done.

Work in this order, committing and pushing after each step so progress is never lost:

1. **Project skeleton**: a Fabric mod, mod id `hollowbell`, package `net.jj.hollowbell`,
   `archives_base_name=hollowbell`, `mod_version=1.0.0`. Same versions as Pitchgut:
   `minecraft_version=1.21.1`, `loader_version=0.19.5`,
   `fabric_version=0.116.17+1.21.1`, Java 21. Add a **Gradle wrapper** (`gradlew`),
   because Pitchgut never had one. Get an empty mod building first.
2. **The model converter**: read `build-source/` (34 Bedrock `.mcstructure` pieces,
   placed by the offsets in `hollowbell.mcfunction`) into one voxel model.
   `reference/read.js` already does the reading and joining, so use it as the guide.
   Then:
   - **Drop the ground patch**: the bottom 6 layers (y 0–5: end stone, end bricks,
     moss, green terracotta, the calcite middle and the grey trails). Keep the strand
     ends.
   - **Map Bedrock names to Java names**, e.g. `concrete[color=silver]` →
     `light_gray_concrete`, `stained_glass[color=lime]` → `lime_stained_glass`,
     `stained_hardened_clay[color=X]` → `X_terracotta`. Fail loudly on any name you
     can't map; never silently turn one into stone.
   - **Rig it**: sort every voxel into a part (bell, rim, crown, the 5 glowing spots,
     each of the 8 arms, each of the ~60 strands, each pod, each egg clump). Save it
     as `hollowbell_model.bin` + `hollowbell_rig.json` like the Mountain's.
   - Keep the converter in the repo (e.g. `tools/`) so it can be run again.
3. **Rendering**: copy Pitchgut's voxel renderer (`pitchgut-src/src/client/.../render/
   VoxelModel*`, `MountainRenderer`) into `net.jj.hollowbell` and adapt it. Every voxel
   uses its **real block texture**. **Glass must draw see-through** with its stained
   colour (a translucent pass, sorted), so you can see into the hollow dome.
4. **The 128 threads going up**: see Decisions #2 in `DESIGN.md`. Same style as the
   build (bone block and calcite, copper bands, froglight bits), coming out between
   the ribs and around the crown, fading out about 100 blocks above the crown.
5. **The entity**: drifting, the bell pulse, arm swing, strand sway and drag, thread
   sway. Hitboxes for the pods, the spots, the crown and the threads. Health, and
   boss bars for health, pods and threads.
6. **Moves**: everything under "What he does" in `DESIGN.md`, plus the inside of the
   dome.
7. **Moods, codex, safe list, riding and being him**: copy these from Pitchgut so they
   match.
8. **Commands, config, drops, spawn eggs, advancement**, the Furrowmaw pattern.
9. **Gametests** for the important things (summon, each move starts and ends,
   grab and let go, threads cut and grow back, pods pop, death, save and reload).
   Everything must pass.
10. **Player README**: `release/Hollowbell README.md`, written like
    `reference/Pitchgut README.md` and `reference/Furrowmaw README.md`: plain, short
    sentences, tables for moves and commands.
11. **Release**: put the finished jar at `release/hollowbell-1.0.0.jar`, commit and
    push. That's the signal: JJ's PC watches `release/` and installs the jar the
    moment it shows up. **Only put a jar in `release/` once it builds and every test
    passes.** Later fixes get a new version number (1.0.1, …) and a new jar there;
    delete the old jar from `release/` in the same commit.

## Pitchgut source (reference only)

`pitchgut-src/` is Pitchgut 1.22.1's source, and `pitchgut-src/PITCHGUT-HANDOFF.md`
explains it: the renderer, the codex, the safe list, how the gametests work, and the
bugs they hit before (read "Things that bit us before"). **Copy from it, never edit
it**, and don't build it. The device-bridge notes in the handoff don't apply here.

## Things to know

- There's no game window in the cloud. You can build and run gametests
  (`./gradlew runGametest`), but you can't see him. So be careful with the model:
  write a small tool that draws the converted model to PNG (front, side, top) and
  compare it with the pictures in `reference/`. Commit those PNGs to
  `reference/converted/` so JJ can check them.
- If the Fabric maven or Gradle downloads are blocked, say so clearly in your final
  message. Don't fake a build.
- Size: at scale 1.0 he's about 200 wide and 200 tall, plus the threads. Sizes from
  0.03 to 2 should work, like the others.
- Keep the mod light enough to play: far away, draw him simpler (Furrowmaw's
  `simpleFarAway` idea) and fade the threads.

## JJ's rules

- JJ's Minecraft name is **MrKillz95**.
- Plain, simple wording everywhere: README, book, chat messages, your final message.
  Don't write like an AI. Keep final messages short.
- License "All rights reserved", author "JJ", like the others.
- **Never password-lock the jar.** It can't be done properly, and it was already
  declined for Pitchgut.
