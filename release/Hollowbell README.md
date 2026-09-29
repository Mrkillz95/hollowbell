# Hollowbell (Java mod) — version 1.7.1

Your KillzAI Hollowbell build turned into a real boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish, about 200 blocks wide and 200 tall at full size, and he swims through the air.

He's made from your build block for block. Every block wears its real texture, and the glass is see-through in its own colours, so you can look into the hollow dome and see what he's caught.

## What's new in 1.7.1

- Fixed the Armour power key (R) not working when other giants' mods are in too. They all use R, and now it works for whichever set you're wearing.

## What was new in 1.7.0

- **His loot waits for you where he fell.** When he dies, a small pale pedestal of his blocks is built on the ground there, 7 blocks across, with glowing posts at the corners. In the middle sits his **Loot Cache**, a chest that holds everything he dropped, so nothing falls in lava or despawns. A beam of light stands up out of it like a beacon's, so you can find it from far off. The beam goes out once the cache is empty. The pedestal stays as a trophy. Break the cache and what's in it drops.
- **The Stinger is a harpoon now.** Hold right-click and let go to throw it on its strand. A small thing it hits (a mob, an animal, a player) is poisoned and pulled to you. A wall, the ground, something big or the Hollowbell himself, and you're the one pulled in. Sneak as you let go to always pull yourself. It never leaves your hand, and it waits 2.5 seconds between throws. A normal hit still poisons and slows.
- **Bell glass armour is 3D and alive.** Bone ribs, a low glass dome and a glowing knob on the helmet, with two short strands down the back. Bone ribs and a glowing spot on the chest, two glass pods on the back, bone caps on the shoulders with tendrils. A skirt of glass strands round the legs and copper bands on the boots. The glow breathes like his spots, the strands sway as you walk, and pale motes drift up off you.
- **Armour power** (the **R** key, change it in Controls): with all four pieces on,
  - on the ground, the **Bell toll**: you float up, hang for a moment, then slam down. A ring goes out that hurts, throws back and stuns everything close (not you, your pets or players in creative). 12 seconds before the next one.
  - in the air, a **glide**: you drift the way you're looking and come down slowly. 7 seconds before the next one.
  - A small chestplate icon right of the hotbar shows when it's ready. The wait also shows on the chestplate.
- **The set's gifts are stronger.** Poison and his toll's dizziness can't touch you, a fast fall still turns slow, and when you're hurt below a third of your health, the glass rings on its own: what's close is thrown back and you get two hearts of cover (once every 30 seconds).
- **Four moves show what they do now.** Every move was checked in pictures. Pod burst, egg rain, stinger storm and sun lances hardly moved his body before. Now each one winds up first (he tightens and draws in), then goes (the bell kicks open, the strands whip out), then settles back.
- **Commands to control everything** (cheats on):
  - `/hollowbell config <setting> [value]` looks at or changes any setting (Tab lists them). `/hollowbell config` lists them all. `/giants config <setting> [value]` does it for every boss that has that setting.
  - `/hollowbell set freeze|speed|invulnerable|glow|name|target|wind|grudge|home|cooldowns` for the nearest one.
  - `/hollowbell pods mend` and `/hollowbell pods pop <n>`.
  - `/hollowbell do <move> <target>` and `/hollowbell do <move> at <x y z>` make him do a move at somebody or at a spot.

## What was new in 1.6.1

- Fixed the game sometimes hanging on 'Saving worlds' when you quit.

## What was new in 1.6.0

- **His ground is made by the world now.** The Bell Hollows are picked before the land there exists, and the world makes that land as his ground when it first makes it. No more painting over land that's already there, so no cut trees, steps or odd edges. It's a real biome now: F3 shows `hollowbell:bell_hollows` and `/locate biome hollowbell:bell_hollows` finds it. The ores, caves and stone under it are normal.
  - A new world picks its ground far off, on land (not sea), in land nobody has been to yet.
  - **Your old ground stays as it is.** Land already made in 1.5.0 or before isn't changed. Land around it that isn't made yet comes out as his ground as you go there. For a whole fresh one, run `/hollowbell ground new`: it picks new ground in land nobody has been to, and the next one comes down there. `/hollowbell ground renew` is gone.
