# JJ's Giants: all seven in one jar

One jar with all seven giants inside: Pitchgut, Furrowmaw, the Fire & Ice Cerberus, the Hollowbell, the Lantern
Willow, Wreckback and the Swarmforge, and the **Giants Guide**, a book about all of them. It works the same as having
the seven jars.
Each giant keeps its own name inside, so your worlds keep working.

Minecraft Java 1.21.1, Fabric Loader 0.19.5, Fabric API. By JJ. All rights reserved.

## What's inside

| Giant | Version |
|---|---|
| Giants Guide | 1.4.4 |
| Pitchgut | 1.32.6 |
| Furrowmaw | 1.14.6 |
| Fire & Ice Cerberus | 2.14.7 |
| Hollowbell | 1.9.6 |
| Lantern Willow | 1.7.7 |
| Wreckback | 1.0.3 |
| The Swarmforge | 1.1.2 |

## Install

Easy way: run `install-all.ps1` in the Hollowbell folder. It takes the seven single jars out of your mods folder
and puts this one in.

By hand: take `pitchgut-…`, `furrowmaw-…`, `fire-ice-cerberus-…`, `hollowbell-…`, `lanternwillow-…`,
`wreckback-…` and `swarmforge-…` out of your mods folder, and the old `giants-all-…` jar too, then put
`giants-all-1.4.4.jar` in. Keep Fabric API in there.
For a server, put the same jar in the server's mods folder.

If a single jar is left in by mistake the game still starts, but take it out anyway so the right version runs.

To get new versions on their own, run `watch-all.sh` in the Hollowbell folder. It waits for a new all-in-one jar
and installs it.

## The Giants Guide

A book about every giant you have. Right-click it to open it.

- **Getting one:** everybody gets one the first time they join a world. Craft another from a book and a compass
  (any shape). `/giantsguide` gives you one, no cheats needed.
- **On the left:** every giant that's installed, with a picture of him. Click one.
- **On the right**, a tab for each part:

