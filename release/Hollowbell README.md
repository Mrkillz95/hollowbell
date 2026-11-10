# Hollowbell (Java mod), version 1.12.43

My KillzAI Hollowbell build, turned into a boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish
made of glass, copper and bone, about 200 blocks wide and 200 tall at full size, and he swims through the air.

He's made from my build block for block, with every block's real texture. The glass is see-through, so you can look
into his hollow dome and see what he's caught.

## What's new

- **Tiny giants** (size 0.01 to 0.1): you can always hit him with a sword, an arrow or a trident, and he never flings, traps or smothers you or your animals.
- His moves, particles, sounds and screen shake now shrink with him at every size, and his minions do too.
- Health, damage, armour and speed follow one size rule shared by all eight giants. At size 0.01 every giant has 60 health (the Swarmforge 40).
- The Guide's **go** now beats his keeping away from another giant. In minigame worlds and scripted adventures giants never hold meetings.
- When he's small he flies a little slower now, so he never outruns Cerberus, the fastest giant. From about a third of his size up he's as fast as before.

### 1.12.42

- **Less lag in minigames** (in the all-in-one jar): a minigame's world loads only its own small area, which saves a
  lot of memory. If the game has too little memory for the giants, a chat note says how to give it more (launcher:
  Installations, the Fabric profile, More options, JVM arguments: change `-Xmx2G` to `-Xmx6G`).
- **The Wreck Cove Rescue** (in the all-in-one jar): the map's giants never fight each other, the lost giant stays
  calm and walks the shore road home with you (new `escort` script step), and the guard only goes for players who
  wade out into the lagoon, not for those reaching the lost giant on his beach.
- Admin book layout fixes: the Editor's custom set, the Swarmforge's robot switches, and question buttons that wrap.

### 1.12.41

- **Adventure maps** in the Giants Admin Book (in the all-in-one jar): admins can script giants (walk a path, guard,
  fight, talk), run commands when something happens to a giant, and save a part of the world as an adventure map to
  play as a minigame. Comes with one ready-made map, The Wreck Cove Rescue. Win any adventure map for the new
  **Adventurer** challenge.
- **Giant editor** (in the all-in-one jar): an admin can dress Hollowbell in another look (Frost, Gold, Shadow,
  Festive, Ancient stone, and new Ember and Crystal, or a custom set). Only how he looks changes. His Bellings wear
  his look too. Followers that gather round him take the look's colour. Frost is lighter now (icy white and pale
  blue).
- **Sound and music picker** and **share codes** (in the all-in-one jar): change his fight music, ground sounds and
  voice pitch; share a preset as a short code that works offline.
- After a staged fight is stopped or ends, both fighters calm down for a minute and walk home: they no longer go
  after players standing near the fight.

### 1.12.40

- **Minigames** in the Giants Admin Book (in the all-in-one jar): an admin can start rounds of eleven minigames
  (Giant Royale, Boss Rush, Siege, Escort, Legends, Possession, Rival Tamers, Bounty Board, Kaiju War, One Life and
  Speedrun), each played in a world of its own that's thrown away after, and every player gets their things back.
  The Hollowbell takes part; minigame giants never count toward the world's own.
- **Staged fights and giant-fight settings** (in the all-in-one jar): an admin can script a fight between giants
  (who wins, how it ends, rounds, bets, a countdown). The Hollowbell's finisher is his sky dive. If the world switched that move
  off, he finishes with his next big move that's still on. New settings decide how ordinary giant fights go; as
  shipped they go exactly as before.
- Fewer boss bars: while a staged fight is on you see only the fight's one bar, not his own bars too, and in a
  minigame only the nearest giant's bars show.

### 1.12.39

- More new systems in the Giants Admin Book (in the all-in-one jar; every one is off until an admin turns it on):
  **villagers** can flee from him, hide, or gather round him while he's calm and leave him bread, and **iron
  golems** go for him when he's wild; he can gather a band of his own **followers** (phantoms) that fight beside him
  and drop nothing; bring his own **weather** to the land round him (a storm: rain, thunder, lightning and sparks of
  his glow under a bell-teal sky), seen only by players near him; live **below**, in the Nether or the End, sized to
  fit; make his ground **sacred** (no building or breaking there while he lives); **learn** the tactic players lean
  on (melee, arrows, magic or blasts) so it hurts him less; and have his size, mood and moves **shuffled** every day
  (Random chaos).