- **Four new blocks in his ground.** You can mine them and they drop themselves (they're in the creative tab too):
  - **Bell Calcite**: calcite with pale green veins. His den's ribs and rim, and the ribs of fallen shards.
  - **Tendril Glass**: see-through green glass with glowing veins. His reefs and bits of old glass in the ground.
  - **Bell Shard**: cracked, cloudy glass from an old bell. The fallen shards and the rubble round his den.
  - **Spore Moss**: a thin glowing moss layer in the gardens.
- **He sleeps.** Leave him alone for about 2 minutes (no target, nobody riding, no orders, nobody hurting him) and he drifts down near the ground and sleeps: his bell pulses slowly, his glow dims, and his arms and strands lie on the ground. A hunting one only sleeps at night. A hit, any order or a rider wakes him, and a hunting one also wakes when you come close. He takes about 2 seconds to get up. Hit him where you see him: his hitboxes lie where he does.
  - `/hollowbell sleep` puts the nearest one to sleep, or wakes him. The book's Him page has a **Go to sleep / Wake up** line.
- **Creatures on the safe list.** "Spare who I look at" works on any creature too (your horse, a named pet, one cow), and **Spare all of that kind** spares every one of that kind (all cows, all wolves). Press again to take them off. Your own tamed animals are always safe. `/hollowbell spare` shows your list, and `/hollowbell spare add|remove <who>` or `/hollowbell spare add|remove kind <type>` changes it.
- **Moving orders work at any distance.** "Come to me", "Drift where I look", "Send him to a spot", `/hollowbell goto` and the new `/hollowbell come [player]` reach him wherever he is: close by, far off, out of the world, or left in land nobody has loaded. He comes as a sum and turns back into himself when someone is near. "Come to me" keeps aiming at you as you move. When he gets there he holds still until you tell him something else. The book says "The Hollowbell is coming. About N blocks, M minutes away." A trip stops at the edge of a woken crown's circle.
- **`/hollowbell tp [number]`** takes you to him (not in the book): onto safe ground just outside his bell, facing him. The number is from `/hollowbell list`. If none is out yet, it takes you to where the next one comes down. `/giants tp <boss>` and `/giants goto <x> <z>` do the same for all of JJ's bosses.
- **The finder speaks like the other giants' finders.** For example: "The Hollowbell is 1240 blocks north-east of you, at 820, -1100, in the Bell Hollows." Distances are rounded to 10 blocks. It shows in chat with the lodestone sound. The book's "Where is he?" and `/hollowbell natural` say the same.
- **The book matches the other giants' books.** A close ✕ at the top, health and pods meters, wind and grudge, and a line for each page. Every order has a tooltip saying what it does and what it costs. The Moves page stays open after a move. The switches on the Him page show what the server really has set.
- **Commands match the other giants'.** `bossbar`, `volume`, `health`, `damage`, `griefing`, `shake` and `stay` all say what they're set to when used on their own, and take `on`/`off` (the old `true`/`false` still works). New: `/hollowbell damage mobs`, `/hollowbell volume off`. Replies come from the language file.
- The riding panel hides with F1, moves below the boss bars, and shows the moves in three columns.
- His health bar shows a name tag's name. Boss bars show within 420 blocks at full size (was 550).
- Settings: `maxHollowbells` is now called `maxInWorld` (your number is kept). A settings file that can't be read is kept as it is and never written over until it reads cleanly.
- Tooltips on the book, the Stinger and his crown. The hunting egg has orange spots, the guardian egg green ones.
- **Fixed: `/giants kill` and `/hollowbell kill` left an invisible Hollowbell behind** with no health, still there, with bits of him hanging in the air. Now they really kill every one of him: in the world, out of it, or in land nobody has loaded. His death also always finishes now, even if everybody walks away while he's dying.
- **`/giants paint hollowbell [radius] [full|biome]`** (cheats on, also `/hollowbell paint`): the land round you becomes the Bell Hollows. `full` (the default) shapes and dresses the land like his ground and sets the biome; `biome` only sets the biome. The radius is 16 to 512 blocks (64 if you leave it out). It paints over everything in the circle, villages and builds too; only bedrock, water and blocks holding things (like chests) stay. **It can't be undone**, so save a copy of your world first.

## What was new in 1.5.0

- **His ground is much bigger and far more detailed.** The Bell Hollows reach about 900 blocks out now (it was 320). The edge has bays and headlands, and the outer part fades into the land around it in soft patches, so there's no hard line.
- **The land changes shape, not just colour.** Soft rolling hills, scooped with round hollows. Some are small, some are 24 blocks across and 7 deep, and they come in fields, with open mint meadows between. The sides of the hollows show bands of calcite, diorite, end stone and bone.
- **Lots to find on it:**
  - fallen shards of an old glass bell: curved walls of glass and calcite, up to 14 tall, some of them leaning
  - tendril reefs: low winding walls of calcite and lime glass with glowing froglight in them
  - spore gardens in the hollows: moss, small dripleaf and glow lichen
  - still pools in the bottoms of hollows, with a light glowing under the water
  - tall calcite spires, some with a glowing eye near the top
  - fallen bone ribs arching out of the ground
  - drifting lights: end rods on thin calcite posts
- **His den is in the middle.** A huge glass bell, 50 blocks across, half sunk in the ground and broken open on one side. Calcite ribs run down it and his crown sits on top. Inside, lights hang on chains over moss and a still pool. A lit path leads in through the break, past the glass it lost, and a ring of tall spires stands round it.
- The middle part is the palest, with the most shards and spires. Further out there are more meadows, gardens and pools.
- New setting `homeRadius` (900, from 200 to 2000): how far his ground reaches. Raising it grows a ground that's already there. Lowering it never shrinks one.
- **If you already have the Bell Hollows from 1.4.0,** they keep their middle and grow to the new size. Chunks the old ground turned are turned again the new way when they next load, but only if you haven't spent time in them. Places you've been using stay exactly as they are. If you'd like those turned too, `/hollowbell ground renew` does it (anything built in them is still left alone, column by column). `/hollowbell ground` says where his ground is, how big, and how much of it is done.
- Everything built is still left alone, like before: villages, chunks people have spent time in, and any column with planks, a path, a chest or a crop near the top. Only wild trees come off. Water is only ever put where it has a floor and walls, so nothing floods.
- When he dies, the next one comes down far enough away that his new ground never lies over the old one.
- **You can see him coming from far off.** Before, at full size you could only see him from about 440 blocks away, because the game only sends creatures that close. If he had stepped out of the world, he only came back when you were about 520 blocks from him. A smaller one showed up even later (about 230 blocks at a third of his size). Now he shows on the horizon from up to 1,024 blocks away, in the world or stepped out of it. Far off he's drawn simply, hanging calm, and past your render distance he sits pale in the fog. When you get close, the real one takes over in the same spot.
  - `/hollowbell detail far <blocks>` changes how far (0 turns it off, up to 4096). `/hollowbell detail far` on its own says how far it is. Changing it needs cheats on.
  - New setting `farSightBlocks` (1024).

## What was new in 1.4.0

- **The world keeps one of him.** A new world puts one Hollowbell out on its own, 3,000 to 15,000 blocks from spawn, on open, fairly flat land. He's calm and full size. When he dies, the next one comes down out of the sky 10 days later, somewhere else a long way off. If one is already out there (put down with an egg or a command, or stepped out of the world), that one counts as the world's own instead, and no new one comes down.
  - `/hollowbell natural on/off` turns it on or off. `/hollowbell natural` on its own says whether it's on, where he is or where the next one comes down, and how many days are left.
- **His own ground, the Bell Hollows.** Where the world's own one comes down, the land turns into a new biome that is only his. It reaches about 320 blocks out, with a ragged edge. Wild trees come off (not placed leaves, and nothing built), and the ground turns to calcite, end stone, diorite, bone, smooth stone and moss with bits of froglight and lime glass. Here and there are shallow bowls with a froglight glowing at the bottom, and little shards of glass sticking up. The fog is pale green-white, the sky is pale and the grass is mint. Nothing else spawns there. There's only one in a world, so it's the rarest place there is. It changes a chunk at a time as the land loads, so nothing stutters.
  - `/locate biome` can't find it, because the world doesn't make it, he does. The finder can.
  - When he dies the old Hollows stay the way they are. The next one comes down somewhere new, and the land there turns.
  - Anything people built is left alone: a column with planks, a path, a chest or a crop near the top isn't touched, and neither are villages or chunks players have spent time in.
- **Finder of the Hollowbell.** Craft it from a compass, four glass and an amethyst shard. Use it and it says how far he is and which way. If none of him is out there, it says how many days until the next one comes down and which way to go. It shines while he's within 300 blocks.
- **One at a time.** The world holds 1 Hollowbell at once now (it was no limit). Summon another and the oldest fades away where he floats, with no loot. `/hollowbell limit <n>` changes it, and 0 means no limit again. Ones that step out of the world count too. Updating never removes any: ones already out there stay, and they count as the oldest the next time you summon one or change the limit.
- **Bell glass armor does things now.** Wear all four pieces and:
  - poison can't touch you,
  - when you fall fast you drift down slowly instead, like he lets things down, and take no fall damage (a mace smash from high up is soft too).
  Other magic still hurts, like a potion of harming.
  Each piece says so in gold. The armor has new pictures too: pale green-white glass with copper seams, bone ribs down the chest, and a warm rivet here and there. It's see-through when you wear it, like his dome.
- **His crown holds him off.** Set his crown down, stand within 16 blocks of it and do `/hollowbell ward on`. For 20 minutes he won't come within 700 blocks of it, won't go after anything inside that circle, and the book can't send him in there. If he's inside when it wakes, he leaves. Then the crown needs 20 minutes to gather itself again. Take the crown up and it stops. `/hollowbell ward blocks 0` turns it off.
- **Keep him to one place.** `/hollowbell area <x> <z> <radius>` keeps the nearest one inside that circle. `/hollowbell area off` lets him go. In the book, on the "him" page, type how far (empty means 200) and press **Keep to here**, and he stays that close to where you're standing. **Let him roam** frees him. While he's kept, everywhere he drifts, is sent or wanders to is pulled inside, and if he's outside he drifts back in. If someone he's after is far outside, he lets them go. Riding him still takes him anywhere. It's saved on him, so it lasts, and it holds while he's out of the world too.
- **`/giants`** changes things for all of JJ's bosses at once (Pitchgut, Furrowmaw, Cerberus, the Hollowbell and the Lantern Willow, whichever you have). See the commands below.
- Fixed: "the oldest goes" at the limit was really a random one after a save, and a Hollowbell just loading back in with its chunk could push another one out. Now only a newly made one counts, and age is saved.
- Fixed: the limit only counted the Hollowbells in one world (not the nether or the end).
- Fixed: stingers from an old save disappeared the moment they loaded.
- Fixed: with Pitchgut or the Lantern Willow installed too, he could vanish when you stepped back from him. Now he stays in sight like before.
- Fixed: bellings that hatched off him vanished at once if every player was more than 128 blocks from them. Now they live out their few minutes.
- If the settings file can't be read, it's kept as `hollowbell.json.bad` instead of being lost.

## What was new in 1.3.5

- Fights between two of the bosses now take about as long at any size. Before, big ones fought for much longer than small ones (about twice as long at full size as at a third of it).

## What was new in 1.3.4

- The bosses take a bit more from each other now, so their fights don't drag on. Against another boss he takes 70% of a blow (it was 55%).

## What was new in 1.3.3

- **Getting off is the ride up in reverse.** Press G (or "get off" in the book): he sinks to the ground, a strand comes up over his dome, takes you off the crown and carries you back down his side to your feet beside him. `/hollowbell ride` still puts you on or off straight away.
- **The strand that carries you moves like one smooth rope.** It pays out from under the rim only as much as it needs and comes out over the rim's edge, so it no longer bends sharply or stretches much.
- **Leaving the game while he has you** (on his crown, being carried up or down, held by a strand or an arm, or inside his dome): he lets go and you're put on the ground beside him with slow falling. You don't come back in mid-air.
- **His attacks hit the other bosses where their bodies really are.** Pitchgut, the Furrowmaw and the Cerberus are made of many parts; before, he could only hit one small box of each. Parts deep under the ground are out of his reach.

## What was new in 1.3.2

- **Getting on him is a real ride now.** "Ride him" in the book (or `/hollowbell carry`): he comes up beside you, a strand reaches out and takes you round the middle, and carries you up his side, round the rim and over the dome, then sets you down on his crown. No more jumping from the strand's end to the top. The strand then goes back down the way it came.
- Stand still while the strand reaches for you. If you walk off, or get hurt, it pulls back and he tries again a moment later.
- While the strand is still going back down, his moves wait a moment.
- A boss made of many parts (like the Furrowmaw) now hurts him once per blow, not once per part.

## What was new in 1.3.1

- The other bosses' small helpers (the Willow's mudlings and the like) now count as their boss, so they land by his armour against giants. His own bellings count as him when they sting another boss.

