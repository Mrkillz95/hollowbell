# The Mountain That Breathes — project handoff

Paste this into a new chat to pick the project up where it left off.
Last updated: 2026-09-23. Current version: **1.22.0**, built, tested and installed.

---

## What the project is

A Minecraft **Java Edition** mod, Fabric, for **Minecraft 1.21.1**. It turns JJ's
KillzAI voxel build of "The Mountain That Breathes" into a real, walking boss mob:
39 legs, 70 arms, 210 eyes, about 265 x 185 blocks at full size.

Mod id `mountain_breathes`, package `net.jj.mountain`.
Gradle: `minecraft_version=1.21.1`, `loader_version=0.19.5`,
`fabric_version=0.116.17+1.21.1`, `mod_version=1.22.0`.

## Where things are

- **Source**: `mountain-mod-source.zip` in `C:\Users\jeria\AppData\Roaming\.minecraft\`.
  A new chat gets a fresh cloud machine, so the old working copy is gone — pull that
  zip across with the device tools and unzip it (e.g. to `/tmp/mtn/mod`) before doing
  any code work. There is no git repo; the zip is the only copy.
- **Installed**: `mountain-that-breathes-1.22.0.jar` (3,436,641 bytes) in both
  `...\.minecraft\mods\` and `...\.minecraft\mountain-server\mods\`.
  `fabric-api-0.116.17+1.21.1.jar` and `fire-ice-cerberus-1.0.0.jar` sit beside it.
- **README.md** (about 70 KB, the player-facing manual) is in `...\.minecraft\mods\`
  and also at the root of `.minecraft` as "Mountain That Breathes - README.md".
- Old jars can't be deleted through the device bridge. The workaround used every time:
  overwrite each old jar with a tiny valid Fabric mod that has a unique id
  (`mountain_old_<ver>`, no entrypoints). That stub is `empty-1212.jar`, 749 bytes.
  Every old version in both folders has already been blanked this way.

## How to build and test

From the source root:

- Build: `gradle build --offline` (output in `build/libs/`)
- Gametests: `rm -rf build/gametest/world && gradle runGametest --offline`,
  results in `build/gametest-report.xml`. **77 tests, all passing as of 1.22.0.**
- The test names in that report are attributed wrong — trust the failure *message*,
  not the name next to it.
- Tests in one batch share a single world and run at the same time, so the one
  `MountainWorld` save leaks state between them. `EMPTY_STRUCTURE` is only about
  8 blocks across, so anything placed at (10,2,10) is outside it.
- `IN_TESTS = System.getProperty("fabric-api.gametest") != null` gates the book sweep,
  the mountain limit and offscreen travel.
- **Visual check harness**: `bash <scratchpad>/runauto.sh <logname>`, script at
  `mod/autotest.txt`, screenshots land in `build/autotest/screenshots/`. Commands:
  `world flat|normal`, `cmd`, `wait`, `shot`, `waitmodel`, `gui`,
  `view mx my mz tx ty tz`, `render N`, `book`, `page N`, `attack n`, `unmake`,
  `end`, `end2`, `quit`. A run takes 2.5–3 minutes, so run it in the background.

## Version history (what was asked for, what shipped)

- **1.20.3** — only one book in the world, done properly.
- **1.21.0** — one Mountain at a time; scars that never fully heal; at about 70% of
  legs broken he stays down for good but turns vicious; the four-leg knockdown lasts
  longer; he respawns 10 in-game days after an unmake, within 7,000 blocks of where
  the last one fell; the heart trophy block redesigned with a temporary ward.
- **1.21.1** — ward base 20 minutes, editable by command; ward range 700 blocks;
  unmake radius 1,000 blocks. (Superseded by 1.21.2.)
- **1.21.2** — a command summon overrides and deletes the current one; the world max
  is settable.
- **1.22.0** — the big update, three things:
  - **Long inhale (draw_in)**: he opens his mouth and drags everything in line of
    sight toward him, reach `180*scale+90`, and swallows what gets close.
  - **Tear a piece off (tear_off / SPLIT)**: he rips a small copy of himself free
    (scale `scale*0.22`, clamped 0.03–0.35, hunter mood, lives 20 minutes, drops
    nothing, doesn't count toward the world limit) and hurts himself 6% of max health
    doing it. Costs a lot of wind and raises grudge. **If a player holds the book,
    only that player can trigger it**; he can still do it himself when nobody holds it.
  - **The stare**: no more lasers. It stands your own **shadow** up out of black goo,
    dripping, carrying a copy of your held item and your health. It stays alive until
    you break his line of sight for a full second, and he keeps attacking while it's up.
  - Powerful moves cost more wind generally. Wind table is `ATTACK_WIND` in
    `CodexOrders.java`; eye storm 0.34, unmake 0.48, draw_in 0.55, tear_off 0.70.

## Key code, if you need to find something

- `entity/MountainEntity.java` (~3,800 lines) — the body, scars (`legsScarred`,
  `legsRuined`, `ruinCapLegs()`, `mendLeg` refusing ruined limbs), the crippled state
  (`crippleAt()` = 27 of 39 legs, `comeDownForGood()`, damage x1.35, purple boss bar),
  the ward (`warded`, `wardEscape`), `canSee(Entity)` (used by the shadow, samples
  every 23rd eye then the head), the one-at-a-time limit (`bornAt`, `takenPlaceOf()`),
  pieces (`isAPiece()`, `becomeAPieceOf`, `pieceTick`), and `someoneHasTheBook()`.
- `entity/MountainAttacks.java` — the move list, `choose()` weights, `gaze()`,
  `drawIn()`, `tearOff()`.
- `entity/ShadowOfYou.java` — the shadow mob. `client/render/ShadowRenderer.java` draws it.
- `world/MountainWorld.java` — the saved data: book holder, ward state, respawn timer,
  the mountain limit, `refreshHolder`, `prune`.
- `block/HeartTrophyBlock.java` — the heart, three states (dark / ready / warding).
- `command/MountainCommand.java` — `/mountain scars`, `/mountain ward`,
  `/mountain limit`, `/mountain unmake radius|wave`, `/mountain mendlimbs one`, etc.
- `world/Crater.java` — the unmake crater, sliding 56-chunk window so it never holds
  thousands of chunks at once, radius capped at 1,200.
- Config file in game: `config/mountain_breathes.json`, defaults in `MountainConfig.java`.

## Things that bit us before

- Crippled Mountain slid several blocks while meant to be immobile — the pivot point
  75*scale ahead of him. Fixed by zeroing `pz` while `downTicks > 0`.
- The lit heart texture came out green from interpolating hue across the red wrap.
  Scale RGB directly, no hue maths.
- `force()` on an attack used to require a target, which broke the self-split test.
  SPLIT and DRAW may now start with a null target.
- Discarding a torn-off piece used to start a world trip and clobber shared state.
  `parkForNow()` now returns early for pieces.
- The device bridge drops out fairly often. Retry once, then fall back to handing the
  file over in chat and tell JJ. `mv` into a newly created folder fails (delete is
  off); `device_commit_files` with `force: true` works.
- `curl` to api.mojang.com and api.github.com is blocked by the proxy (403). WebFetch works.

## Standing rules for this project

- JJ's Minecraft name is **MrKillz95**, UUID `dd307c79-ee40-4622-8943-a37378ff0d40`.
- Keep wording plain and simple. Short final messages. Don't write like an AI.
- **Do not password-lock the jar.** This was asked for once and declined: a jar can't
  be password-protected, and putting the password inside it would just hand it to
  anyone who has the file. Nothing was stored. JJ said "never mind on locking it then."
  It stays declined.

## Open question, not yet answered

While the book's owner is **offline**, `someoneHasTheBook()` still returns true, so he
will never use the tear-off move on his own. The question put to JJ: should an offline
holder's book keep blocking that move, or should "nobody online has it" count as nobody
having it? Recommendation was the latter, for that move only, and leave the safe list
alone (the list surviving a logout is what he asked for and works right). **No code
change until JJ answers.**