- Follow me: he now stops a little further back, clear of the arms and strands that swing out past his rim. Over a
  lava lake under the Nether's roof he keeps his height instead of jumping onto the roof.
- When he falls (with corpses or skeletons on), his remains are now made from his own body: a corpse that rots away
  in stages, or his own skeleton, left for good.
- The ends of his arms fit the ground better, and a rider's press up lifts him sooner.

### 1.12.38

- Follow me (JJ's answers): he sets off after you once you're a little past his body and stops just clear of it,
  the same rule for every giant, so a small one keeps close and a big one stays proportionally further back.
- New systems in the Giants Admin Book (in the all-in-one jar; every one is off until an admin turns it on): **weak
  points** on his body (the glowing spots on his bell and his crown): hits there do more, and a glint shows where
  while he fights. Through the Guide he can also **grow** with the days and his wins, get **hungry**, be **loyal**
  to his owner (or turn on them), leave a **corpse or skeleton** where he falls, get stronger with more players,
  **herd** with others of his kind, feel the **seasons** and the **full moon**, and the blocks he breaks can **grow
  back** (then they drop nothing).
- Giants in each other's grounds (an admin setting, off unless an admin or a preset turns it on): the world's own
  Hollowbell may come in another giant's ground where he can live (on land), with his companions beside him. A
  dimension set to "no giants" now also keeps him from coming back there until it's lifted.

### 1.12.37

- Server tools in the Giants Admin Book (in the all-in-one jar): the Respawn timers page shows when the world's
  own Hollowbell comes back, and an admin can bring him back now or set the wait (`/giants admin respawn hollowbell now`,
  `... 3d`). Pause all giants stops him where he stands until it's turned off.
- Admin fun (in the all-in-one jar): an admin can play as him with no time limit, stage a fight with him, give
  him a name and title that shows on his boss bar, his own loot, and a giant tag with its own health,
  damage and speed. He can be moved to live somewhere else on land, or in the sky, and the biome brush paints his ground
  wherever it points. Left alone, nothing about him changes.

### 1.12.36

- New setting for the Giants Admin Book (in the all-in-one jar): Meeting other giants, what he does on meeting
  a giant of another kind when no player sent either: decide (as always), fight, run away or ignore.
  `/hollowbell config meetGiants fight` works too. Follow me fixed: a following Hollowbell lay down after two quiet minutes, and lying down ended the follow. A follower now stays busy.
- The Giants Guide's player settings (in the all-in-one jar) reach him: sitting on, riding and standing on him,
  his armour powers, boss bar, fight music, chat lines, his book, and "no attack orders", which now also refuses
  every attack move used from his crown (Grab and Harvest count as attacks); the guardian mood still works. Per-player rules too: a player who may not
  own giants can't hold his book, and an ignored player is never his target.
- My Giant: a player who picks him is taken to his ground with his finder and their own book of him, and one of
  him comes to them as their own: he answers only their book. Two players can each have their own, up to Most at
  once; at that limit the next one shares the holder's book and is told why. If their one falls, their book goes
  quiet. With My Giant off, the one-book rule is exactly as before.

### 1.12.35

- Ten new behaviour settings for the Giants Admin Book (in the all-in-one jar): Size spread (each one that
  comes on his own at a random size in a range), How many come (up to 8 of him at once), Comes back after he
  falls, Temper (peaceful, normal or hunter), Roaming (home, wander the world or stay put), Seeks out other giants,
  Raids (villages and players), Sleeps (as usual, by day or never), Strength at night and Loot. They drive the
  new presets: Pocket Giants, Titans, Gentle Giants, Kaiju War, Night Terrors, Wild Sizes, Siege, One Life, Boss
  Rush, Nomads, Hardcore, Builder / Showcase, Public Server Safe, Lite and Speedrun. `/hollowbell config` now takes
  the choice settings too (temper, roam, sleepSchedule) and spawnSizeMin/spawnSizeMax. Left as shipped,
  nothing about him changes.
- He can now be made as small as size 0.01, about 2 blocks tall. Below size 0.03 he carries, grabs and
  harvests nothing and can't be ridden.

### 1.12.34

- Four new world settings for the Giants Admin Book (in the all-in-one jar), also on the Create World
  screen's new Giants… button: how near and how far from spawn his ground may begin (when the world is made;
  as shipped 2,100 to 14,100 blocks), the room kept between his ground and every other giant's, and how many
  copies of his ground the world has (both only for land not made yet). Left as shipped, worlds are made exactly
  as before.

