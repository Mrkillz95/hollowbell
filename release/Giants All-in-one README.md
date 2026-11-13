# JJ's Giants: all nine in one jar

One jar with all nine giants inside: Pitchgut, Furrowmaw, the Fire & Ice Cerberus, the Hollowbell, the Lantern
Willow, Wreckback, the Swarmforge, the Gloamwick and the Hearthstride, and the **Giants Guide**, a book about all of
them. It works the same as having the nine jars.
Each giant keeps its own name inside, so your worlds keep working.

Minecraft Java 1.21.1, Fabric Loader 0.19.5, Fabric API. By JJ. All rights reserved.

## What's inside

| Giant | Version |
|---|---|
| Giants Guide | 1.16.2 |
| Pitchgut | 1.35.45 |
| Furrowmaw | 1.17.42 |
| Fire & Ice Cerberus | 2.18.42 |
| Hollowbell | 1.12.45 |
| Lantern Willow | 1.10.45 |
| Wreckback | 1.2.50 |
| The Swarmforge | 1.4.48 |
| The Gloamwick | 1.2.29 |
| The Hearthstride | 1.3.0 |

## Install

The jar is on the repo's **Releases** page on GitHub (Giants All-in-one 1.18.0), not in this folder: it's too big
to keep in git.

Easy way: run `install-all.ps1` in the Hollowbell folder. It downloads the newest all-in-one from Releases, takes
the single giant jars out of your mods folder and puts it in.

By hand: take `pitchgut-…`, `furrowmaw-…`, `fire-ice-cerberus-…`, `hollowbell-…`, `lanternwillow-…`,
`wreckback-…`, `swarmforge-…`, `gloamwick-…` and `hearthstride-…` out of your mods folder, and the old
`giants-all-…` jar too, then put `giants-all-1.18.0.jar` (from the Releases page) in. Keep Fabric API in there.
For a server, put the same jar in the server's mods folder.

If a single jar is left in by mistake the game still starts, but take it out anyway so the right version runs.

## The Giants Guide

A book about every giant you have. Right-click it to open it.

- **Getting one:** everybody gets one the first time they join a world. Craft another from a book and a compass
  (any shape). `/giantsguide` gives you one, no cheats needed.
- **On the left:** every giant that's installed, with a picture of him. Click one.
- **On the right**, a tab for each part:

| Tab | What's on it |
|---|---|
| About | what he is, how big, how he fights (juggernaut, ambusher, skirmisher, aerial, fortress, warship, factory, spirit or home), his moods |
| Where | his ground and what it looks like, how to find him (the finder and its recipe), whether the world keeps one of him out there and how many at once. On a server it also says where he is right now. |
| Moves | every move, light, medium and heavy (and Wreckback's sea moves), with what it does (the same words as his own book) |
| Army | the Swarmforge only: every robot and unit he sends out, and the army orders in his book |
| Fighting | where to hit him, what hurts him most, and tips |
| Drops | his weapon, his armour and its two powers (R and Sneak + R), his ward, the rest of his loot and his book, with pictures of the real items and their recipes |
| Commands | the commands anyone can use, and the ones that need cheats |

- **Giants together**: who fights whom and who keeps away when they meet, and every `/giants` command.
- **This world** (the last one on the left): this world's rules (its preset, how dangerous the giants are and what's
  different from Classic), votes on the next preset, and the leaderboards. With My Giant on, Pick your giant is here.
- Scroll with the mouse wheel, the arrow keys or the bar on the right. Hold the mouse over an item to see its name.
- It only shows the giants you have, so it works with any of them on their own too.
- The "right now" part needs the guide on the server as well (it is, if the server has this jar). Otherwise it
  tells you to use his Finder. `/giants where` and every `/<giant> where` need cheats.
- **Giants Performance** (`/giants performance` or `/giants effects`, a key in Controls, or the Performance button
  in the Guide): presets from Highest (every giant exactly as made, nothing changed) down to Very Low, your own
  saved presets, and every effect, detail and world feature each giant has, each saying what it does and what
  turning it down loses. World features are for operators, or the owner of a single-player world.
- **Controls** (the second tab on Giants Performance): every giant's keys in one place, grouped by giant, each with
  what it does. Click a key and press the one you want; it's the same key as in Minecraft's Controls. Keys that
  clash show red, and each has a Reset, with Reset all at the top.
- Setting: `giveOnFirstJoin` in `config/jj_giants.json` (on). Turn it off and nobody gets one on joining.
- New in the Guide 1.4.11: every giant's moves match his own book again, with the Cerberus's triple breath, rift
  and leaping slam, and the Gloamwick's pages have his real size, his new look, his dark wood and his ward, the
  Gloam Lantern.
- New in the Guide 1.4.12: the Gloamwick's Eclipse has the Hush, his shadow got up, that hunts by sound while the
  sky is black. Keep still or sneak; it can't be killed, only driven off for a moment.
- New in the Guide 1.4.13: the Giants Performance screen, the new operator commands on the `/giants` page
  (`home`, `health`, `damage`, `preset`, `heal`, `calm`, `sleep`, `wake`, `summon`, `freeze`, `unfreeze`, `info`),
  `where` for operators only (players use the Finders), the old `detail` commands gone (the screen has them), and
  every giant's respawn, grudge and round 3 text brought up to date.
- New in the Guide 1.4.14: the Controls tab on Giants Performance, to change every giant's keys there, how giants
  back down from a wild fight they're losing (and `/giants flee`), and sitting anywhere on a giant with a triple
  crouch.
- New in the Guide 1.4.16: the Drawing setting (in Detail on Giants Performance): Fast to start with, Classic for
  the old drawing.
- New in the Guide 1.4.17: the new healing rule for every giant, and the right number of Cerberus pelts.
- New in the Guide 1.5.0: admin settings for operators. `/giants admin list|get|set|reset|log|preset|export|import`
  changes any giant's settings, and the all-giants multipliers, for this world only, with a log of every change.
  Every command that changes a setting takes `world` (this world, the same as saying nothing) or `allworlds` (the
  server default). `/giants adminbook` gives the Giants Admin Book, whose pages come in the next update. The
  Performance screen says "Sent to the server." when an operator's change goes through.
- New in the Guide 1.6.0: the Giants Admin Book (`/giants adminbook`, operators). Every setting of every giant on
  pages, with search, Simple and Advanced, a danger rating and clash warnings on Home, Live control of every giant
  in the world (with Summon here), a Changes page with undo and snapshots, moderators, locks, a Presets page with
  saved presets and an export/import paste box, and a settings sync file for server owners.
- New in the Guide 1.7.0: a Giants… button on Create World's More tab picks a preset and changes any giant
  setting before the world is made (Edit settings… opens the admin book for the new world), the presets Close
  Quarters and Far Frontier, and `config/jj_giants_admin/new_world.txt` for dedicated servers.
- New in the Guide 1.8.0: fifteen new presets that change how the giants behave (Pocket Giants, Titans, Gentle
  Giants, Kaiju War, Night Terrors, Wild Sizes, Siege, One Life, Boss Rush, Nomads, Hardcore, Builder / Showcase,
  Public Server Safe, Lite, Speedrun), each with its danger rating, and ten new settings on every giant's page.
- New in the Guide 1.9.0: a new look for the admin book (its own texture, General and Special halves, sections,
  set one value for every giant at once, See all changes, a big Giants button on Create World), the Players page
  (My Giant with each player's own book, the player switches, each player's rules, votes, leaderboards and the
  starting kit), the This world page, and the Giants: Challenges advancements.
- New in the Guide 1.10.0: two more parts of the admin book. Server tools (pause all giants, quiet mode, events,
  schedules, areas and dimension rules, a map, stats, alerts, respawn timers, per-world profiles and pack mode),
  and Fun and World tools (watch any giant, play as one, stage a fight, names and titles, custom loot, giant tags,
  the Biome Brush, arenas and dens, and moving a giant to live somewhere else).
- New in the Guide 1.11.0: the admin book's **Systems** page (herds, growth, hunger, loyalty, grief healing, what
  is left when a giant falls, dying near a giant, stronger with more players or time, weak points, fear and awe,
  seasons and the full moon), the presets Herds and Evolution, a map you can drag and zoom, Watch for moderators,
  giants in each other's grounds, and Home keeping the preset's name during an event.
- New in the Guide 1.12.0: seven more systems on the Systems page under "The world around them" (village
  reactions, mob followers, Weather Gods, the Underworld, sacred ground, Mirror and random chaos), the presets Random
  Chaos, Underworld, Weather Gods, Sacred Ground and Mirror, remains made from each giant's own body, and dens and
  arenas built with real detail.
- New in the Guide 1.13.0: the admin book's **Minigames** page (eleven minigames, each round in a world of its own)
  and the **Fight script** on the Fun page (staged fights between giants), the giant-fight settings on the Systems
  page under "Giant fights", Minigames on the Guide's This world page, and fewer boss bars on screen.
- New in the Guide 1.14.0: the admin book's **Adventure** page (scripted giants, command hooks, adventure maps),
  the **Editor** page (giant looks) and the **Sound** page (music, ground sounds, voices), and share codes.
- New in the Guide 1.14.1: minigame worlds load only their own small area (much less lag), a chat note when the game
  has too little memory, Wreck Cove fixes and book layout fixes.
- New in the Guide 1.14.2: the Wreck Cove Rescue's guard is a real threat and the lost giant keeps to the road,
  giant tags can be given from the book (from a list, the one you look at, or every one of a kind) and shown without
  his own name, and the Guide's go beats a giant's keeping away.
- New in the Guide 1.16.0: **Max giants per player** on the admin book's Players page (a limit of 1 to 9 for the
  world or for one player, or no limit; every giant a player owns counts, and at the limit they keep theirs but can't
  gain more; also `/giants players <player> max`), the Hearthstride's page tells how to drive him from his helm and
  what's left when he falls, the Close Quarters preset's grounds are a little smaller so all nine giants fit, and a
  Speedrun won right at Go still keeps a best time.
- New in the Guide 1.16.1: the Hearthstride's page knows all 14 forms and all 70 of his moves by name, says which
  form each move belongs to, and tells about building on him and who his foes are.
- New in the Guide 1.16.2: the Hearthstride's page says he only fights his foes.

## New in 1.18.0

The Hearthstride gets to work: lasting works for every form, and a Switches page in his book.

### The Hearthstride 1.3.0

- **Works:** every form has three lasting works, 42 in all. Pick one on the Orders page of his book and he stays
  put and keeps at it, a little at a time, while he's parked in that form. **Stop work** stops him, **Pack up** takes
  down what his works built (dug ground stays dug), **Show work area** marks its edge. What he gathers goes into his
  storage.
  - Cottage: Homestead, Hearth Kitchen, Rest Stop
  - Bastion: Hold the Line, Garrison, Moat
  - Wingfold: Roost, Courier, Scout Flights
  - Scuttler: Forager, Den, Path Maker
  - Workshop: Forge, Smelter, Tinker
  - Library Tower: Study, Ward Circle, Archive
  - Farmstead: Tend the Fields, Ranch, Orchard
  - Delver: Dig a Mine, Quarry, Light the Caves
  - Inn: Open House, Market, Tavern
  - Chapel: Hallowed Ground, Grave Keeper, Healing Font
  - Alchemist's Cottage: Brewery, Herb Garden, Potion Rack
  - Observatory: Survey, Weather Watch, Giant Tracker
  - Watchtower: Keep Watch, Lighthouse, Alarm Bells
  - Ark: Fishery, Dock, Kelp Farm
