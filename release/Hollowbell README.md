# Hollowbell (Java mod), version 1.12.20

My KillzAI Hollowbell build, turned into a boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish
made of glass, copper and bone, about 200 blocks wide and 200 tall at full size, and he swims through the air.

He's made from my build block for block, with every block's real texture. The glass is see-through, so you can look
into his hollow dome and see what he's caught.

## What's new

- New `/giants` commands for operators: `home <giant>` takes you to the middle of a giant's ground,
  `health <x>` and `damage <x>` multiply the giants' health and damage (1.2 is a fifth more), `preset
  easy/normal/hard` sets both, and `heal`, `calm`, `sleep`, `wake`, `freeze`, `unfreeze`, `summon` and `info`
  work for all the giants or just the one you name. `/hollowbell detail` is gone: the Giants Guide's
  Performance screen does that now.
- When he comes back after dying, his new ground keeps clear of the other giants' grounds.
- With the Belling count turned right down on the Performance screen he still sheds at least one belling.

### 1.12.19

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