## What was new in 1.3.0

A balance round across all five bosses, so they're fair against each other and fit how they move.

- **Pods can't be farmed.** A popped pod takes 1% of his health now, not 1.5%, and it takes 150 seconds to start growing back, not 90.
- **His sweeps reach all the way out.** Before, the outer edge of a sweep often missed.
- Explosions do about a third to him (they did almost nothing before).
- **Fights with the other bosses** (Pitchgut, Furrowmaw, Cerberus, the Lantern Willow): a blow from one of them lands by his armour against giants (a bit over half), not by where it hits. He never picks one up. He still hurts it.
- New command `/hollowbell carry`: he comes to you and a strand lifts you up onto his crown, like "sit on his crown" in the book.
- New command `/hollowbell giants on/off`: whether he picks fights with the other bosses.
- New command `/hollowbell volume <0-2>`: how loud he is.
- New settings `giantArmor`, `fightGiants` and `podPopShare`.

## What was new in 1.2.0

- **He steps out of the world when nobody is near him**, like Pitchgut. Once no player is close, he's written down (health, pods, mood, where he was going) and taken out of the game, so he costs nothing. He keeps drifting where he was going while he's gone. When anybody comes near where he should be by then, he's put back there, the same as he left.
- **The book still reaches him out there.** "Come to me", "Go there", the X/Z boxes, "Stay" and "Call off" all work, and "Where is he?" tells you where he's got to and how long he has left. Anything that needs him in front of you says so.
- **`/hollowbell where`** says where every one is, in the world or out of it. `/hollowbell away on/off`, `/hollowbell away blocks <n>` and `/hollowbell away now` control it.
- **His boss bars show from much further off**: about 550 blocks at full size, up from 180. `/hollowbell bossbar <blocks>` sets your own (0 = worked out from his size).

