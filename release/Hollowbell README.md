# Hollowbell (Java mod), version 1.12.11

My KillzAI Hollowbell build, turned into a boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish
made of glass, copper and bone, about 200 blocks wide and 200 tall at full size, and he swims through the air.

He's made from my build block for block, with every block's real texture. The glass is see-through, so you can look
into his hollow dome and see what he's caught.

## What's new

The book shows how much wind each attack costs: a thin bar under each one, green, yellow or red if he hasn't enough
wind left.

1.12.11: much easier to hit: swing at any part of him you can see in reach, his long arms and strands too, and it
lands on that part, at any size, still or drifting.

## Install

You need Fabric Loader 0.19.5 or newer for 1.21.1 and Fabric API. Put `hollowbell-1.12.11.jar` in your `mods` folder
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

## Main commands (cheats on)

| Command | What it does |
|---|---|
| `/hollowbell summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/hollowbell do <move> [target]` | the nearest one does that move |
| `/hollowbell where` | where every one is (no cheats needed) |
| `/hollowbell tp [number]` | takes you to him |
| `/hollowbell come [player]` | he comes to you, wherever he is |
| `/hollowbell ride` | puts you on top of him, or takes you off |
| `/hollowbell kill` / `remove` | kills them (with loot) or removes them (no loot) |
| `/hollowbell config [setting] [value]` | lists, shows or changes a setting |
| `/hollowbell detail on/off` | draws him simpler far away so the game runs faster (no cheats needed) |
| `/giants ...` | the same kind of commands for all my giants at once |

The settings are in `config/hollowbell.json`. `/hollowbell config` lists them all.