- Works only break natural ground, never anything built, keep out of spawn protection and follow the mob griefing
  rule. Each work's tip in the book says what it does.
- **The Switches page:** over 40 things he does, each with a tip, to turn on or off, in five groups: looking after his
  household, in a fight, getting about, home and works, looks and sound (rescue the hurt, guard his household, hit
  back, follow me, wander when idle, change form himself, work by himself, pack up when leaving, lights at night,
  chimney smoke and more). **Strangers are foes** is one of them now. On the Moves page each move has a **By
  himself** row.
- **Batten Down, redone:** the Ark's Batten Down lasts 15 seconds: blows land at less than a third, arrows glance off
  and his household is braced. Its boards lie flat on his hull.
- **Fixes:** raid waves always come, even near a work that keeps foes away; works and switches only stop natural
  spawns, so spawn eggs, spawners and commands work as usual. Blocks a work put down drop nothing when broken (what's
  in a chest still falls out), Dock boats break into nothing, and Garrison golems left behind are taken away. Survey
  draws only land that's been explored and makes no new land. Light the Caves leaves players' roofed rooms dark. The
  Wingfold's Roost pad lies just before his door.

### The Giants Guide 1.16.2

- The Hearthstride's page says he only fights his foes (it said nobody needs to fight him).

Inside: Pitchgut 1.35.45, Furrowmaw 1.17.42, Cerberus 2.18.42, Hollowbell 1.12.45, Lantern Willow 1.10.45,
Wreckback 1.2.50, Swarmforge 1.4.48, Gloamwick 1.2.29, Hearthstride 1.3.0, Giants Guide 1.16.2.

## New in 1.17.2

A big update for the Hearthstride: eight new forms, real move effects, and players and giants as his foes.

### The Hearthstride 1.2.0

- **Eight new forms**, 14 in all: the Farmstead (a long barn with a silo and a water wheel), the Delver (a mine-head
  on digging claws), the Inn (a coaching inn with a market under it), the Chapel (a stone chapel with a bell tower),
  the Alchemist's Cottage (a tall tower house of jars and cauldrons), the Observatory (a domed tower with a
  telescope), the Watchtower (a lighthouse on long legs: knock at the foot of his tower to come in) and the Ark (a
  ship with his house at the bow: on deep water he floats and sails). Each has its own lit, furnished rooms on its
  own storeys and its own helm, and five moves of its own: 40 new moves.
- **Big, real move effects:** his moves show real things instead of odd light: crops shooting up from tilled beds, a
  well geyser, a drill on a shaft and great lamps on booms, a great bell, a rose window, a big cauldron and thrown
  flasks, stars and an armillary, a flare shot high with a wide red glow, rope nets, lifebuoys and bellied sails. The
  Wingfold's wings fold alike, with a white cloud, wind streaks and swoop trails. Ore Sense counts the ores about him,
  Star Reading names the nearest village and giant, Sound the Alarm counts the foes and giants about him.
- **Build on his outside:** his household can build on his walls and roof with any block. It moves, grows and shrinks
  with him and belongs to the form it was built on. Beds there set your respawn and furnaces cook. His Keeper says
  whether residents may build there; admins can turn it off or set how many blocks each form carries.
- **Smoother ride inside:** no lurch on lag, a gentler bob, steady stairs.
- **He stands and steps properly:** the feet holding him stay on the ground in every move, his legs fold at the
  joints when he settles, his moves dip and lean in one smooth curve, and he lunges into quick strikes with a foot
  forward. His chimney no longer clips through his roof.
- **His foes:** monsters, plus players (when the server allows PvP) and giants who hurt him or his household in the
  last half minute. His moves push, slow and light up foes; a player or giant is never laid down, and a giant is only
  nudged. His household, their giants and his Keeper's team are never his foes. His Keeper can turn on **Treat
  strangers as foes** in his book. Docked giants go for a player or giant only when it's his foe. Two new admin rows:
  **Players can be his foes** and **Giants can be his foes** (both on by default).

### The Giants Guide 1.16.1

- The Hearthstride's page knows all 14 forms and all 70 of his moves by name, says which form each move belongs to,
  and tells about building on him and who his foes are.

Inside: Pitchgut 1.35.45, Furrowmaw 1.17.42, Cerberus 2.18.42, Hollowbell 1.12.45, Lantern Willow 1.10.45,
Wreckback 1.2.50, Swarmforge 1.4.48, Gloamwick 1.2.29, Hearthstride 1.2.0, Giants Guide 1.16.1.

## New in 1.17.1

A big update for the Hearthstride, safe ground round him for every giant, a limit on how many giants one player may
own, and fixes for most giants.

### The Hearthstride

- **Drive him yourself** from his helm, the map table by his hearth, in every form. W goes, S stops, A and D turn
  him, Space strides (other forms climb, the Wingfold goes up), Shift settles or lands him, V looks out at him, G
  leaves. Only his Keeper drives him, plus residents if the Keeper allows it.
- **His Hearth Book works like the other giants' books.** It shows his distance, health, form and what he's doing,
  with clear buttons: Come to me, Follow me, Walk where I look, Send him to a spot, Where is he?, Stay here, Let him wander. It's
  in the creative Tools tab too, and one book covers every Hearthstride you are Keeper of.
- **His rooms match his outside:** the rooms you see through his windows and door are the ones you walk into, storey
  by storey, with no jump at the door. They're lit and furnished, with glass panes in the windows and a stairwell,
  and they never poke out through his walls or roof.
- **Living in him feels like being your own size:** your view bobs, and sounds and particles are those of a normal
  house.
- **Real doors and real blocks:** his doors really shut (you can't walk through them at any size), and every block in
  his rooms has its real shape.
- **Blocks you build in him work as they do in the world.** Furnaces, smokers and blast furnaces cook while he walks.
  Beds, crafting tables, doors, gates, levers, anvils and looms all work. Ladders, vines and scaffolding can be
  climbed, fences, panes and walls join up, and stairs turn corners. Filled shulker boxes, named banners and heads
  come back exactly as they were.
- His Keeper can take out the furniture and inside walls he was built with, and put it all back from his book
  (residents too, if allowed).
- Things dropped in his rooms, including your items if you die in him, land on his floors, ride with him and stay
  through rebuilds and saves.
- **Much more solid** to stand on and ride in, at every size and in every form: no slipping through, bouncing or
  getting stuck. His floors no longer lurch when he turns, the Bastion most of all.
- Logging out in him, changing worlds, teleporting, or your bed in him being far off now always brings you back in his
  rooms or safely on the ground. You can't go in when he's too small for his doors, and anyone inside when he shrinks
  that small is set down beside him. Going out of his door sets you down past his porch.
- **New look:** slim chicken ankles with splayed toes and a back toe, no lone block at the toe tips, matching legs,
  far less clipping, and he sits flush on slopes. The Wingfold now stands and walks on its legs on the ground, tucks
  them up to fly and lets them down to land.
- **His moves show real things** (hay bales, banners, pages, an iron wall, a horn) instead of glows. Dust, ground
  cracks and splinters fly at his heavy moments, with a little screen shake. Both can be turned down in the settings.
- You can see him on the skyline from far off (1,024 blocks by default).
- **Docking:** giants owned by people who live in him can dock beside him (from his book or `/hearthdock`). They
  rest, heal twice as fast, follow when he walks off, and, if you allow it, go for whatever hits him (never a player).
- **No more relighting:** when he falls his ruin stays a ruin, and his storage and everything built in him is in
  chests by the cold hearth. A new wild one comes 3 days later. When he's taken away by a command or an admin, nobody
  is left in the air and his things are left in chests. His "most at once" limit never takes away someone's home.
- The Giants Guide's pause (and `/giants freeze`) now holds him still too.
- Fixes: `/hearthstride tp` reaches him even in unloaded land, his Beacon no longer makes monsters already saved
  nearby vanish when their land loads, and there is less lag when lots of creatures are around.

### All giants

- **Max giants per player** (admin book, Players page): a limit of 1 to 9 for the whole world or for one player, or
  no limit. Every giant a player owns counts toward it, and so does being a Hearthstride's Keeper. At the limit they
  keep the giants they have but can't get more: a giant's book won't come to them, a copy they're given stays quiet
  and tells them why, and an op who gives them a book is told they're at their limit. There's also a command:
  `/giants players <player> max`.
- **Safe ground round the Hearthstride:** in the yard round his home, and round him wherever he stands, no fight
  between giants starts, and the giants leave the players there alone and break no blocks. A giant resting beside
  him won't start fights either. Pitchgut and the Cerberus can still go for the Hearthstride himself.
- **Held to their size:** a small Furrowmaw, Wreckback or Hollowbell can no longer knock you further than his size
  allows, and nor can a crowd of Furrowlings or Bellings. That covers the Furrowmaw's hits and thrown magma clods,
  Wreckback's claw squeeze, holds, eye beams, lightning and bell's toll, and the Hollowbell's spore clouds, stings,
  eggs and stingers, and his grip when he has taken you in. A very small Cerberus stays big enough to hit, every part of him, while he moves as well as while he
  stands still.

### Each giant, and the Guide

- **Pitchgut:** if you're hit while riding him, you're thrown off right away, even where his land hasn't fully loaded
  yet or the server is busy. His feet no longer come down a block inside the ground when he steps onto a ledge or
  into a hole his goo ate.
- **Furrowmaw:** when he shudders on stepped ground, his rings no longer dip into the ground. When he comes back after
  travelling unseen, he comes up facing where he's going (before, he could turn to the nearest player and crawl off
  backwards).
- **Cerberus:** after a long ride he still knows you're his rider, so his steps beside you as you get off no longer
  hurt you. The game now knows which way he's facing, so a teleport or reloading the world no longer turns him round.
- **Hollowbell:** Egg rain's eggs land right on their markers on the ground, when he drops them from low down, when
  he aims them far off, and far out in the world. If you're standing on him and one of his arms curls up around you,
  you're lifted up onto the arm instead of pushed off its edge.
- **Lantern Willow:** very far out in the world (millions of blocks from spawn), his roots and strands land exactly
  where he aims, and you hit the part of him you point at. Lantern Willows in a minigame no longer count toward how
  many this world holds, so this world's own Lantern Willows (and My Giant ones) still come.
- **Swarmforge:** picking him again with My Giant when he's already yours never
  counts as a new giant, even if he's still waiting for the land around you to load before he rolls in.
- **Giants Guide:** the Hearthstride's page explains how to drive him from his helm, and what happens when he falls:
  his things are kept in chests by his cold hearth, the hearth can't be lit again, and a new wild one comes later. The
  Close Quarters preset makes each giant's ground a little smaller, so all nine giants fit. In the Speedrun minigame,
  bringing a giant down right at Go now still counts as a best time.