## What was new in 1.1.3

- **Other giants really hurt him now.** Hits from players still have to land on his crown, his glowing spots or his pods to do much. But blows from creatures and other bosses land in full, so Pitchgut's Unmake burns him and its last blow kills him like everything else.

## What was new in 1.1.2

- **He can't pick up the other giants.** His strands and arms won't grab, wrap or carry Pitchgut, the Cerberus, the Furrowmaw, any other boss (the Wither, the Ender Dragon, the Warden), or anything huge. He can still fight them. JJ's future boss mods are left alone too.

## What was new in 1.1.1

- **All the `/hollowbell` commands work again.** In 1.1.0 the new `detail` command stopped every other `/hollowbell` command from reaching the game.
- **Riding him:** the list of keys no longer runs off the screen. The moves are in two columns, and the whole list gets smaller if your window is small.
- **Wind and grudge bars** show while you ride him, like in the book.
- "Sit on his crown" is now just **Ride him**.

## What was new in 1.1

- **No more threads.** His pods hold him up now. Pop a third of them and he sinks right down for a while. Popped pods grow back.
- **He really flies.** He swims up and down like a jellyfish: the bell squeezes and shoots him up, and opens like a parachute when he sinks. He follows the ground, climbs after things up high and never scrapes his rim on the ground.
- **He moves like he has weight.** Arms and strands trail behind him, swing back when he stops, bundle up under him when he climbs and float up when he sinks. Nothing snaps from one pose to the next, even when a move gets cut off halfway. The bell leans into the way he's going.
- **Smooth on your screen too.** He glides between server updates instead of stepping.
- **What he grabs stays on the end of the strand** holding it, all the way up into the dome.
- **22 moves** (13 new), split into light, medium and heavy. Heavy moves are huge, hit a big area and come with a loud warning and a red message first. After one he's worn out for a few seconds.
- **He kills things now.** In 1.0 he hardly hurt creatures. Now his hits are much bigger, they land even right after another hit, hunting and guardian ones go after hostile mobs, and what he takes into the dome dies in there.
- **His own sounds:** a low hum, a wet drifting sound, the whoosh of each pulse, a deep toll, a heartbeat when you're inside him, and a sound for every move. Bigger ones sound deeper.
- **`/hollowbell detail on/off`** to choose whether he's drawn simpler far away.
- **A bit tidier:** fewer egg clumps (in small groups), fewer speckles, no floating bits.
- **A fresh one comes down out of the sky** instead of popping in.
- **He doesn't freeze** when you watch him from far off.
- **New commands:** `height`, `goto`, `stay`, `detail`. The thread commands are gone.
- Runs lighter: parts of him you can't see aren't drawn.