### 1.12.33

- The Giants Admin Book (operators, `/giants adminbook`, in the all-in-one jar) can now control him live: its Live
  control page lists every one of him the world knows (where he is, his health, size and mood, whether he's asleep
  or fighting, out of the world or in land nobody has loaded, and who holds his book), and can teleport you to one,
  heal him, change his size, put him to sleep, wake him, calm him, forgive his grudges, give his book to a player
  online, send him home, remove him or kill him. Only that one is touched. Summon here runs his own summon command
  as you. The world's own one lying in land nobody has loaded can't be sent home, removed or killed from there:
  teleport to him first.

### 1.12.32

- Operators can change his settings for just this world when the Giants Guide is installed (it's in the
  all-in-one jar): `/giants admin list hollowbell` shows every one (spawning, stats, behaviour, each of his moves on or
  off with its wait and wind cost, healing, taming, effects and server load), `/giants admin set hollowbell <setting>
  <value>` changes one, and `get`, `reset` and `log` show them, put them back and list who changed what. Settings
  marked "only affects new spawns" take hold the next time he comes; "only affects new land" ones only on land not
  made yet. His settings file stays the server default for every other world.
- Every command that changes one of his settings (`/hollowbell damage`, `/hollowbell heavydelay`, `/hollowbell config`,
  `/giants health` and the rest) takes `world` or `allworlds` at the end. Nothing said means this world, and the
  reply says which. Without the Giants Guide, nothing said still changes the server default, as before, and the
  reply says so. His book's operator switches (Break blocks and Harvest) follow the same rule.
- His stinger shots are called Stinger instead of showing a code.

### 1.12.31

- New healing rule, the same for every giant: he heals while he sleeps, and once he's been out of a fight for five
  minutes (nothing has hurt him, he has hurt nothing and he is hunting nothing): 2% of his health a minute over the
  Bell Hollows, 1% anywhere else. Nothing else heals him on his own, not even resting after he backs down. Settings:
  `passiveHeal`, `healHomePerMinute`, `healAwayPerMinute`, `healPauseMinutes`.
- His finder and his book now show up in your recipe book: the finder once you pick up a compass or any of its other
  items, the book once you pick up something of his that goes in it.

### 1.12.30

- The cracks he leaves when he lands now spread over a few ticks instead of freezing the game.
- New Drawing setting (Giants Performance, Detail): Fast, the default, draws the same picture with less work for
  your graphics card. He may show up a few frames late the first time you see him. If anything looks wrong, switch
  to Classic, the old drawing.

### 1.12.29

- No more long freezes: his moves no longer make the game wait for land that isn't loaded yet. Over land
  nobody has loaded, his footsteps and effects don't load it.
- Painting his ground, and painting it again when he comes back, now happen a bit at a time instead of
  freezing the game.
- His deep toll is easier on the game.

### 1.12.28

- Sitting on him now works anywhere you can stand on him: right out to his edges, and on the parts of him that
  move, like a stepping leg or a swinging head. Getting up from an edge puts you back on that spot.
- Your game and the server both need this version of him for sitting to work.

### 1.12.27

- Hunting, he now goes after any player nearby who isn't safe and who he can see, even while he's busy with a
  monster. A wall or a hill between you hides you; a block or two right next to you doesn't. Once he loses sight of
  you he keeps after you for about ten seconds. Someone who hurt him he still hunts, seen or not.
- On Peaceful he leaves every player alone.
- He notices players from further off now, well past the reach of his own blows.

### 1.12.26

- Model detail at 100% now always draws him in full, however far off.
- His fight music now plays only while he's fighting you: he's after you, one of you hit the other, or you ride or sit
  on a giant that's fighting him. Then it plays through the whole fight, pauses and all, and ends when he dies, forgives
  you, backs down, falls asleep, or you die or leave. A calm or wandering giant nearby stays quiet.
- Sit anywhere: the three crouches now have two seconds, not one.

### 1.12.25