Inside: Pitchgut 1.35.45, Furrowmaw 1.17.42, Cerberus 2.18.42, Hollowbell 1.12.45, Lantern Willow 1.10.45,
Wreckback 1.2.50, Swarmforge 1.4.48, Gloamwick 1.2.29, Hearthstride 1.1.0, Giants Guide 1.16.0.

## New in 1.17.0

### The Hearthstride joins

JJ's ninth giant: an old timber cottage walking on two great chicken legs, about 145 blocks tall. Look twice and his
front door is a mouth and his two upper windows are eyes.

- **He doesn't fight: he's a home.** Come in through his door and live in his rooms while he walks, strides or
  settles down like any house. His rooms are built at your size: you grow or shrink to fit them as you come in.
- **Six forms:** the Cottage, the Bastion (a castle on four stone legs), the Wingfold (a house with wings that
  flies), the Scuttler (a quick little shed), the Workshop and the Library Tower. He rebuilds himself from one to
  another by moving his own walls, roof and legs, and carries you safely through it.
- **Beds, storage and building:** a bed in him is your respawn point wherever he walks; every chest and barrel in him
  opens his one shared storage; those he lets live in him can build in his rooms.
- **Trust:** shelter in him, sleep in his bed, never hit him. At full trust he gives you his Hearth Book and you're
  his Keeper: choose who may come in, give roles, order him about.
- **Thirty moves** (five a form) to keep foes off his household: door snaps, hay bales, a drawbridge, wind, an iron
  wall, a lullaby and more. They hurt nothing, or hardly.
- **His Hearthmeadows**, 3,000 to 15,000 blocks from spawn: rolling meadows with Hearthblooms and Thatchgrass, a brook
  and his yard. Find him with a Hearth Lantern.
- **When he falls** he leaves a ruined cottage and a cold hearth. His Keeper can light it again (16 logs, 16
  cobblestone and a lantern) and he stands up there.
- Pitchgut and the Cerberus may go for him. Every other giant keeps away from him, and won't start a fight with
  another giant near him.
- The Giants Guide has his page, the admin book has his settings, and the Performance screen his chimney smoke and
  lit windows. He never fights in minigames, and he's earned by trust, so he isn't one of My Giant's picks.

Inside: Pitchgut 1.35.44, Furrowmaw 1.17.41, Cerberus 2.18.41, Hollowbell 1.12.44, Lantern Willow 1.10.44,
Wreckback 1.2.49, Swarmforge 1.4.47, Gloamwick 1.2.28, Hearthstride 1.0.0, Giants Guide 1.15.0.

## New in 1.16.2

Tiny giants you can fight fairly, everything sized with the giant, one size rule for all eight, new speeds, the
Wreck Cove Rescue's guard and giving tags from the book.

### Tiny giants

- You can always hit a tiny giant (size 0.01 to 0.1) with a sword, an arrow or a trident.
- A tiny giant never flings, traps or smothers you or your animals: every push, grab, swallow and blast is held to
  his size, and he only picks up what's small enough for him to hold.

### Everything grows and shrinks with him

- Every giant's moves, particles, sounds and screen shake now shrink and grow with him at every size, from 0.01 to
  giant size. A small one sounds smaller and a little higher.
- His minions (robots, furrowlings, mudlings, bellings, crew and the rest) are sized with him, with health, damage and
  sounds to match.
- Health, damage, armour and speed follow one size rule shared by all eight giants, so two giants of the same size are
  a fair match. At size 0.01 every giant has 60 health (the Swarmforge 40).
- **The Swarmforge's robots drop nothing** any more (no items, no experience). He still drops his own loot.

### Speeds

- **Cerberus is the fastest giant**, at every size: about 15 blocks a second at full size.
- **Wreckback is faster:** about 5 blocks a second on land, 6 when he comes after you, 6.5 in the sea.
- Furrowmaw and Hollowbell are a little slower when they're small, so they never outrun Cerberus. At full size they're
  as fast as before.

### Giants and the Guide

- The Guide's **go** now beats a giant's keeping away from another giant: he goes where you sent him.
- In minigame worlds and scripted adventures, giants never stop to hold meetings.

### The Wreck Cove Rescue

- The lost giant keeps to the shore road, round its corners, at a pace you can keep up with, and waits for you when
  you fall behind.
- The guard is a real threat now. He's twice the lost giant's size and walks the cove's west waterline, right by the
  beach where the lost giant waits. Wade into the lagoon or come within 20 blocks of him and he comes after you, up the
  beach and the road too, until you're 60 blocks away. He keeps up with a walking player, so sneak past while he's at
  the far end, or sprint. He never touches the lost giant.

### Giant tags (admin book, Fun page)

- Give a tag to a giant straight from the book: pick one from a list (the nearest at the top, with the tag each one
  wears now), the giant you're looking at, or every giant of one kind.
- A tag's title can be shown alone: set **His own name** to *Left off* and his boss bar says just "Terror of the Bay"
  instead of "Wreckback, Terror of the Bay".

Inside: Pitchgut 1.35.43, Furrowmaw 1.17.40, Cerberus 2.18.40, Hollowbell 1.12.43, Lantern Willow 1.10.43,
Wreckback 1.2.48, Swarmforge 1.4.46, Gloamwick 1.2.27, Giants Guide 1.14.2.

## New in 1.16.1

A quick fix release: the lag after minigames, the Wreck Cove Rescue, and a few book layouts. Released without the
full test run: every changed mod was built and the jar was checked on a server.

### Less lag in minigames

- A minigame's world now loads only its own small area, not a whole world's worth round every player. That saves a
  lot of memory, so the game no longer freezes again and again during and after a round.
- If your game has too little memory for the giants, a note in chat tells you once and says how to fix it. Minecraft
  gives itself 2 GB by default, which is too little for the giants: in the launcher go to **Installations**, the
  Fabric profile, **More options**, and in **JVM arguments** change `-Xmx2G` to `-Xmx6G`.

### The Wreck Cove Rescue

- The two map giants never fight each other.
- The lost giant stays calm: he never turns on the players who came to help him.
- He walks the shore road home at a walking pace and waits for you (new `escort` script step:
  `escort <path> [blocks]`).
- The guard only goes for players who wade out into the lagoon. Reaching the lost giant on his beach and walking him
  home along the shore road no longer sets the guard on you.

### Admin book

- Editor page: on a custom set, the set's name and buttons never overlap.
- Swarmforge's Special and Behaviour pages: the robot switches no longer overlap.
- Question buttons wrap their text onto a second line instead of running off the button.

Inside: Pitchgut 1.35.42, Furrowmaw 1.17.39, Cerberus 2.18.39, Hollowbell 1.12.42, Lantern Willow 1.10.42,
Wreckback 1.2.47, Swarmforge 1.4.45, Gloamwick 1.2.26, Giants Guide 1.14.1.

## New in 1.16.0