## Installing

1. You already have Fabric for 1.21.1 and Fabric API from the Mountain.
2. Put `hollowbell-1.7.1.jar` in `.minecraft\mods`. Take the old Hollowbell jar out first. It sits fine next to Pitchgut, Furrowmaw, the Cerberus and the Lantern Willow.
3. For your server, put the same jar in `mountain-server\mods` too. Anyone joining needs it in their own mods folder as well.

## Getting him

- **On his own:** the world puts one out, 3,000 to 15,000 blocks from spawn, in his Bell Hollows. No cheats needed. Make a **Finder of the Hollowbell** and it points the way:

```
GAG
GCG
```

(G = glass, A = amethyst shard, C = compass)

- **Spawn eggs** (creative, Spawn Eggs tab): Calm, Hunting, Guardian (full size) and Small (hunting, a fifth of the size). There's a Belling egg too.
- **Command:** `/hollowbell summon hunting` makes a full-size one in front of you. He comes down out of the sky. Add a size for a smaller or bigger one, like `/hollowbell summon calm 0.3`. Anything from 0.03 to 2 works.

### The moods

- **Calm** leaves you alone unless you hit him. Hit him and he'll come after you for a minute.
- **Hunting** goes after any player he can find.
- **Guardian** stays near the spot he was put down and goes after anything that comes near it, hostile mobs too.

Hunting ones go after hostile mobs as well when there's no player about.

### His ground

The world's own one comes down in the middle of the **Bell Hollows**, his own biome, about 900 blocks across. The world makes it as it makes that land: pale calcite and bone hills scooped with hollows, mint grass, fallen glass shards, tendril reefs, spore gardens, still pools, spires, fallen ribs and drifting lights, with his great glass bell in the middle. His own blocks are in it too: bell calcite, tendril glass, bell shard and spore moss. There's only one in a world. `/locate biome hollowbell:bell_hollows` finds it, and so does the finder.

## What he's like

- **He swims.** He drifts low over the land with his strands hanging under him, and the strand ends drag along the ground and flatten plants. He goes up hills and down into valleys.
- **His bell pulses.** The dome squeezes in and lets go, and a ripple runs round the rim. Each pulse pushes him along. His arms swing with it.
- **Climbing**, the bell squeezes hard and quick and shoots him up, with his arms and strands pulled in under him. **Sinking**, the bell opens wide and his strands float up and spread out.
- **He goes after things up high.** Fly near him or build up a pillar and he'll climb until you're among his strands.
- **His strands sway** and trail behind him when he moves, and swing back when he stops. The pods bob and the egg clumps wobble.
- **At night** the crown, the glowing spots and the froglight in his strands glow, and the glow pulses with him.
- **Touching a strand stings:** poison and slowness.

## His moves

There are three kinds. **Light** moves are quick and he uses them a lot. **Medium** moves have a short warning and hit hard: most normal mobs die from one. **Heavy** moves are rare and huge: they hit tens of blocks round him at full size, kill everything normal in the way and badly hurt a player in netherite. Before a heavy move you hear a loud warning sound, a red message comes up and his glow flares, so you have 1.5 to 4.5 seconds to get away. After one he's worn out for 5 seconds: slow, drooping and not attacking.

Bigger ones hit harder and wider, smaller ones less.

### Light

| Move | What it does |
|---|---|
| Grab and lift | A strand wraps round you and slowly pulls you up into the dome. Hit that strand four times and it lets go. |
| Harvest | He does it to anything: cows, villagers, even trees get pulled up and into the dome, where you can see them through the glass. Creatures die in there. |
| Sting volley | The strand ends nearest you flick three rounds of stingers at you. Poison and slowness. |
| Strand lash | One strand winds back and whips sideways through everything on that side of him. |
| Glow flash | Everything that glows on him flares at once: blindness and darkness, worse at night. |

### Medium

| Move | What it does |
|---|---|
| Curtain | The strands close in round you like a cage and pull tight. |
| Sweep | All his strands swing across the ground in a wide arc. |
| Arm slam | One of his eight arms lifts up and slaps down on the ground. Everything under the arm gets hit. |
| Arm wrap | An arm curls round you and squeezes. Hit that arm four times and it lets go. |
| Pulse wave | A hard pulse sends out a shock wave: knockback and nausea, and it knocks flyers out of the air. |
| Shed | Egg clumps break off and hatch into **Bellings**, small jellies that drift after you and sting. |
| Spore cloud | Egg clumps burst into clouds of poison that drift after you. |
| Pod burst | Every pod swells and sprays poison goo straight down. |
| Egg rain | Egg clumps drop off and burst where they land. Some hatch into Bellings. |

### Heavy

| Move | What it does |
|---|---|
| Drop | The bell lifts and glows, then the whole of him slams down on the ground. He stays down for a while, then rises back up slowly. |
| Whirlpool | He spins, and his strands drag everything in a wide ring in under him, then crush it. |
| Sky dive | He climbs about 70 blocks, turns over and dives bell first. The shock wave reaches about 100 blocks out. |
| Deep toll | Three tolls, louder each time, then a massive shock ring. It throws everything far and breaks glass and leaves. |
| Arm storm | All eight arms go up, then crash down one after another all round him. |
| Stinger storm | Every strand flicks stingers up, and they rain down over a wide area. |
| Sun lances | A beam of burning light comes out of each glowing spot and sweeps over the ground. One hunts you. |
| Undertow | His strands spread wide and drag everything round him in, then slam it down. |