| Tab | What's on it |
|---|---|
| About | what he is, how big, how he fights (juggernaut, ambusher, skirmisher, aerial, fortress, warship or factory), his moods |
| Where | his ground and what it looks like, how to find him (the finder and its recipe), whether the world keeps one of him out there and how many at once. On a server it also says where he is right now. |
| Moves | every move, light, medium and heavy (and Wreckback's sea moves), with what it does (the same words as his own book) |
| Army | the Swarmforge only: every robot and unit he sends out, and the army orders in his book |
| Fighting | where to hit him, what hurts him most, and tips |
| Drops | his weapon, his armour and its Armour power (R), his ward, the rest of his loot and his book, with pictures of the real items and their recipes |
| Commands | the commands anyone can use, and the ones that need cheats |

- **Giants together** (the last one on the left): who fights whom and who keeps away when they meet, and every
  `/giants` command.
- Scroll with the mouse wheel, the arrow keys or the bar on the right. Hold the mouse over an item to see its name.
- It only shows the giants you have, so it works with any of them on their own too.
- The "right now" part needs the guide on the server as well (it is, if the server has this jar). Otherwise it
  tells you to use `/giants where`.
- Setting: `giveOnFirstJoin` in `config/jj_giants.json` (on). Turn it off and nobody gets one on joining.

## New in 1.4.4

- **The Swarmforge 1.1.2: Commander Mode.** Leave your body (it stays safe) and command his army from a flying
  camera, up to 750 blocks from him: select robots with the mouse, give them orders, build more, or take control of
  one robot and fight as it. From his book's Army tab, P in his cab, or `/swarmforge commander`. The Guide's
  Swarmforge pages say how.
- `/giants tp swarmforge` puts you on safe ground beside him (before, it could put you on his roof, a long way to
  fall if he drove off, or under him at his Scrapyard).

## New in 1.4.3

- **`/giants tp swarmforge` always finds him**, even waiting unloaded in his Scrapyard or out of the world, and says
  which. `/giants where` and `list` show the giants that lie still where nobody is near (Pitchgut, Furrowmaw,
  Hollowbell, the Lantern Willow, Wreckback and the Swarmforge), and their `tp` lands you beside them.
- **The Swarmforge 1.0.3**: robots never stuck in his bays, robots that really go for you and land their blows, army
  cap 75, every robot type can be brought out, stow and release his army as data up a ramp under his tail, and a
  claw that sets you down on the ground from his cab. The Guide's Swarmforge pages say so.

## New in 1.4.2

- **`/giants where` and the finder give the Cerberus's real spot** (Cerberus 2.14.7). Before, just after he was
  summoned or loaded, or while he was far off, they said he was at 0 0 0.

## New in 1.4.1

- **The Cerberus's mane is sleeker** (Cerberus 2.14.6): shorter tufts that lie back along his necks instead of
  shaggy strands hanging down, and it no longer clips through itself, his necks or the other heads' manes when he
  moves. The pelt armour's mane is a bit sleeker too.

## New in 1.4.0

- **The Swarmforge is in**: JJ's seventh giant, a huge rusted war factory on four tank treads that keeps sending out
  robots. He has his own README (`Swarmforge README.md` in the Swarmforge folder) and his own pages in the Giants
  Guide, with an Army tab for his robots and units.
- The other six know him: Pitchgut, Furrowmaw, the Cerberus and the Lantern Willow fight him when they meet; the
  Hollowbell and Wreckback keep away from him. `/giants` reaches him too, and his robots' blows land on the other
  giants like a giant's helpers'.
- Every giant is at its newest version (see the table).

## New in 1.3.2

- **All six giants' big moves look and feel bigger.** Chunks of the real ground fly up when they slam down, dust
  rolls out, shockwaves run along the ground with cracks behind them, and on water there are splashes and real
  waves. Wreckback's tidal wave is a wall of water you can see rolling at you, his whirlpool and maelstrom spin the
  sea, and his cannons flash and smoke. The screen shakes near the big hits, the boom comes late from far off, and
  they move heavier round their slams. Each giant has a new `bigEffects` setting (0 = none, 1 = normal, 2 = more).
  See each giant's own README.

## New in 1.3.1

- **Wreckback 1.0.0**, the full version: `/giants` answers for him everywhere, his chains look like the game's own
  chain made big and hang under their weight, his rope ladders are short and stiff (a rope's end hangs down to climb
  on from the ground), his ship rides calmer while he walks, and you can see him on the skyline from far off again.
  A Drowned Crewman spawn egg and a "Below Decks" advancement too. See his own README.
- **`/giants paint` goes 5 blocks deep** for every giant, not just the top block. Add a number after `full` to go
  deeper or shallower, 1 to 64: `/giants paint wreckback 64 full 10`. Caves under it stay open, and it never goes
  through bedrock.
- **Hollowbell 1.9.3**: his arms, strands and glowing pods hold together now.
- The Giants Guide has the new `/giants paint` depth and Wreckback's new bits.
- Every giant is at its newest version (see the table).

## New in 1.3.0

- **Wreckback is in**: JJ's sixth giant, a hermit crab the size of a ship with a wrecked galleon on his back. He has
  his own README (`Wreckback README.md` in the Wreckback folder) and his own pages in the Giants Guide.
- The other five know him: Pitchgut, Furrowmaw and the Cerberus fight him when they meet; the Hollowbell and the
  Lantern Willow keep away from him. `/giants` reaches him too.
- Every giant is at its newest version (see the table).

## New in 1.2.0

- **The Giants Guide** (see above).
- The five giants are the same versions as in 1.1.0.

## New in 1.1.0

- **Each giant's ground is made in full.** No more holes where a witch hut, village or other building was, no
  trees left standing in it, and no lakes, rivers or odd stone patches left over. Buildings simply don't get made
  inside a giant's ground any more. Checked on real worlds: at most a few hundred columns out of 100,000+ are off,
  nearly all right at the edge or a few blocks higher or lower than planned.
- **`/giants paint` paints over everything**: buildings, trees, water, all of it, down to bedrock. What was in chests
  is dropped on the new ground, and a painted-over witch hut or village stops being one.
- **`/<giant> ground check`** (operators) makes a map of how well his ground came out.
- **Giants that keep away from each other keep away every time**, and don't start fights by brushing past each
  other.
- Worlds you already have: land made with older versions keeps its gaps. Use `/giants paint` over it.

## New in 1.0.0

- **Giants meet.** When two giants wander near each other they fight or keep away. Fights end when one backs
  down and runs off. You get a chat line when a fight starts and ends. `/giants meetings on|off`, and
  `/giants meet` to make the two nearest fight now.
- **Music.** Each giant has its own fight music, and his ground has its own calm music and sounds. Turn the fight
  music off with the `fightMusic` setting.
- **Orders last through a restart.** Come to me, go there, stay and trips carry on after the server restarts.
- **Six new advancements each** for his armour, his weapon, his loot and watching two giants fight.
- **Loot beams show from far off**, up to 1024 blocks, even with a short render distance.
- **Pitchgut:** the Goo Maw is 3D, his ground fades into the land at the edge, a brighter shrine beam.
- **Furrowmaw:** far orders cost wind, the blade stays put in the Burrow dash, the dive circles under the spot
  when he's told to stay, and more moves were checked.
- **Cerberus:** a real shaggy mane on all three necks that moves with the heads.
- **Hollowbell:** the egg rain is big and easy to see: a wind-up, big eggs, rings where they land, and bellings
  hatching.
- **Lantern Willow:** his strands don't fling out flat after a hitch any more.
- Lots of bug fixes. Each giant's own README has the full list.