This completes the Giants Admin Book. Released without the full test run (JJ's call, to get it out faster): every
mod was built and the jar was load-checked on a server.

- **Adventure maps** (admins: the admin book's Adventure page): give a giant a script (walk or patrol a path, wait
  for players, guard a spot, fight, talk, set his title), run commands when something happens to a giant (he falls,
  wakes, gets hurt, enters an area, sees a player...), and save a piece of your world, giants and all, as an
  adventure map that's played as a minigame round. The Adventure Wand marks paths and areas. One map comes ready:
  **The Wreck Cove Rescue** (bring a lost young giant home past the cove's guard). Winning any adventure map gives
  the new **Adventurer** challenge. Everything works from command blocks too.
- **Giant editor** (the Editor page or `/giants admin look ...`): dress a whole kind, a tagged group or one giant in
  another look: Frost, Gold, Shadow, Festive, Ancient stone, and new **Ember** and **Crystal**, or your own set. Only
  how he's drawn changes. Frost is lighter now (icy white and pale blue, even on dark giants). The Swarmforge's
  robots, Hollowbell's Bellings, the Lantern Willow's Mudlings and Furrowmaw's Furrowlings wear their giant's look,
  and followers take its colour. Looks belong to the world: they aren't part of presets or share codes.
- **Sound and music picker** (the Sound page): per giant, his fight music (his own, none, or another giant's), his
  ground sounds on or off and how loud, and his voice higher or deeper.
- **Share codes**: share any preset as a short `GS1-...` code (Presets page or `/giants admin share`) and import it
  on another server. No internet needed.
- **Staged fight fix**: when a staged fight is stopped or ends, both fighters drop every target and grudge (players
  and each other), calm down for a minute and walk home. In 1.15.0 a fighter could still go after a player standing
  near the fight right after Stop.
- The Wreck Cove Rescue's giants are smaller, so both fit the cove.

Inside: Pitchgut 1.35.42, Furrowmaw 1.17.39, Cerberus 2.18.39, Hollowbell 1.12.41, Lantern Willow 1.10.42,
Wreckback 1.2.47, Swarmforge 1.4.45, Gloamwick 1.2.26, Giants Guide 1.14.0.

## New in 1.15.0

- **Minigames** (operators: the admin book's Minigames page or `/giants minigame`): eleven modes, Giant Royale (the
  safe zone closes in, last player standing wins), Boss Rush, Siege, Escort, Legends, Possession, Rival Tamers,
  Bounty Board, Kaiju War, One Life and Speedrun. Players join with Join on the Guide's This world page, [Join] in
  chat or `/giants join`, can watch with `/giants spectate` and leave with `/giants leave`. Every round is played in
  a world of its own, made for it and thrown away after: nothing in your world is touched, and every player gets
  their things, place and levels back exactly, even if they log out or the server stops mid-round. Twelve settings
  (round time, giant size, players, PvP and more), a Minigame winners board and eleven challenges.
- **Staged fights** (the Fight script on the Fun page, or `/giants admin fight ...`): pick one or two giants a side,
  who wins (or let them fight), how the loser ends (to the death with the winner's finisher in slow motion, running
  off, run down and caught, or bowing out), the length, rounds, the arena, the countdown, the announcer, fight music
  and one boss bar for the whole fight. Players can bet emeralds (`/giants fight bet a|b <emeralds>`) or join a side
  (`/giants fight join a|b`). Save a setup and replay it, or put it on the schedule. A giant's finisher is never a
  move the world switched off: then he uses his next big move that's still on. A losing giant who runs for it
  really runs (a little faster for a few seconds), and the fighters are part of the fight from the moment they
  stand there, so no wild giant can start on them before the bell.
- **Giant-fight settings** (41, on the Systems page under "Giant fights"): for wild fights and owners' fights apart,
  how far a fight goes, whether the loser is chased and caught or spared, damage between giants, blocks broken
  (usual, none, or healed after), healing and rest after, the fight area, keeping out of villages, keeping players
  near safe, one bar for both, fight music, fight news, the winner growing, loyalty, the loser's loot, and whether
  owned giants may fight or kill each other. Players: `/giants fight standdown`, `allow` and `top`.
- **Fewer boss bars**: during a staged fight you see only the fight's one bar, and in a minigame the minigame's bar
  with only the nearest giant's bars under it, not a stack of five.
- When Pitchgut stands down from a fight he lets go only of his grudge against the giant he fought.
- Left as shipped, every new setting matches 1.14.0: giant fights go exactly as before, and nothing starts until an
  admin starts it.

Inside: Pitchgut 1.35.41, Furrowmaw 1.17.38, Cerberus 2.18.38, Hollowbell 1.12.40, Lantern Willow 1.10.41,
Wreckback 1.2.46, Swarmforge 1.4.44, Gloamwick 1.2.25, Giants Guide 1.13.0.

## New in 1.14.0

- **Seven more systems** on the admin book's Systems page, under "The world around them" (every one off until an
  admin turns it on): **Village reactions** (villagers flee from a wild giant, hide as for a raid, or gather round a
  calm one, look up at him and leave him bread each day; iron golems can go for wild giants), **Mob followers** (each
  wild giant gathers a band of his own kind of creature: Pitchgut magma cubes, Furrowmaw husks, Cerberus wither
  skeletons, Hollowbell phantoms, the Willow slimes, Wreckback drowned, the Swarmforge vindicators, the Gloamwick
  vexes; they fight beside him and drop nothing), **Weather Gods** (each giant brings his own weather to the land
  round him, seen only by players near him: ash, dust, sleet and embers, a storm, mist, a squall, smog, gloaming),
  the **Underworld** (the giants live in the Nether, the End or both instead of the Overworld, one by one, each sized
  to fit; the Furrowmaw goes to the End, he can't live under a roof), **Sacred ground** (no building or breaking in a
  living giant's ground, except in creative), **Mirror** (giants learn the tactic players lean on, melee, arrows,
  magic or blasts, and it hurts them less and less) and **Random chaos** (every game day each giant's size, mood and
  moves are shuffled). Five new presets: **Random Chaos**, **Underworld**, **Weather Gods**, **Sacred Ground** and
  **Mirror**. `/giants admin systems` shows them too.
- **Remains made from each giant's own body**: with corpses or skeletons on, a fallen giant lies where he fell, his
  real size and heading; a corpse rots away in four stages down to his skeleton, and a skeleton stays for good.
  Admins can lay one anywhere: `/giants admin systems remains <giant> <size> skeleton|corpse [stage]`.
- **Dens and arenas with real detail** (World tools): Pitchgut's crater, the Furrowmaw's burrow, the Cerberus's
  kennel, the Hollowbell's roost, the Willow's mire, Wreckback's cove, the Swarmforge's yard and the Gloamwick's
  glade, each big enough for him to fit inside, and arenas with seats, banners and a clear floor. Nothing in them
  traps or slows you, grows, spreads or falls.
- Under the Nether's roof every giant stands on the floor below, never on the roof. Fixes: Wreckback's shots burst
  where they hit and getting off him sets you clear of his body; the Swarmforge's brutes hit what they swing at; the
  Willow's Nether shrine stands on the floor; the Hollowbell stops a little further back when following you and a
  rider's press up lifts him sooner; two gaps in the Furrowmaw's body are closed and jumping up from a seat sets you
  right on him; standing up from a seat on the Cerberus sets you clear of his edge.
- Steadier game tests behind the scenes.
- Left as shipped, every new system is off and the giants play as in 1.13.0 apart from these fixes.

Inside: Pitchgut 1.35.40, Furrowmaw 1.17.37, Cerberus 2.18.37, Hollowbell 1.12.39, Lantern Willow 1.10.40,
Wreckback 1.2.45, Swarmforge 1.4.43, Gloamwick 1.2.24, Giants Guide 1.12.0.

## New in 1.13.0

- **New systems** in the admin book's new **Systems** page (every one off until an admin turns it on): **Herds**
  (giants of a kind keep together round the world's own one), **Growth** (they grow with the days and their wins,
  and one left alone long enough becomes a Titan), **Hunger** (they must eat; hungry ones weaken and hunt, fed ones
  calm down), **Loyalty** (how an owner treats their giant changes his mood; at none left he can turn on them),
  **Grief healing** (blocks the giants break grow back, slow to instant; while it's on, those blocks drop nothing),
  **When a giant falls** (his drops only, or also a corpse that rots or a skeleton you can mine), **Dying near a
  giant** (normal, keep your things, or he swallows them until he falls), **Stronger with more players, with time
  or against armour**, **Weak points** on each giant's body (hits there do more), **Fear and awe** (slowness and
  darkness near a hunting giant, strength beside your own) and **Seasons** and the **full moon**. Two new presets:
  **Herds** and **Evolution**. `/giants admin systems` shows what's on.
- An admin's resize from Live control (or a giant tag) now sticks while Growth is on: he grows on from that size.
- **Follow me** is the same for every giant: he sets off once you're a little past his body and stops just clear of
  it. Small ones keep close; big ones stay further back (at full size the Lantern Willow stops about 136 blocks off
  because of his strands, Wreckback about 163). The Willow no longer speeds up to catch up (he is meant to be slow).
  Wreckback and the Gloamwick no longer wander off while following. The **Swarmforge** has his own Follow me / Stop
  following, and turning the players' books off also ends **Commander Mode**.
- Home keeps the preset's name during an event or a schedule ("Classic · Event: Giant war"). Pause all giants also
  stops the Swarmforge's robots. A dimension set to "no giants" also keeps the world's own giants from coming back
  there until it's lifted. The **map** drags and zooms (Fit shows the players and spawn). **Moderators may Watch**
  giants (an admin switch, on to start with). New setting **Giants in each other's grounds** (off; on in Wild Sizes
  and Nomads): the world's own giants may also come in other giants' grounds, with their companions beside them.
  The pick screen shows every giant as Normal danger.
- Left as shipped, nothing about the giants changes except Follow me's distances.

Inside: Pitchgut 1.35.39, Furrowmaw 1.17.36, Cerberus 2.18.36, Hollowbell 1.12.38, Lantern Willow 1.10.39,
Wreckback 1.2.44, Swarmforge 1.4.42, Gloamwick 1.2.23, Giants Guide 1.11.0.

## New in 1.12.0

- **Server tools** in the admin book: **Pause all giants** (every giant stops where he is), **Quiet mode** (no giant
  chat for players, no chat lines for admins), **events** that run for a while and end by themselves (Giant war,
  Siege on spawn with griefing on while it lasts, Boss Rush hour), **schedules** that put a preset on at weekends, on chosen days, on full-moon
  nights or on a date, **areas** where giants aren't allowed or players are left alone, no giants near spawn or in a
  whole dimension, a **map** of the giants, their grounds and the players (click to go there), **stats** for each
  giant (falls, players killed, blocks broken, what he costs the server), **alerts** to admins (a fight near spawn,
  heavy griefing, a player killed; off to start with in single player), **respawn timers** (bring one back now or set the wait), **per-world
  profiles** and **pack mode** for modpacks. Commands under `/giants admin` (`pause`, `quiet`, `event`, `schedule`,
  `area`, `respawn`, `stats`, `alerts`, `profile`, `pack`).
- **Fun and World tools** in the admin book: **watch** any giant with a hidden camera that follows him (or through
  his eyes), **play as** any giant with no time limit, **stage a fight** between two giants where you stand or look,
  **names and titles** on his boss bar, **custom loot** for one giant, **giant tags** (like "Event boss", with their
  own health, damage, speed, size and title), the **Biome Brush** to paint or erase a giant's ground, an **arena**
  and a **den** for each giant (both can be taken down again), and **moving a giant to live** somewhere else: on land,
  at sea (Wreckback, Swarmforge, Lantern Willow), in a cave (Furrowmaw) or in the sky (Hollowbell).
- While all giants are paused nobody can play as one or stage a fight, and anyone playing one steps out. Quiet
  mode also keeps the fun tools' notes to other admins out of chat (the change log keeps them).
- Left as shipped, nothing changes: nothing is paused, quiet, scheduled or kept out until an admin sets it.

Inside: Pitchgut 1.35.38, Furrowmaw 1.17.35, Cerberus 2.18.35, Hollowbell 1.12.37, Lantern Willow 1.10.38,
Wreckback 1.2.43, Swarmforge 1.4.41, Gloamwick 1.2.22, Giants Guide 1.10.0.

## New in 1.11.0

- **My Giant**: each new player picks a giant on first join and gets their own book of him and their own one of
  him, up to the world's limit (then they share the holder's book). Turn it on with the My Giant preset or the
  admin book's new Players page.
- **Players page** in the admin book: My Giant, the Rules page, votes, leaderboards (also on signs at spawn),
  challenges, the starting kit, a switch for each thing players may do (sitting, riding, standing on giants, armour
  powers, boss bars, fight music on or off for everyone, chat lines, books, finders, and "no attack orders", which
  also stops attack moves while riding), and each player's rules (may own giants, ignored by giants, no kit). The
  Swarmforge has his own Commander Mode switch.
- **The Guide's This world page**: rules, votes and leaderboards for every player. New advancement tab: **Giants:
  Challenges**.
- **A new look for the admin book**: leather and parchment with its own item texture, contents with a ribbon, a
  breadcrumb, General and Special halves on each giant's page, sections and folding groups, one value for every
  giant at once on All giants (`/giants admin setall`), See all changes on preset previews, and a big Giants button
  on Create World's first tab.
- **Meeting other giants**, a new setting for every giant: decide (as always), fight, run away or ignore.
- **Follow me** fixed on the Cerberus, Hollowbell, Wreckback, Lantern Willow and Gloamwick.
- Left as shipped, nothing changes: My Giant is off and every player switch is on.

Inside: Pitchgut 1.35.37, Furrowmaw 1.17.34, Cerberus 2.18.34, Hollowbell 1.12.36, Lantern Willow 1.10.37,
Wreckback 1.2.42, Swarmforge 1.4.40, Gloamwick 1.2.21, Giants Guide 1.9.0.

## New in 1.10.0

- **Fifteen new presets** in the admin book and on Create World's Giants… button. Suggested: Pocket Giants, Titans,
  Gentle Giants, Kaiju War, Night Terrors, Wild Sizes. All: Siege, One Life, Boss Rush, Nomads, Hardcore, Builder /
  Showcase, Public Server Safe, Lite, Speedrun (plus Classic, Close Quarters and Far Frontier). Each shows a danger
  rating from 1 Gentle to 5 Brutal, and they keep where the giants' grounds go.
- **Ten new settings for every giant**: Size spread, How many come (up to 8 at once), Comes back after he falls,
  Temper (peaceful, normal, hunter), Roaming (home, wander, stay), Seeks out other giants, Raids, Sleeps (usual, by
  day, never), Strength at night and Loot. The Gloamwick also has Eclipse more often.
- **Tiny giants**: every giant can now be as small as about 2 blocks tall.
- **Each giant's own config command** (`/wreckback config temper hunter` and so on) takes the choice settings too.
- **One Life**: once a giant's own one falls, his kind never comes back to that world.
- Left as shipped, every new setting keeps the giants exactly as before. My Giant comes next.

Inside: Pitchgut 1.35.36, Furrowmaw 1.17.33, Cerberus 2.18.33, Hollowbell 1.12.35, Lantern Willow 1.10.36,
Wreckback 1.2.41, Swarmforge 1.4.39, Gloamwick 1.2.20, Giants Guide 1.8.0.

## New in 1.9.0

- **Giants… on Create World.** The More tab has a Giants… button: pick a preset (Classic, Close Quarters, Far
  Frontier or one you saved), see its danger rating, and Edit settings… to change any giant setting in the admin
  book before the world is made. It all goes into the world before its first land, and the change log says "Set at
  world creation".