## Inside the dome

If a strand pulls you all the way up, you're inside the dome with whatever else he's caught. You hear his heartbeat and the dome echoing. It hurts in there (poison and damage over time, and creatures don't last long), but the glowing spots are right over your head. **Hit them from inside for extra damage.** Hurt him enough from inside and he lets you go (you float down slowly).

A very small Hollowbell has no room inside: he squeezes you and drops you instead.

## Fighting him

| Where you hit him | How much it counts |
|---|---|
| The crown (the pale patch right on top of his dome) | 3 times (4 from inside) |
| The five glowing spots (the four yellow balls under the dome and the glowing vase in the middle) | 2.5 times (3.5 from inside) |
| A pod | full, and the pod pops |
| An egg clump | a little |
| The copper and bone (dome, rim, arms, strands) | very little |

- **Pods** hang among the strands and can be reached from the ground. Each popped pod takes 1% of his health on top of the hit, and drops a pod.
- **The pods hold him up.** Each popped pod makes him hang a little lower and lean toward that side. Pop a third of them (8 of 23 at full size) and he sinks right down for at least 30 seconds, until enough have grown back. A popped pod starts growing back after 150 seconds.
- While he's down (after a drop, or with his pods popped), everything hits him a bit harder, and his dome and rim are in reach from the ground. The crown and spots are high up, so bring a bow.
- **Boss bars:** his health, and how many pods are left (it turns purple while he's down).
- He has 6,000 health at full size. Smaller ones have less, and he gets more when several players fight him.
- **Below half health** the lime loops on his arms turn red and he pulses more often.
- **When he dies** he stops floating, the bell sinks onto the strands, the strands buckle, and he folds down onto the ground. Then he sinks away and drops his loot.

### What he drops

| Drop | What it's for |
|---|---|
| Stinger | A whip-like sword, a bit stronger than netherite. What it hits is poisoned and slowed. Hold right-click and let go to throw it like a harpoon: small things are pulled to you, walls and big things pull you to them. |
| Bell Glass | Craft it into bell glass armor (like diamond, with more toughness). The full set keeps poison and dizziness off, slows a fast fall, rings when you're hurt low, and has its Armour power (R): the Bell toll on the ground, a glide in the air. |
| Hollowbell Pods | For the book, and a pod in a glass bottle makes a poison potion. |
| Hollowbell Crown | A glowing trophy block to set down. Wake it with `/hollowbell ward on` and it holds him off. |
| Oxidized copper, bone blocks, verdant froglight | From his body. |

All of it goes into his Loot Cache on a pedestal where he fell. The beam over it goes out once you've emptied it.

### His crown

Set his crown down, stand within 16 blocks of it and do `/hollowbell ward on`. For 20 minutes he won't come within 700 blocks of it, won't go after anything inside that circle, and the book can't send him in there (a trip stops at the edge). Then the crown sits dark for 20 minutes. Take the crown up and it stops. `/hollowbell ward blocks 0` turns it off.

## The book

The **Hollowbell Codex** controls him, laid out like the Mountain's and Furrowmaw's. Craft it like this (P = pod, G = bell glass, B = book):

```
 P
GBG
 G
```

Carry it anywhere on you and the nearest Hollowbell within 600 blocks is yours: he leaves you alone, drifts where you point and grabs what you point at. Right-click to open it. Right-click a creature with it to send him after that creature.

- **Orders:** come to me, drift where I look, grab what I look at, leave it, hold still / let him drift, ride him / get off, let go of everything, where is he, and boxes to type an X and Z to send him to.
- **Moves:** every move he has, on three tabs: light, medium and heavy. A line goes grey with a count while that move is cooling down. Heavy moves cost a lot more wind.
- **Him:** calm / hunting / guardian, forget his grudges, whether he breaks blocks, whether he harvests, and **Keep to here** / **Let him roam** with a box for how many blocks (200 if you leave it empty).
- **Safe list:** *Spare who I look at* puts the player you're looking at on your list (or, if it's a creature, every one of that kind). *Spare everyone near me* adds every player within 48 blocks. Each name has a ✕ to take it off. The list only counts while you're holding the book.

At the top it says how far away he is, his health, his mood and what he's doing, with bars for his wind and his grudge. Like the Mountain, every order costs him some wind, and if you push him too hard he starts to resent you: first he takes his time, then he ignores some orders, then he picks his own targets, and in the end he turns on you. Hit him five times while the book keeps him off you and he stops listening to it.

## Being him

*Ride him* in the book: he drifts up beside you, a strand takes you round the middle and carries you up his side, round the rim and over the dome, and sets you down on his crown. Stand still while it reaches for you. Then the view swings out behind him, like the Mountain's. To get off, press G: a strand carries you back down the same way.

| Key | What it does |
|---|---|
| Mouse | which way |
| W / A / S / D | drift him that way |
| Jump / Sneak | take him up / down |
| 1 to 0, then Z X C V B N M, then R H J K U | his 22 moves, light ones first (the list sits top left with a clock on each line) |
| Scroll, or [ and ] | move the view in and out |
| G | get off (he sinks down and a strand carries you back down beside him) |

Top left you also see his health, and his **wind** and **grudge** bars, the same as in the book. Every move costs him some wind. Push him too hard and the grudge grows.

## All the commands (cheats on)

| Command | What it does |
|---|---|
| `/hollowbell summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/hollowbell do <move> [target]` | makes the nearest one do a move at you (or at that target). `/hollowbell do <move> at <x y z>` aims it at a spot. Light: `grab`, `harvest`, `sting_volley`, `strand_lash`, `glow_flash`. Medium: `curtain`, `sweep`, `arm_slam`, `arm_wrap`, `pulse_wave`, `shed`, `spore_cloud`, `pod_burst`, `egg_rain`. Heavy: `drop`, `whirlpool`, `sky_dive`, `deep_toll`, `arm_storm`, `stinger_storm`, `sun_lances`, `undertow` |
| `/hollowbell list` | where they all are, their health and pods |
| `/hollowbell where` | where every one is, in the world or out of it (works without cheats) |
| `/hollowbell away on/off` | whether he steps out of the world when nobody is near. On its own it says which. |
| `/hollowbell away blocks <n>` | how far off everybody has to be before he steps out (0 = worked out for you) |
| `/hollowbell away now` | every one with nobody near steps out now |
| `/hollowbell bossbar [on/off/blocks]` | shows or hides his boss bars, or how far off they show (0 = worked out from his size). On its own it says which. |
| `/hollowbell mood <mood>` | changes the nearest one's mood |
| `/hollowbell size <size>` | resizes the nearest one |
| `/hollowbell hurt <amount>` | takes that much off the nearest one |
| `/hollowbell sethealth <health>` | sets the nearest one's health |
| `/hollowbell heal` | heals the nearest one fully and grows his pods back |
| `/hollowbell popped <n>` | pops that many pods (for testing) |
| `/hollowbell height <blocks>` | how high the nearest one drifts over the ground |
| `/hollowbell goto <x> <z>` | sends the nearest one there |
| `/hollowbell stay [on/off]` | makes the nearest one hold still where he is, or drift again (on its own it swaps) |
| `/hollowbell ride` | puts you straight on top of him to ride him (or takes you off) |
| `/hollowbell kill` | kills them (he still folds down and sinks) |
| `/hollowbell remove` | removes them straight away, and the ones out of the world too |
| `/hollowbell health [health]` | full-size health for new ones. On its own it says it. |
| `/hollowbell damage [multiplier]` | multiplies how hard he hits. `/hollowbell damage mobs [times]`: how much harder he hits creatures. |
| `/hollowbell griefing [on/off]` | whether he pulls up trees and flattens plants. On its own it says which. |
| `/hollowbell shake [on/off]` | screen shake. On its own it says which. |
| `/hollowbell sleep` | the nearest one goes to sleep, or wakes up |
| `/hollowbell come [player]` | the nearest one comes to you (or to that player), wherever he is |
| `/hollowbell paint [radius] [full/biome]` | the land round you becomes the Bell Hollows (the same as `/giants paint hollowbell`). Can't be undone. |
| `/hollowbell tp [number]` | takes you to the nearest one (or that one from `/hollowbell list`) |
| `/hollowbell spare` | your safe list. `/hollowbell spare add <who>`, `spare remove <who>`, `spare add kind <type>`, `spare remove kind <type>` change it |
| `/hollowbell carry` | he comes to you and a strand carries you up his side and over his dome onto his crown (if you're already up there, it carries you back down) |
| `/hollowbell giants on/off` | whether he picks fights with the other bosses. On its own it says which. |
| `/hollowbell volume [off/0-2]` | how loud he is (1 is normal). On its own it says how loud. |
| `/hollowbell natural [on/off]` | whether the world keeps one of him coming down on his own. On its own it says where he is or where the next one comes down, and how many days are left |
| `/hollowbell limit [n]` | how many of him the world holds at once (0 = no limit). Summoning past it makes the oldest fade away. On its own it says the limit and how many there are |
| `/hollowbell ward on` | wakes his crown set down within 16 blocks of you: he's held off for a while |
| `/hollowbell ward off` | stops it, and lets it be used again straight away |
| `/hollowbell ward` | says whether a crown is awake, or how long until it can be again |
| `/hollowbell ward minutes <1-1440>` | how long the crown holds him off |
| `/hollowbell ward rest <0-1440>` | how many minutes the crown sits dark afterwards |
| `/hollowbell ward blocks <0-20000>` | how far the crown holds him off |
| `/hollowbell area <x> <z> <radius>` | keeps the nearest one within that many blocks (32 or more) of that spot. `/hollowbell area off` frees him, `/hollowbell area` says which |
| `/hollowbell config [setting] [value]` | lists every setting, says one, or changes it |
| `/hollowbell set freeze on/off` | the nearest one stops thinking and moving, or starts again |
| `/hollowbell set speed <0.1-5>` | how fast the nearest one drifts (1 is normal) |
| `/hollowbell set invulnerable on/off` | nothing can hurt the nearest one |
| `/hollowbell set glow on/off` | the nearest one glows through walls |
| `/hollowbell set name <name>` | names the nearest one |
| `/hollowbell set target <who>` / `set target none` | sends the nearest one after somebody, or calls him off |
| `/hollowbell set wind <0-1>` | the nearest one's wind (how much the book can still ask of him) |
| `/hollowbell set grudge <player> <0-1>` | how much he holds against that player |
| `/hollowbell set home` | the nearest one's home is where you stand |
| `/hollowbell set cooldowns clear` | he can do any move again right away |
| `/hollowbell pods mend` / `pods pop <n>` | grows all his pods back, or pops that many |
| `/hollowbell reload` | reads the settings file again |
| `/hollowbell detail on/off` | on (the default): he's drawn simpler far away so the game runs faster. Off: always full detail. On its own it says which. This one works without cheats and only changes your own game. |
| `/hollowbell detail far <blocks>` | how far off you can see him coming (1024 to start, 0 = off, up to 4096). On its own it says how far. Changing it needs cheats on. |
| `/hollowbell ground` | where his ground, the Bell Hollows, is and how far it reaches |
| `/hollowbell ground new` | new Bell Hollows in land nobody has been to yet. The old ground stays as it is. If he isn't out, the next one comes down there. |

### /giants (all of JJ's bosses at once)

Works the same in every one of JJ's boss mods. You only need one of them for it to work. Each boss you have answers on its own line.

| Command | What it does |
|---|---|
| `/giants` | one line from each boss: how many are standing, the limit, and the main settings |
| `/giants natural [on/off]` | whether each world keeps one of each boss coming on its own |
| `/giants limit <0-20>` | how many of each the world holds at once (0 means no limit) |
| `/giants fight [on/off]` | whether they fight each other |
| `/giants away [on/off]` | whether they step out of the world when nobody is near |
| `/giants volume <0-2>` | how loud they are (1 is normal) |
| `/giants shake [on/off]` | whether they shake your screen |
| `/giants bossbar <blocks>` | how far away their boss bars show (0 works it out from size) |
| `/giants griefing [on/off]` | whether they break blocks |
| `/giants where` | where each one is (works without cheats) |
| `/giants list` | every one standing, with size and health |
| `/giants kill` | kills them all, with loot |
| `/giants remove` | removes them all, no loot |
| `/giants goto <x> <z>` | sends every one of them there, wherever they are |
| `/giants tp <boss>` | takes you to that boss |
| `/giants paint <boss> [radius] [full/biome]` | turns the land round you into that boss's ground. Can't be undone. |
| `/giants config [setting] [value]` | looks at or changes a setting in every boss that has it. On its own it lists each boss's settings. |

## Settings

`config\hollowbell.json`, made the first time you start the game:

- `spawnEggScale` is how big the egg ones are (1.0 is full size), and `smallEggScale` is how big the small egg is (0.2)
- `health`, `damageMultiplier`, and `mobDamage` (2.5): how much harder he hits creatures than players
- `griefing` covers pulling up trees, flattening plants and cracking the ground. It also needs the mobGriefing gamerule on.
- `harvest`: whether he pulls up creatures and trees on his own
- `insideDome`: off and he squeezes what he lifts and drops it instead of taking it inside
- `podRegrowSeconds` (150): how long a popped pod takes to start growing back
- `podPopShare` (0.01): how much of his health a popped pod takes
- `podsToSink` (0.34): how many of his pods have to be popped before he sinks, and `sunkSeconds` (30): how long he stays down at least
- `shedCount`: how many egg clumps he sheds at a time (0 = never)
- `giantArmor` (0.7): how much of a blow from another boss he takes
- `fightGiants` (on): whether he picks fights with the other bosses
- `maxInWorld` (1): how many the world holds at once (0 = no limit). Past that, the oldest one goes.
- `homeRadius` (900): how far his own ground, the Bell Hollows, reaches (200 to 2000). Raising it grows a ground already there; lowering it never shrinks one.
- `oneInTheWorld` (on): whether the world keeps one of him coming down on his own. `worldScale` (1.0) is how big that one is, `worldRespawnDays` (10) how many days after he dies the next one comes, and `respawnBlocks` (7000) about how far from where he fell
- `wardBlocks` (700), `wardSeconds` (1200) and `wardRestSeconds` (1200): how far his woken crown holds him off, for how long, and how long it sits dark afterwards
- `bookCosts`, `windSeconds`, `freeHits`, `grudgeRate`, `bookRange`: how the book works, the same as the Mountain's
- `screenShake`, `bossBar`, `soundVolume`, `renderDistance`
- `bossBarRange`: how far off his boss bars show (0 = worked out from his size, about 550 at full size)
- `offscreenTravel` (on) and `awayBlocks` (0): whether he steps out of the world when nobody is near, and how far off they have to be (0 = worked out for you)
- `ambientSounds`: his hum and drifting sound (off keeps the rest)
- `chunkLoading`: keeps the ground under him loaded and him moving while a player is near him
- `simpleFarAway` / `simpleFarAwayAt`: far away he's drawn with bigger blocks so he runs faster (`/hollowbell detail` changes the first one)
- `farSightBlocks` (1024): how far off you can see him coming, in the world or stepped out of it (0 = off, up to 4096)

## Tips

- Pop the pods first. They're low down among the strands, and popping a third of them brings him down.
- If a strand grabs you, hit that strand. If you'd rather go up, hit the glowing spots once you're inside.
- Pop a third of his pods to bring him down, then hit him while he can't rise.
- Watch for the bell squeezing hard: that's the pulse wave. Don't be flying near him when it goes.
- When the red message comes up, run. Heavy moves reach a long way, so get well out from under him, or get behind something solid.
- Right after a heavy move he's worn out for a few seconds. That's the time to hit him.
- Use a render distance of 12 or more for the full-size one.