- Sit anywhere on him. If you hold his book, or you're on the safe list of whoever holds it, stand on any part of him
  you can stand on (the top of his bell, its rim, an arm) and crouch three times quickly, within two seconds: you sit
  down right there. You go wherever that part of him goes, through his walking, turning and every move, you don't fall
  off, and nothing of his hurts you while you sit. Jump to get up: you stand on the same spot, or you're set down on
  the ground beside him if there's no room there now. Crouching doesn't get you up. Wild giants can't be sat on.
- New key in Controls: "Sit on giant" (not set to start with) sits you down or gets you up, like the crouches. The
  Giants Guide's Controls tab can turn the triple crouch off.

### 1.12.24

- New boss fight music that changes as he gets hurt: a darker take below half his health and a last-stand take below a
  quarter. He also has his own special track, heavier, that plays while he's sunk to the ground.

### 1.12.23

- When he's losing a wild fight with another giant (one no player started), he backs down, climbs up out of reach and
  flies home to the middle of the Bell Hollows (or to whoever holds his book, if they're in the same world), up to
  about 1,500 blocks away. There he rests for a few minutes and heals a little, up to half his health, and starts no
  fights. He never backs down from a fight a player started: one his book sent him into, one started with
  `/giants meet`, or while somebody rides him. New for operators: `/giants flee on|off [giant]` (off: giants fight
  their wild fights to the end; on its own it shows each giant's setting).

### 1.12.22

- Fights between giants re-balanced after a full duel test: other giants' blows hurt him a bit more.
  Players see no change.

### 1.12.21

1.12.21: When he backs down from a fight with another giant, he now really gets away: he climbs high up out of its reach
  first, then flies off fast. Nothing of his (spores, stingers, eggs, bellings) keeps hurting a giant that has
  backed down. Other giants' blows hurt him a bit more (players see no change). When another
  giant's helper hurts him, he blames that giant. He no longer locks up a server when another mod puts a giant
  somewhere impossible.

### 1.12.20

1.12.20: New `/giants` commands for operators: `home <giant>` takes you to the middle of a giant's ground,
`health <x>` and `damage <x>` multiply the giants' health and damage (1.2 is a fifth more), `preset
easy/normal/hard` sets both, and `heal`, `calm`, `sleep`, `wake`, `freeze`, `unfreeze`, `summon` and `info`
work for all the giants or just the one you name. `/hollowbell detail` is gone: the Giants Guide's Performance
screen does that now. When he comes back after dying, his new ground keeps clear of the other giants' grounds. With
the Belling count turned right down on the Performance screen he still sheds at least one belling.

1.12.19: He's a bit weaker against the other giants: their blows hurt him more, at most 3 of his arms land on one
giant in an Arm Storm, and stray stingers and eggs no longer hit giants he isn't fighting. When he comes back on land
that was already explored, that land turns into his ground over a minute or so (never near spawn or on another
giant's ground; land people have lived in only gets his biome). Works with the Giants Guide's Performance screen.

1.12.18: More of his moves can be heard: the sweep creaks, rushes and tears up the ground, the curtain snaps shut,
the pulse squeezes, the drop roars down, and the sun lances hiss and crackle where they burn. The Great Toll lights
up the bell glass as you hang, and lands with a flash, a thud and flying dust; the Glass Bell flashes down round you
and shatters into glinting shards; the Stinger's barb spurts green poison.

1.12.17: The strand sweep only hits what its strands drag through: he sinks low and drags them across the ground.
The arm wrap reaches down for you, hoists you out from under him, squeezes green venom out of you while you
struggle, and drops you. Stingers are bigger with a bright green streak you can see from across a fight. He keeps
his grudges: whatever hurt him he hunts again when it's back in reach, through hiding, reloads and him stepping out
of the world, until it dies, stays well away for ten minutes, or you tell him to forget. He picks moves that can
land from where you are. The next one comes down within 10,000 blocks of where the last died, after 3 days; if he
was yours, after 15 days, and your book burns (the next player carrying a copy gets it). `/hollowbell where` needs
cheats now: his Finder shows the way.

1.12.16: His moves look like what they do: rings of sound when he tolls, trails behind his swinging strands and
arms, goo pouring from his pods, real sun lance beams, dust swirling in under the whirlpool and the undertow, and a
flash that bursts out of him. Stingers fly with a green streak. The strand lash only hits what its strand passes
through. His strand ends and arms lie on the ground instead of sinking into it, on slabs, paths and snow too.

1.12.15: Knows the Gloamwick: he fights or keeps away from him, and their grounds never overlap.

1.12.14: the bell glass powers and the Stinger can now hit other players when PvP is on, but never farm
animals, villagers, golems or your own pets; stuns and pins last half as long on players. With PvP on the Glass
Bell keeps other players out too.

1.12.13: the bell glass armour has two new powers (whole set on). **R: Great Toll** (every 12 seconds): on the
ground you rise, hang and slam down. A ring goes out 10 blocks: 8 to 14 damage, and everything it hits is stunned
for 3 seconds (it can't move or hit). A second, softer ring a second later does about half and pushes them back
out. In the air R still glides. **Sneak + R: Glass Bell** (every 2 minutes): a glass dome closes over you for 20
seconds. Creatures are pushed out of it, nothing shot or thrown gets through, and under water you breathe inside
it. You can still hit and shoot out of it. The chestplate and the leggings by the hotbar show each wait.

The Stinger is reworked: a hit gives Poison II and slows. Its throw is now a barbed harpoon, up to 36 blocks,
every 6 seconds: what it hits takes 8, is pinned where it stands for 3 seconds (half on players, never giants or
bosses) and gets Poison III. It no longer drags things to you. Sneak as you throw and it pulls you to where it stuck.

The book shows how much wind each attack costs: a thin bar under each one, green, yellow or red if he hasn't enough
wind left.

1.12.11: much easier to hit: swing at any part of him you can see in reach, his long arms and strands too, and it
lands on that part, at any size, still or drifting.

## Install

You need Fabric Loader 0.19.5 or newer for 1.21.1 and Fabric API. Put `hollowbell-1.12.19.jar` in your `mods` folder
and take any older Hollowbell jar out. For a server, put the same jar in the server's `mods` folder too. Everyone who
joins needs it as well.

It works next to my other giants, or use the all-in-one jar instead.

## Getting him

The world puts one out on its own, far from spawn, in his own biome, the Bell Hollows. Craft a Finder of the
Hollowbell (glass, an amethyst shard and a compass) and it points the way.

There are spawn eggs in creative (calm, hunting, guardian and small), or use `/hollowbell summon hunting`. Add a size
from 0.03 to 2 for a smaller or bigger one, like `/hollowbell summon calm 0.3`.

Calm ones leave you alone until you hit them. Hunting ones go after any player. Guardians stay near where they were put
down and go after anything that comes close.

## Fighting him

Pop his pods first. They hang low among his strands, and once a third of them are gone he sinks to the ground for a
while. If a strand grabs you, hit it and it lets go. When a red message comes up, a heavy move is coming, so get well
out from under him. Right after a heavy move he's worn out for a few seconds, and that's the time to hit him.

He heals while he sleeps, and once he's been out of a fight for five minutes: 2% of his health a minute over the
Bell Hollows, 1% anywhere else. In a fight he doesn't heal at all.

When he dies, his loot waits in a Loot Cache on a small pedestal where he fell, with a beam over it you can see from
far off. Inside: the Stinger (a whip-like sword you can also throw like a harpoon), bell glass for armour, his pods,
his crown and blocks from his body.

## The book

Craft the Hollowbell Codex from a pod, three bell glass and a book. Carry it and the nearest Hollowbell within 600
blocks is yours: he leaves you alone, drifts where you point and grabs what you point at. Right-click to open it.
With the book on you, press V (the middle mouse button while you ride him) to send him after what you're looking at.
If your Hollowbell is killed, your book burns.

## Main commands (cheats on)

| Command | What it does |
|---|---|
| `/hollowbell summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/hollowbell do <move> [target]` | the nearest one does that move |
| `/hollowbell where` | where every one is |
| `/hollowbell tp [number]` | takes you to him |
| `/hollowbell come [player]` | he comes to you, wherever he is |
| `/hollowbell ride` | puts you on top of him, or takes you off |
| `/hollowbell kill` / `remove` | kills them (with loot) or removes them (no loot) |
| `/hollowbell config [setting] [value]` | lists, shows or changes a setting |
| `/giants ...` | the same kind of commands for all my giants at once (`home`, `health`, `damage`, `preset`, `heal`, `calm`, `info` and more) |

The settings are in `config/hollowbell.json`. `/hollowbell config` lists them all.