- **Four new world settings for every giant**: Nearest to spawn and Farthest from spawn (world creation only),
  Room from other grounds and Copies of his ground (only land not made yet). As shipped, worlds are made exactly as
  before.
- **New presets**: Close Quarters (grounds 500 to 1,500 blocks from spawn, smaller and 50 apart) and Far Frontier
  (5,000 to 20,000 blocks out). More presets come in the next updates.
- **For servers**: put a preset name or a settings string in `config/jj_giants_admin/new_world.txt` and every
  brand-new world the server makes gets it.

Inside: Pitchgut 1.35.35, Furrowmaw 1.17.32, Cerberus 2.18.32, Hollowbell 1.12.34, Lantern Willow 1.10.35,
Wreckback 1.2.40, Swarmforge 1.4.38, Gloamwick 1.2.19, Giants Guide 1.7.0.

## New in 1.8.0

- **The Giants Admin Book.** `/giants adminbook` gives operators a book with every giant setting on pages: Home (the
  preset, a danger rating from 1 Gentle to 5 Brutal, settings that clash, moderators), All giants, a page for each
  giant, search, Simple or Advanced, a "?" on every setting, markers for settings that only reach new spawns or new
  land, saving for this world or all worlds, a size picture beside a player, and a short walkthrough.
- **Live control.** Every giant in the world in one list (where he is, health, size, mood, asleep, fighting, owner,
  out of the world or not loaded) with Teleport, Heal, Size, Sleep, Wake, Calm, Forgive, Owner, Home, Remove and
  Kill, and Summon here for admins.
- **Changes.** The change log with Undo and Undo to here, snapshots kept before every preset, import and full reset
  (the last 10, with Restore), and locked settings.
- **Moderators** (Live control only) and **locks** (a locked setting keeps its value until an admin unlocks it), in
  the book or with `/giants admin moderator add|remove <player>` and `/giants admin lock|unlock|locks`.
- **Presets page.** Classic now (the other presets come in the next updates), your own presets saved for the whole
  server, a preview of every change with the danger rating before and after, and export/import with a paste box.
- **Settings sync** for server owners: put a settings string in `config/jj_giants_admin/sync.txt` and the server
  makes it the default for every world when it starts (`/giants admin sync status|now`).

Inside: Pitchgut 1.35.34, Furrowmaw 1.17.31, Cerberus 2.18.31, Hollowbell 1.12.33, Lantern Willow 1.10.34,
Wreckback 1.2.39, Swarmforge 1.4.37, Gloamwick 1.2.18, Giants Guide 1.6.0.

## New in 1.7.0

- **Admin settings, for operators.** Every giant's settings can now be changed for just this world, with
  `/giants admin`: `list` (every setting of a giant, with its range and whether this world has its own value), `get`,
  `set`, `reset` (one setting, one giant, or `everything`), `log` (who changed what, and when), `preset`, `export`
  and `import` (the whole world's settings as one line of text). Each giant has his spawning, stats, behaviour,
  every move (on or off, its wait and its wind cost), healing, taming, effects and server load, plus his own extras
  (the Swarmforge can have each kind of robot switched off). `all` holds the all-giants multipliers: health, damage,
  speed, size, move waits, healing and wind cost, on top of each giant's own. Settings marked "Only affects new
  spawns" or "Only affects new land" say so when you change them. Values are kept in the world, other operators are
  told about each change, and the giants' settings files stay the server default for every other world.
- **`world` or `allworlds` on every setting command.** `/giants health`, `/giants damage`, `/giants preset`, each
  giant's own commands (`/wreckback damage`, `/pitchgut heavydelay`, `config` and the rest) and the operator
  switches in the books: nothing said or `world` changes this world, `allworlds` changes the server default. The
  reply says which. A giant used without the Giants Guide keeps changing the server default when nothing is said.
- `/giants adminbook` gives the Giants Admin Book. It's a placeholder for now: its pages come in the next update.
- The Swarmforge's Loot Cache drops itself when you break it. The Pitchgut's Innards, the Swarmforge's crane seat
  (the Hold) and the Hollowbell's stinger shots show their names. The Guide's Performance screen shows "Sent to the
  server." instead of a code, and every giant's "stepped out of the world" server log line is worded the same.

Inside: Pitchgut 1.35.33, Furrowmaw 1.17.30, Cerberus 2.18.30, Hollowbell 1.12.32, Lantern Willow 1.10.33,
Wreckback 1.2.38, Swarmforge 1.4.36, Gloamwick 1.2.17, Giants Guide 1.5.0.

## New in 1.6.8

- **Healing is the same for every giant now.** A giant heals only while he's asleep, or once he's been out of a
  fight for five minutes (nothing has hurt him, he has hurt nothing and he isn't after anything). Then he gets back
  2% of his health a minute in his own ground and 1% anywhere else. Resting after backing down from a fight with
  another giant doesn't heal him any more, and Wreckback no longer heals faster in deep water. Settings in each
  giant's file: `passiveHeal`, `healHomePerMinute`, `healAwayPerMinute` and `healPauseMinutes`.
- **Every giant's book needs something from him**, and every book shows up in your recipe book once you pick up
  something of his that goes in it. New recipes (each is the giant's thing on top of a book, with his other thing on
  both sides of the book and under it):
  - Pitchgut: one of his eyes on top, Pitchgut flesh round the book (simpler: no nether star, horn or goo).
  - Wreckback: a barnacle block on top, Shell Plates round the book. He drops three more plates (27 to 33).
  - Cerberus: a blaze rod on top, Cerberus Pelts round the book. He drops three more pelts (29 to 35).
  - The Swarmforge: a redstone block on top, Forge Plates round the book. He drops three more plates (27).
  - Furrowmaw, Hollowbell, the Lantern Willow and the Gloamwick keep their books as they were.
- **Every finder is a compass and easy everyday things**, and shows up in your recipe book as soon as you pick up a
  compass or one of its other items. Each one is the middle item over a compass, with the side item on both sides
  of each (a 3 by 2 grid):
  - Pitchgut: a spider eye, with rotten flesh.
  - Furrowmaw: a bone, with coarse dirt.
  - Cerberus: a snowball, with coal or charcoal.
  - Hollowbell: an amethyst shard, with glass.
  - The Lantern Willow: a torch, with any kind of log.
  - Wreckback: string, with dried kelp.
  - The Swarmforge: redstone, with copper ingots.
  - The Gloamwick: an ink sac, with torches.
- **Furrowmaw's Hold still really holds.** He fights only what comes into his reach and never crawls, burrows or
  charges after anything. When his book can't give an order, it tells you why.
- **Furrowmaw no longer climbs up onto himself.** When he lies still, any part of him with nothing under it comes
  down onto the ground, and his feet only stand on real ground.
- **The Lantern Willow's** honey drips stay on the honey in his mouth as his lip moves, and the drool falls from
  them. When he sleeps, real eyelids slide shut over his eyes.
- **The Gloamwick at small sizes:** his flames, smoke and embers stay where they belong as he moves, and his shake,
  Candlefall and sparks are sized to him.
- The Giants Guide (1.4.17) has the new healing rule for every giant and the right number of Cerberus pelts.

Inside: Pitchgut 1.35.32, Furrowmaw 1.17.29, Cerberus 2.18.29, Hollowbell 1.12.31, Lantern Willow 1.10.32,
Wreckback 1.2.37, Swarmforge 1.4.35, Gloamwick 1.2.16.

## New in 1.6.7

- **New Performance setting: Drawing** (Giants Performance, Detail). It's Fast by default: the same picture, with
  less work for your graphics card. It's much faster for the Swarmforge's robots, the Lantern Willow, and Furrowmaw
  and Pitchgut up close. A giant may show up a few frames late the first time you see him. If anything looks wrong,
  switch to Classic (the old drawing) on the Performance screen. No preset changes it.
- **The Swarmforge's** robot army finds its paths with less work, and the small hitch when his robots settle is
  smaller.
- **The Lantern Willow** costs the game almost nothing when nobody is near him.
- **Hollowbell's** landing cracks now spread over a few ticks instead of freezing the game when he lands.
- **Pitchgut's** footsteps at the edge of loaded land no longer freeze the game.
- The Giants Guide (1.4.16) explains the Drawing setting.

Inside: Pitchgut 1.35.31, Furrowmaw 1.17.28, Cerberus 2.18.28, Hollowbell 1.12.30, Lantern Willow 1.10.31,
Wreckback 1.2.36, Swarmforge 1.4.34, Gloamwick 1.2.15.

## New in 1.6.6

- **No more long freezes.** The giants' moves no longer make the game wait for land that isn't loaded yet. Over land
  nobody has loaded, their footsteps and effects don't load it.
- **Painting a giant's ground**, and painting it again when he comes back, now happen a bit at a time instead of
  freezing the game. Being taken to the Lantern Willow doesn't freeze it either.
- **The Gloamwick's** eclipse, Night Bellow and Snuff Wave no longer freeze the game.
- **The Cerberus** no longer fills your game with graphics errors when he's far away. **Hollowbell's** deep toll is
  easier on the game, and **the Swarmforge's** robots spread out their path finding.
- The Swarmforge's robots and sniper can't see or shoot through land nobody has loaded.
- The tips of **Furrowmaw's** mandibles are solid now when he holds them out wide, so you can stand and sit on them.

Inside: Pitchgut 1.35.30, Furrowmaw 1.17.27, Cerberus 2.18.27, Hollowbell 1.12.29, Lantern Willow 1.10.30,
Wreckback 1.2.35, Swarmforge 1.4.33, Gloamwick 1.2.14.

## New in 1.6.5

- **Sitting on a giant** now works anywhere you can stand on him: right out to his edges, and on the parts of him
  that move, like a stepping leg or a swinging head. Getting up from an edge puts you back on that spot. Your game
  and the server both need 1.6.5 for sitting to work.
- **The Hush** glides smoothly now, with no more hopping. It climbs walls up to 24 blocks high, over the top and down
  the other side.
- **The Gloamwick** only picks a move that can reach you, and walks in closer when none can. His aim follows you
  while he winds up a blow, but settles just before it lands, so a last step aside still dodges it. His embers, wax
  and candles come down where he aims them, his Charge runs at where you stood when he set off, and his Stampede
  sends its biggest runner straight at you.

Inside: Pitchgut 1.35.29, Furrowmaw 1.17.26, Cerberus 2.18.26, Hollowbell 1.12.28, Lantern Willow 1.10.29,
Wreckback 1.2.34, Swarmforge 1.4.32, Gloamwick 1.2.13.

## New in 1.6.4

- **Wreckback** is weaker against other giants on land, and keeps his strength at sea. The server freeze when one
  of his cannonballs ran away is fixed.
- **Boss music** plays only while a giant is fighting you, and it lasts the whole fight.
- **Model detail at 100%** on the Performance screen now always draws every giant in full, however far off he is.
- **Sitting on a giant:** the three crouches now only have to come within two seconds.
- **Riding the Gloamwick** starts you in third person.
- **Hunting:** giants now go after players they can see, and they go for players before mobs. They see further.
  Furrowmaw also hunts by sight when he's on the surface, and the Gloamwick closes in on you. On Peaceful they
  leave players alone.
- **The Hush** moves smoothly and never teleports. It can't see you while you crouch. It walks upright and drops to a
  crawl when it chases you.
- **The Gloamwick's trees** no longer come out cut in half at the edge of his ground or over water (in newly made
  land).
- **Furrowmaw's legs** no longer flip or snap, and he stays solid while he dives and comes back up.
- **Pitchgut's arms** no longer stretch. His Field of Tentacles has a new arm slam that leaves goo, his suck-in pulls
  30% harder, and you never end up stuck in blocks after he coughs you up.
- **Giants Guide 1.4.15:** says two seconds for the triple crouch and explains the new 100% model detail.

Inside: Pitchgut 1.35.28, Furrowmaw 1.17.25, Cerberus 2.18.25, Hollowbell 1.12.27, Lantern Willow 1.10.28,
Wreckback 1.2.33, Swarmforge 1.4.31, Gloamwick 1.2.12.

## New in 1.6.3

- **New fight music for all eight giants.** Each has his own boss track, and it changes as he gets hurt: a darker
  take below half his health and a last-stand take below a quarter.
- **The giants are balanced against each other.** After a full duel test between them, their blows on each other
  were evened out so no one giant wins every fight. Players see no change.
- **Beaten giants run away and heal.** When a giant is losing a wild fight with another giant (one no player
  started), he backs down, gets out of reach and goes home to the middle of his ground, or to whoever holds his book
  if they're in the same world. There he rests for a few minutes, heals up to half his health and starts no fights.
  A giant never runs from a fight a player started: one his book sent him into, one started with `/giants meet`, or
  while somebody rides him.
- **Grudges:** when another giant's helper hurts a giant, he blames that giant. A giant hunts whatever hurt him until
  it dies, stays well away for ten minutes, or you tell him to forget.
- **Sit anywhere on him.** If you hold a giant's book, or you're on the safe list of whoever holds it, stand on any
  part of him and crouch three times quickly: you sit right there and go wherever that part goes, and nothing of his
  hurts you while you sit. Jump to get up. There's also a "Sit on giant" key in Controls, and the Guide's Controls
  tab can turn the triple crouch off. Wild giants can't be sat on.
- **New `/giants` commands for operators:** `flee on|off [giant]` (off: giants fight their wild fights to the end),
  `home <giant>` takes you to the middle of a giant's ground, `health <x>` and `damage <x>` multiply every giant's
  health and damage, `preset easy/normal/hard` sets both, and `heal`, `calm`, `sleep`, `wake`, `freeze`,
  `unfreeze`, `summon` and `info` work for all the giants or just the one you name. `where` is for operators only
  now: players use the Finders. The old `detail` commands are gone; the Performance screen does that.
- **When a giant comes back after dying** on land that was already explored, that land turns into his ground over a
  minute or so (never near spawn or on another giant's ground; land people have lived in only gets his biome).
- **Giants Guide 1.4.14:** the **Giants Performance** screen (`/giants performance`), with presets and every
  giant's effects in one place, and its **Controls** tab with every giant's keys. The `/giants` page has all the new
  commands, and the Guide tells you about backing down, `/giants flee` and sitting anywhere.

Inside: Pitchgut 1.35.25, Furrowmaw 1.17.22, Cerberus 2.18.23, Hollowbell 1.12.25, Lantern Willow 1.10.26,
Wreckback 1.2.31, Swarmforge 1.4.29, Gloamwick 1.2.9.

## New in 1.6.2

- **The Gloamwick 1.2.0: the Hush.** During his Eclipse a new horror creature climbs out of his shadow and hunts
  you by sound. Keep still or sneak to hide from it. It can't be killed, a hit only makes it flicker away for a
  moment, and its bite hits very hard.
- **Giants Guide 1.4.12:** the Gloamwick's Eclipse tells you about the Hush.

Inside: Pitchgut 1.35.14, Furrowmaw 1.17.13, Cerberus 2.18.12, Hollowbell 1.12.16, Lantern Willow 1.10.16,
Wreckback 1.2.19, Swarmforge 1.4.18, Gloamwick 1.2.0.

## New in 1.6.1

- **The Gloamwick 1.1.4:** in a fight with another giant he keeps fighting when it shoves him a long way off from
  you. Before, he could stop dead in the middle of it while the other giant got its health back.

Inside: Pitchgut 1.35.14, Furrowmaw 1.17.13, Cerberus 2.18.12, Hollowbell 1.12.16, Lantern Willow 1.10.16,
Wreckback 1.2.19, Swarmforge 1.4.18, Gloamwick 1.1.4.

## New in 1.6.0

- **Every giant's moves look like what they do.** All of them had an animation and effects pass: real fire, frost,
  water, dust and smoke instead of round puffs and blocks, a wind-up before the big blows, and the ground shakes,
  cracks and flashes where they land. Their feet stand on the ground now, on slabs, paths and snow too, instead of
  sinking in or floating.
  - Pitchgut: real knees that fold as he steps, a scream you can see roll out, a long breath that drags the dust in,
    real drops of goo and thin threads of dark vapour from his holes.
  - Furrowmaw: his lava breath pours out in an arc and leaves pools that cool, his geysers stand up out of glowing
    cracks, thin smoke rises from his vents, and his legs paw the air like a centipede's.
  - Cerberus: flickering flame and frost crystals, tall roaring pillars in the firestorm, rings of shaken air when he
    roars or howls, and ice spikes, cinders, hail and boulders that are real shapes, not blocks.
  - Hollowbell: rings of sound when he tolls, trails behind his strands and arms, goo pouring from his pods, real sun
    lance beams and stingers with a green streak.
  - Lantern Willow: real curling roots bursting out of the ground, eyes that glare in two beams, a lantern nova that
    gathers in his canopy and goes off in rays, and motes, drips and leaves coming off him.
  - Wreckback: wide sheets of spray off his claws, guns that fire with a flash and a bank of powder smoke, splinters
    off his ship, crust and rust flaking off, and water streaming off him.
  - Swarmforge: a roaring furnace blast, a crane slam with a white flash and molten metal, forge smoke and steam,
    lightning over his core, and treads that leave prints.
- **The Gloamwick is redone (1.1).** A new look: long moose legs with knees and hocks, a dark face with fire in his
  eyes, palm antlers with six tines, hide over his ribs and a bell under his throat. He walks, trots and gallops like
  a moose, and every attack winds up and only hurts where his antlers, head or hooves land. His moves show what they
  do: a herd of ghost moose, pillars of soul fire, whips of blue fire, chains bursting out of the ground, real wax
  dripping off his antlers. He has a boss bar and a riding camera, the Gloaming is a dark wood of dark oaks and pale
  ghost trees, his armour and the Gloam Antler are redone, and his new ward, the Gloam Lantern, keeps him 700 blocks
  away for 20 minutes. One sleeps in the Gloaming in every new world, and he fights back when another giant starts on
  him.
- **Fixes:**
  - Furrowmaw no longer freezes or steps out of the world when you're near his tail but far from his head.
  - The Lantern Willow's roots come down where and when they hurt, the same on everyone's screen.
  - The Cerberus no longer stops in the middle of a move when the only one watching is a spectator far off.
  - Pitchgut's sweeping leg, the Hollowbell's strand lash and the Swarmforge's crane swing, tread crush and ram only
    hit what they really pass through.
  - Wreckback: being dragged, flung or dropped by an anchor or its chain never gives fall damage.
  - The Gloamwick: his book reaches him from anywhere, even out of the world, his front hooves stay down when he
    stands still, his legs step round when he spins, and no more yellow glow on the ground under him at night.
- **Giants Guide 1.4.11:** every giant's moves match his own book again, with the Cerberus's triple breath, rift and
  leaping slam, and the Gloamwick's pages have his real size, his new look, his dark wood and the Gloam Lantern.

Inside: Pitchgut 1.35.14, Furrowmaw 1.17.13, Cerberus 2.18.12, Hollowbell 1.12.16, Lantern Willow 1.10.16,
Wreckback 1.2.19, Swarmforge 1.4.18, Gloamwick 1.1.3.

## New in 1.5.0

- **The Gloamwick joins**, JJ's eighth giant: a huge spirit moose of souls, light and darkness, with lantern ribs and
  antlers of melting wax. He puts out lights as he walks, breathes dark mist and lashes blue soul fire. His biome is
  the Gloaming, a forest stuck at dusk with the Antler Hollow in the middle. His gear: Wickbark armour (R: Soul Lash,
  Sneak + R: Soul Form), the Gloam Antler with Reap, the Undying Wick, his Codex and the Finder.
- All the giants know him: each one fights him or keeps away from him when they meet, and their grounds never
  overlap. He fights Pitchgut, the Cerberus, the Lantern Willow and the Swarmforge, and keeps away from the
  Furrowmaw, the Hollowbell and Wreckback.
- The Giants Guide has his pages: who he is, his ground, all his moves, how to fight him, his drops and commands.
  Its Furrowmaw moves list matches his book again.

Inside: Pitchgut 1.35.13, Furrowmaw 1.17.11, Cerberus 2.18.10, Hollowbell 1.12.15, Lantern Willow 1.10.15,
Wreckback 1.2.17, Swarmforge 1.4.16, Gloamwick 1.0.0.

## New in 1.4.51

- Every giant: much easier to hit. A swing at any part of him you can see in reach lands on that part, at any size,
  standing or moving.
- Every giant's book shows how much wind each attack costs: a thin bar under each one, green, yellow or red if he
  hasn't enough wind left.
- Every armour set has two powers now (whole set on): R for fighting and Sneak + R for getting about, finding things
  or making things. Pitchgut: Goo Geyser and All Eyes Open. Furrowmaw: Burrow Strike and Earth Swim. Cerberus:
  Three-Headed Fury and Hellhound's Hunt. Hollowbell: Great Toll and Glass Bell. Lantern Willow: Drowning Roots and
  Angler's Lure. Wreckback: Broadside and Anchor Throw. Swarmforge: War Form and Field Forge. The chestplate and the
  leggings by the hotbar show each wait.
- Every weapon is reworked to be about as strong as the others, each in its own way: the Goo Maw devours, the
  Mandible Blade breaks armour, the Twin Fangs are evened out, the Root Maul heals you and roots, the Anchor Hook
  keelhauls, the Wrench Hammer throws lightning and the Stinger pins with a barbed harpoon.
- Who powers hit: armour powers and weapon powers can hurt other players when the server has PvP on (never you), and
  they leave farm animals and other peaceful animals, villagers, golems and your own pets alone. Holds and stuns last
  half as long on players. The Lantern Willow's roots no longer kill cows and sheep.
- The Giants Guide's Drops pages show the new powers and weapons.

Inside: Pitchgut 1.35.12, Furrowmaw 1.17.10, Cerberus 2.18.9, Hollowbell 1.12.14, Lantern Willow 1.10.14,
Wreckback 1.2.16, Swarmforge 1.4.15.

## New in 1.4.50

- Cerberus: much easier to hit. Aiming at any part of him you can see, in normal reach, now hits him.
- Swarmforge: the Forge armour's helper robot builds itself again after it's destroyed (no iron), and the armour
  power turns it into a big war robot for 30 seconds. The armour is as strong as the best giant set now.
- Every giant: a book in your ender chest is nobody's now, like one in any chest.

Inside: Pitchgut 1.35.8, Furrowmaw 1.17.6, Cerberus 2.18.5, Hollowbell 1.12.10, Lantern Willow 1.10.9, Wreckback 1.2.11,
Swarmforge 1.4.10.

## New in 1.4.49

- Every giant: his fight music starts as soon as you're near him while he's awake, no hit needed, and plays
  without cutting out until a few seconds after you leave.
- Every giant's biome music now plays in creative too, and starts within a second or two of entering.
- The old and the new tracks both stay: each time a fight or biome track starts, it picks one of them.
- Biome background sounds are a little louder again.

Inside: Pitchgut 1.35.7, Furrowmaw 1.17.5, Cerberus 2.18.4, Hollowbell 1.12.9, Lantern Willow 1.10.8, Wreckback 1.2.10,
Swarmforge 1.4.9.

## New in 1.4.48

- Every giant: new fight music, and it now plays whenever you fight him, in creative too (it used to start only
  when he went after a survival player). It holds for a few seconds after the fight instead of cutting out.
- Every giant: new, calmer music in his biome, and calmer biome sounds: quieter, longer background loops and the
  small sounds come much less often.

Inside: Pitchgut 1.35.6, Furrowmaw 1.17.4, Cerberus 2.18.3, Hollowbell 1.12.8, Lantern Willow 1.10.7, Wreckback 1.2.9,
Swarmforge 1.4.8.

## New in 1.4.47

- Every giant: beating him always drops enough of his materials to make all his gear and items, at any size.
  Pitchgut now also drops a goo bucket for the Goo Maw, and Wreckback drops wet logs.

Inside: Pitchgut 1.35.5, Furrowmaw 1.17.3, Cerberus 2.18.2, Hollowbell 1.12.7, Lantern Willow 1.10.6, Wreckback 1.2.8,
Swarmforge 1.4.7.

## New in 1.4.46

- Cerberus: his fire and frost breath come out as thick streams now, from all three mouths in Triple Breath.

Inside: Pitchgut 1.35.4, Furrowmaw 1.17.2, Cerberus 2.18.1, Hollowbell 1.12.6, Lantern Willow 1.10.5, Wreckback 1.2.7,
Swarmforge 1.4.6.

## New in 1.4.45

- Cerberus: his moves land where he aims them on hills and in dips. New moves: Triple Breath, Fire and Frost Rift
  and Leaping Slam, and his howl knocks you back. His moves have sound now, his steps rumble and shake the ground a
  little, and anyone stuck inside him is moved out.

Inside: Pitchgut 1.35.4, Furrowmaw 1.17.2, Cerberus 2.18.0, Hollowbell 1.12.6, Lantern Willow 1.10.5, Wreckback 1.2.7,
Swarmforge 1.4.6.

## New in 1.4.44

- Hollowbell: all eight big arms end the same, in a smooth rounded point. When he catches you, you're shut in one of
  his pods instead of his bell, and you drop free when it pops. His own book cover, fewer hint lines, a soft beat as
  he swims, and anyone stuck inside him is moved out.
- Swarmforge: a player who ends up in the hollow inside him is set down beside him instead of falling.

Inside: Pitchgut 1.35.4, Furrowmaw 1.17.2, Cerberus 2.17.3, Hollowbell 1.12.6, Lantern Willow 1.10.5, Wreckback 1.2.7,
Swarmforge 1.4.6.

## New in 1.4.43

- Swarmforge: his own book cover (a cog with a robot eye), fewer hint lines, a low tread rumble and a small shake as
  he drives, and anyone stuck inside him is moved out.

Inside: Pitchgut 1.35.4, Furrowmaw 1.17.2, Cerberus 2.17.3, Hollowbell 1.12.5, Lantern Willow 1.10.5, Wreckback 1.2.7,
Swarmforge 1.4.5.

## New in 1.4.42

- Furrowmaw: far away he stays in one piece, even where the land is only partly loaded.
- Pitchgut: a low rumble and a small shake as he walks, and a big one far away no longer stands frozen.
- Pitchgut, Furrowmaw, Lantern Willow and Wreckback: anyone stuck inside him is moved out to clear ground straight
  away, and standing on him while he moves no longer makes you slide.

Inside: Pitchgut 1.35.4, Furrowmaw 1.17.2, Cerberus 2.17.3, Hollowbell 1.12.5, Lantern Willow 1.10.5, Wreckback 1.2.7,
Swarmforge 1.4.4.

## New in 1.4.41

- Wreckback: his feelers no longer stretch out far past their own length, and only catch what they really reach.
  A low rumble and a small shake as he walks.
- Lantern Willow: his own book cover (willow strands and lanterns), fewer hint lines, and a low rumble and a small
  shake as he walks.
- Both: anyone who ends up stuck inside him is moved out to clear ground straight away.

Inside: Pitchgut 1.35.3, Furrowmaw 1.17.1, Cerberus 2.17.3, Hollowbell 1.12.5, Lantern Willow 1.10.4, Wreckback 1.2.6,
Swarmforge 1.4.4.

## New in 1.4.40

- Furrowmaw: three new light moves (Snap Lunge, Ring Shudder, Tail Swipe), a low rumble as he crawls, his own book
  cover, and fewer hint lines. His Tail Sweep swings his tail again.
- Cerberus: his own book cover (his three heads) and fewer hint lines.

Inside: Pitchgut 1.35.3, Furrowmaw 1.17.1, Cerberus 2.17.3, Hollowbell 1.12.5, Lantern Willow 1.10.3, Wreckback 1.2.5,
Swarmforge 1.4.4.

## New in 1.4.39

- Pitchgut and Wreckback: fewer hint lines. Their messages say what happened and leave the rest for you to find out.

Inside: Pitchgut 1.35.3, Furrowmaw 1.17.0, Cerberus 2.17.2, Hollowbell 1.12.5, Lantern Willow 1.10.3, Wreckback 1.2.5,
Swarmforge 1.4.4.

## New in 1.4.38

- Cerberus: getting off is smooth, he lifts you off his back and sets you down on clear ground beside him. People on
  his heads stay on through a pounce, his land loads much faster, his heads stop at another giant's side, and only one
  player at a time can have his book.
- Every giant now has the one-book rule: `/<giant> book who` says who has it, `/<giant> book free` (ops) lets it go.

Inside: Pitchgut 1.35.2, Furrowmaw 1.17.0, Cerberus 2.17.2, Hollowbell 1.12.5, Lantern Willow 1.10.3, Wreckback 1.2.4,
Swarmforge 1.4.4.

## New in 1.4.37

- Furrowmaw has new moves: Lava Breath, Furrow Geysers, Burst from Below, Plow Charge, Coil and Crush and Tail Sweep.
  His inhale throws debris and his slam sends out a tremor ring. The small ones (spit, dust, thrash, vent, magma
  storm, tail flick) are gone. Lava he leaves cools back to normal ground after 20 seconds.

Inside: Pitchgut 1.35.2, Furrowmaw 1.17.0, Cerberus 2.17.1, Hollowbell 1.12.5, Lantern Willow 1.10.3, Wreckback 1.2.4,
Swarmforge 1.4.4.

## New in 1.4.36

- Lantern Willow: the hole at the outer sides of his mouth is closed, shut or open. Only one player can have his
  book at a time, even while they're offline.

Inside: Pitchgut 1.35.2, Furrowmaw 1.16.3, Cerberus 2.17.1, Hollowbell 1.12.5, Lantern Willow 1.10.3, Wreckback 1.2.4,
Swarmforge 1.4.4.

## New in 1.4.35

- Hollowbell and the Swarmforge: only one player can have each one's book at a time, even while they're offline.
  `/<giant> book who` says who has it; `/<giant> book free` (ops) lets it go.

Inside: Pitchgut 1.35.2, Furrowmaw 1.16.3, Cerberus 2.17.1, Hollowbell 1.12.5, Lantern Willow 1.10.2, Wreckback 1.2.4,
Swarmforge 1.4.4.

## New in 1.4.34

- Furrowmaw: only one player can have his book at a time, even while they're offline. `/furrowmaw book who` says
  who has it; `/furrowmaw book free` (ops) lets it go.

Inside: Pitchgut 1.35.2, Furrowmaw 1.16.3, Cerberus 2.17.1, Hollowbell 1.12.4, Lantern Willow 1.10.2, Wreckback 1.2.4,
Swarmforge 1.4.3.

## New in 1.4.33

- Pitchgut: only one player can have his book at a time, even while they're offline, like Wreckback's.
  `/pitchgut book who` says who has it; `/pitchgut book free` (ops) lets it go.

Inside: Pitchgut 1.35.2, Furrowmaw 1.16.2, Cerberus 2.17.1, Hollowbell 1.12.4, Lantern Willow 1.10.2, Wreckback 1.2.4,
Swarmforge 1.4.3.

## New in 1.4.32

- Wreckback: only one player can have his book at a time, even while they're offline. Anyone else is told who has
  it, and any other copy goes quiet. `/wreckback book who` says who has it; `/wreckback book free` (ops) lets it go.
  The other giants get the same rule next.

Inside: Pitchgut 1.35.1, Furrowmaw 1.16.2, Cerberus 2.17.1, Hollowbell 1.12.4, Lantern Willow 1.10.2, Wreckback 1.2.4,
Swarmforge 1.4.3.

## New in 1.4.31

- Getting down from Pitchgut and the Swarmforge: you're carried smoothly all the way to the ground, with no more
  hanging in the air and then a jump.
- Pitchgut's screen shake is much gentler: one shake per blow, it fades fast and gets weaker farther from him.
- The Swarmforge's robots get round him and reach you instead of wandering back to him.
- Teleporting to the Hollowbell, Wreckback or the Swarmforge, or walking into their land, loads a lot faster.

Inside: Pitchgut 1.35.1, Furrowmaw 1.16.2, Cerberus 2.17.1, Hollowbell 1.12.4, Lantern Willow 1.10.2, Wreckback 1.2.3,
Swarmforge 1.4.3.

## New in 1.4.30

- Lantern Willow: getting down, you ride his lure smoothly all the way to the ground (no more hanging in the air and
  then a jump). Teleporting to him or walking into his marsh no longer freezes the game while his land loads.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.2, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.2, Wreckback 1.2.2,
Swarmforge 1.4.2.

## New in 1.4.29

- Furrowmaw: getting down, he carries you smoothly all the way to the ground (no more hanging in the air and then a
  jump), lets you off a few steps clear of him, and keeps still a moment so his next move doesn't catch you.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.2, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.2.2,
Swarmforge 1.4.2.

## New in 1.4.28

- Wreckback: the kelp on his sides hangs in whole strands, the posts on his rail stay with the hull (no more floating
  ones), and his mirrored feelers are joined on properly at the root.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.1, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.2.2,
Swarmforge 1.4.2.

## New in 1.4.27

- Wreckback: a smaller, tidier jar. Nothing changes in game.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.1, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.2.1,
Swarmforge 1.4.2.

## New in 1.4.26

- Wreckback: in his mast sweep the mainmast comes down onto the ground instead of into it, his small claw's snip lands
  where it's aimed, and his gaze and pincer crush shake less.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.1, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.2.0,
Swarmforge 1.4.2.

## New in 1.4.25

- Wreckback: far away he keeps his face, you can stand on even a tiny one, `/giants paint stop` stops his paint,
  people he holds or carries into another world are let go there, and his replies match the other giants.
- Furrowmaw: a smaller, tidier jar. Nothing changes in game.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.1, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.1.2,
Swarmforge 1.4.2.

## New in 1.4.24

- Hollowbell: "Follow me" in his book, like the Lantern Willow's. He keeps just past his rim from you.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.0, Cerberus 2.17.1, Hollowbell 1.12.3, Lantern Willow 1.10.1, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.23

- Cerberus and Lantern Willow: smaller, tidier jars and replies that match the other giants. Far away, the Lantern
  Willow's broken lures show. Nothing else changes in game.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.0, Cerberus 2.17.1, Hollowbell 1.12.2, Lantern Willow 1.10.1, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.22

- Hollowbell: his strands stay whole curves up close and in his moves, and far away he no longer turns into a solid
  ball. Riding his arm, the swim beat doesn't throw you off. Press V with the book to send him after what you look
  at, and his replies match the other giants.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.0, Cerberus 2.17.0, Hollowbell 1.12.2, Lantern Willow 1.10.0, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.21

- Furrowmaw: a big one no longer sinks out of the world, standing on him works at every size, `ride` again sets you
  down beside him, and no more see-through square on your screen after riding him. He has `help`, `wake`, `paint`,
  `heavydelay` and `detail <distance>` like the others, and his replies match theirs.

Inside: Pitchgut 1.35.0, Furrowmaw 1.16.0, Cerberus 2.17.0, Hollowbell 1.12.1, Lantern Willow 1.10.0, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.20

- Pitchgut: getting down, his hand lowers you, lets go and lifts away, and you land on clear ground beside him.
  "Follow me" is in his book, `kill` reaches every world, his replies match the other giants, and `/giants list`
  shows where he really is.

Inside: Pitchgut 1.35.0, Furrowmaw 1.15.0, Cerberus 2.17.0, Hollowbell 1.12.1, Lantern Willow 1.10.0, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.19

- Swarmforge: getting out of his cab puts you on the ground beside him with no fall, remove and kill also reach the
  one waiting in his Scrapyard, his crane slam no longer shoots up like a pole, and his replies match the others.

Inside: Pitchgut 1.34.2, Furrowmaw 1.15.0, Cerberus 2.17.0, Hollowbell 1.12.1, Lantern Willow 1.10.0, Wreckback 1.1.1,
Swarmforge 1.4.2.

## New in 1.4.18

- Lantern Willow: getting down, his angler lure lowers you to clear ground beside him. No more getting stuck under
  him or inside his trunk. "Follow me" is in his book, and his light blows land with more weight.

Inside: Pitchgut 1.34.2, Furrowmaw 1.15.0, Cerberus 2.17.0, Hollowbell 1.12.1, Lantern Willow 1.10.0, Wreckback 1.1.1,
Swarmforge 1.4.1.

## New in 1.4.17

- `/giants paint stop` stops every giant's paint. It works from the server console too.
- Cerberus: "Put me down" sets you on clear ground beside him, no more see-through square on your screen after
  riding him, and `/cerberus detail <distance>`.
- Hollowbell: a smaller jar, nothing changes in game.

Inside: Pitchgut 1.34.2, Furrowmaw 1.15.0, Cerberus 2.17.0, Hollowbell 1.12.1, Lantern Willow 1.9.1, Wreckback 1.1.1,
Swarmforge 1.4.1.

## New in 1.4.16

- Swarmforge: `/giants paint stop` stops his paint, in the Nether he stands on the floor, and giants close together
  cost less to work out.

Inside: Pitchgut 1.34.2, Furrowmaw 1.15.0, Cerberus 2.16.0, Hollowbell 1.12.0, Lantern Willow 1.9.1, Wreckback 1.1.1,
Swarmforge 1.4.1.

## New in 1.4.15

- Pitchgut: `/giants paint stop` stops his paint, his goo trail follows the mob griefing rule, and in the Nether he
  stays on the floor instead of the roof.

Inside: Pitchgut 1.34.2, Furrowmaw 1.15.0, Cerberus 2.16.0, Hollowbell 1.12.0, Lantern Willow 1.9.1, Wreckback 1.1.1,
Swarmforge 1.4.0.

## New in 1.4.14

- Lantern Willow: Lantern Willows in unloaded land count toward the limit, he no longer reels in another giant's
  rider, and damaged save numbers load safely.

Inside: Pitchgut 1.34.1, Furrowmaw 1.15.0, Cerberus 2.16.0, Hollowbell 1.12.0, Lantern Willow 1.9.1, Wreckback 1.1.1,
Swarmforge 1.4.0.

## New in 1.4.13

- Furrowmaw is solid: you can stand on him and ride him, you never get stuck inside him, and giants push apart.
  A sleeping one far away is drawn whole.

Inside: Pitchgut 1.34.1, Furrowmaw 1.15.0, Cerberus 2.16.0, Hollowbell 1.12.0, Lantern Willow 1.9.0, Wreckback 1.1.1,
Swarmforge 1.4.0.

## New in 1.4.12

- Hollowbell: new timing for his drop, slam, pulse and sweep, one shake per blow, a proper death landing, and his
  glow beats with the bell.
- Cerberus: his mane stays outside his body, heavy moves wait for the first minute of a fight
  (`/giants heavydelay` changes it), better tail sweep, pounce, stomp and death, and the book can forgive.

Inside: Pitchgut 1.34.1, Furrowmaw 1.14.8, Cerberus 2.16.0, Hollowbell 1.12.0, Lantern Willow 1.9.0, Wreckback 1.1.1,
Swarmforge 1.4.0.

## New in 1.4.11

- Pitchgut and the Cerberus work together again (with the newest single jars the game crashed on start).
- Hollowbell: clean big-arm ends, no height limit. Wreckback: feet stay planted, no floating claw spines.
- Swarmforge: robots and Commander orders survive a reload, the book's "Where is he?" and send-to-spot are back.
- Lantern Willow: better impacts, look key, /lanternwillow help.

Inside: Pitchgut 1.34.1, Furrowmaw 1.14.8, Cerberus 2.15.2, Hollowbell 1.11.1, Lantern Willow 1.9.0, Wreckback 1.1.1,
Swarmforge 1.4.0.

## New in 1.4.10

- Lots of bug fixes in every giant (spawn eggs, thrown tridents, held mobs on save, the Nether roof, kill credit).
- Heavy moves wait a minute into a fight. Change it with `/giants heavydelay <seconds>`.
- The Hollowbell moves like before again, with even small tentacles and lights that glow from every side.

Inside: Pitchgut 1.34.0, Furrowmaw 1.14.8, Cerberus 2.15.0, Hollowbell 1.11.0, Lantern Willow 1.8.3, Wreckback 1.0.8,
Swarmforge 1.3.2.

## New in 1.4.9

- Pitchgut, the Cerberus, the Hollowbell and the Lantern Willow are solid now: you bump into them, stand on them and
  ride along.
- Standing on a Hollowbell pod or a Willow limb tip no longer throws you off.
- The Willow's leaves don't go through his trunk, and you can't get stuck inside it.
- Smaller Wreckback jar, nothing changes in game.

Inside: Pitchgut 1.33.0, Furrowmaw 1.14.8, Cerberus 2.15.0, Hollowbell 1.10.1, Lantern Willow 1.8.2, Wreckback 1.0.7,
Swarmforge 1.3.1.

## New in 1.4.8

- **Every giant can be found, wherever he is.** One summoned far from everyone, hatched from an egg, or left where
  nobody is near is written down where he lies when his land is put away, and also when the server stops. `/giants
  where`, `list`, `tp`, the finders and each giant's own where and list find him there, also after a restart.
- **A summon far out works.** His land is held until it's made, and a few seconds more, so he takes his first steps
  there. Before, most giants summoned far out were never found again (only the Hollowbell was).
- **No more freeze or crash on a tp to land that isn't made yet.** Every tp, `/giants tp` and each giant's own (the
  Willow's rise spot too), says "Getting the land ready…" and takes you there once it's ready. If it still isn't
  ready after a minute (a very busy server), you stay where you are and the chat says so. Before, a tp there could
  stop the server for 15 seconds or more, and the Swarmforge's could crash it.
- **The Hollowbell's strands don't whip when a move is cut off** halfway by another one.
- **The Swarmforge 1.3.0 and 1.3.1** are in: he and his big robots are solid, giants don't walk into each other, "go
  after what I look at" from anywhere, and a slow heal.

## New in 1.4.7

- **"Go after what I look at" works from far off.** Pitchgut, Furrowmaw, the Cerberus, the Hollowbell and the Lantern
  Willow (and Wreckback): look at something up to 512 blocks away and press that order in his book. He goes after it
  even if he is far away, asleep or out of the world, and keeps at it until it dies, it's lost for 30 seconds or you
  give a new order. A part of another giant counts as that giant. It glows for a moment, only for you, and the chat
  says why when he can't.
- **A very slow heal.** Those six heal 1% of their health a minute on their own, and nothing for 15 seconds after any
  hurt, so in a fight it hardly counts (Wreckback 2% a minute in deep water). Settings `passiveHeal` and
  `passiveHealPerMinute`.
- **The Willow's Drink the land doesn't heal him any more** (and grows no lures back). It's just an attack now.
- **Wreckback is solid all over.** You can stand on any part of him and ride it, nobody ends up inside him, and
  giants don't go through him when they fight or walk into him.
- **Fewer server freezes.** A giant comes back into the world only once his land is loaded, a tp to land not made yet
  takes you there the moment it's ready, and summoning one far out no longer stops the server.
- **The Willow's left eye is whole again**, with no see-through hole when he blinks.
- The Giants Guide says all this on each giant's pages.

## New in 1.4.6

- **The Swarmforge 1.2.1 and 1.2.2**: no more server freeze when you go to his Scrapyard; his robots don't vanish
  any more when you use Commander Mode or he steps out of the world; auto-stow is off unless you turn it on; a build
  queue (click a type as often as you like, he builds six at once, only that bay's door opens); robots never stuck in
  the ground and they walk round him; Commander Mode turns the view with the cursor at the screen's edge, Q/E and more,
  goes anywhere he's loaded (no 750 block limit) and only ends for a real reason; driving a robot has its own screen
  and F5 for its eyes or behind it; and a simpler book with a Build grid. The Guide's Swarmforge pages say so.

## New in 1.4.5

- **The Swarmforge 1.2.0**: army points instead of a robot count (each type costs points, 150 in all, freed when a
  robot goes), recycling up his stow ramp, nobody gets stuck inside him, Commander Mode like a strategy game (move,
  attack-move, patrol, formations, groups, build keys, pings, a unit card, alerts, a camera that doesn't go through
  blocks), an icon and a button for every robot type and unit move, a glow outline only you see, and his book fits at
  every GUI size. The Guide's Swarmforge pages say so.

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
