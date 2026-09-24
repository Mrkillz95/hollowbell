# Pitchgut (Java mod) — version 1.23.0

Your KillzAI build turned into a real walking boss for Minecraft Java 1.21.1 with Fabric. At full size he's about 265 blocks long and 185 tall, a little longer than the build because his tail end is round now instead of sliced flat.

## What's new in 1.23

He has a name now. **Pitchgut** — for the black stuff that comes out of him and for what is waiting in the middle of him if you get swallowed. "The Mountain That Breathes" is what people called him before anybody got close enough to name him properly.

What changed with it: his name on the boss bar, in the mods list, on every one of his sounds, and on everything he drops — Pitchgut Flesh, Horn of Pitchgut, Finder of Pitchgut, Pitchgut's Heart, Pitchgut's Book. The spawn eggs read Calm, Hunting and Guardian Pitchgut. The command is now **`/pitchgut`**, and **`/mountain` still works exactly as it did** — same subcommands, same everything — so nothing you have built in a command block breaks.

What did not change: anything under the hood. The mod id, the entity id, the item ids and the config file are all still `mountain_breathes`, on purpose. Worlds you already have keep working, and the one already walking around out there is still the same creature — he just answers to something now.

## Fixed in 1.22.1

The attack page of the book. Two new lines went onto it in 1.22 and the page was never built for sixteen of them, so the last one sat underneath **Close the book** and you couldn't press either cleanly. That page now runs on a shorter grid of its own: all sixteen attacks on eight rows, the close line on its own row under them, and it still fits on a small screen.

While it was open: every attack now carries a thin bar under its name showing what that one takes out of him — green he can do all day, yellow costs him, red is more wind than he has left — and hovering a line tells you the number and whether it is one he'll hold against you. The two new moves got shorter names to match the rest: **The long breath** and **Tear a piece off**.

## What's new in 1.22

- **The stare doesn't burn you any more. It stands you up against yourself.** He looks at you, black goo comes up out of the ground behind you in your own shape, and it comes at you with your health and whatever is in your hand. It stands for exactly as long as he can see you — put a hill, a wall or one of his own legs between his eyes and your back and it falls apart where it stands. One of them per person, and the whole time it's out he is getting on with everything else, because holding the stare costs him nothing. The old version was lasers and a damage tick; this one makes "get out of his sight" the entire problem.

- **He breathes in and doesn't stop.** Fifteen seconds of a pull you can't walk out of: you, anything else alive, dropped items, all of it dragged toward his mouth, hardest in the last few blocks. Solid world between you and his head kills it completely, which is the only answer. Get all the way to the mouth and you go down his throat into the heart room. He'll reach for it himself once he's properly hurt, and it's the one thing he can still do well flat on his belly.

- **He tears a piece off himself.** He puts a hand into his own side and pulls, and what comes out stands up: him at about a fifth the size, fighting beside him for a minute before it collapses into goo. It costs him health, most of a wind bar and it sours him badly. **While somebody is carrying the book it is theirs to call and only theirs** — he won't decide on it himself. Put the book down and he'll do it on his own again. The piece drops no loot (killing lumps was never going to be the cheap way to a nether star), it doesn't count against `/mountain limit`, and it's never the world's own one.

- **The big moves cost what they're worth.** Every heavy line in the book got dearer: the stare went from a fifth of his wind to a third, the goo storm and eye storm to near half, and the two new ones to just over half and seven tenths. Ordering the stare or the eye storm now sours him a little every time as well, and tearing a piece off sours him a lot. The small stuff is unchanged.

- **And he won't fight his own shadow**, or a piece of himself, or another Mountain.

## What's new in 1.21

- **He keeps what you do to him.** A leg or an arm that has been broken and knitted back never comes back the same. It mends, but it mends **scarred** — grey and dead-looking, and it only takes about half what a whole one did, so it goes again much sooner. Break a scarred one a second time and that limb is **ruined**: it stays broken for good and it never knits again. His burst eyes already worked this way and always will. So a Mountain you have fought five times over a month looks like it and fights like it, and yours ends up looking nothing like anybody else's. He can never be more than a third ruined, so he can't quietly fall apart on his own — past that, a scarred limb just breaks and knits and breaks again. `/mountain scars` says what he is carrying, `/mountain scars clear` takes it all off, and `/mountain scars off` goes back to him knitting back whole.

- **Break about seven legs in ten and he never stands again.** At 27 of his 39 legs he comes down on his belly for good, and that is where he stays. He can't follow you, he can't be sent anywhere, he can't be made to burrow or to carry you, and he won't lie down and sleep — the book says so instead of quietly doing nothing. What he does instead is get worse. He stops waiting between moves, takes whatever is nearest whatever variant he is, hits about a third harder, and works through everything he can do from the floor: the stare, the storm, tentacles, darts, the tongue, the scream. The moves that need legs under him are out. His health bar goes purple and says *down for good*. You can still be pulled apart by him, you just have to walk into it. Standing back up needs enough legs to knit — and ruined ones never do. `crippleAt` in the settings moves the line.

- **The four-leg knockdown is a real window now.** Four legs on one side put him on his belly for seven seconds, which was barely worth breaking them for. It's twenty-one seconds now, and it gets longer the more of him is already gone, up to forty-five. He also stopped sliding several blocks across the ground while he was down — flat on his belly he turns about his own middle instead of pivoting around a point out in front of him.

- **The heart he drops does something worth doing.** It used to be a block you knocked on to shove nearby monsters over, which was fine and easy to miss. Now it is the one thing that holds him off. Knock on it and it goes off like a drum: everything nearby that means you harm is thrown off its feet as before, **and for twenty minutes he will not come within seven hundred blocks of it.** If he is already inside that circle when it starts he turns round and walks out, and the book will not send him back in. Then it sits dark for another twenty while it gathers itself. There is one beating at a time, and digging it up stops it, so it is somewhere to run to rather than somewhere to live. It also tells you what it is now — a tooltip on the item, a line in chat every time you touch it saying how long is left or how long until it is ready, and three clear looks: cold and dark, warm and beating, or lit right through with a ring rolling out along the ground. It isn't a plain cube any more either. `/mountain ward` says what is going on, `/mountain ward minutes <n>` and `/mountain ward rest <n>` change how long it runs and how long it sleeps, `/mountain ward blocks <n>` changes the seven hundred, and `0` turns it off.

- **One of him, and the newest one wins.** A spawn egg, a `/summon` or the world quietly putting up a second could all leave you with two, which the whole mod is written around not happening. There is a real limit now, and summoning another is treated as somebody deciding they want him here: the one that has been standing longest **comes apart where it stands** and the new one takes over, including taking over as the world's own so the count down to the next one still means something. It doesn't get written down as a calculation on the way out either, so it doesn't come walking back. If you put one down yourself and there's room, the world adopts it rather than adding another beside it.

  `/mountain limit <n>` sets how many the world will hold — `1` to start, `0` for as many as you like, up to 20. Turning it down prunes the extras within a few seconds.

- **And he doesn't fight his own kind.** With the limit off and two of them standing close enough to tread on each other, they'd shove, one would take the shove as a hit, and from then on the pair were locked on each other and no use to anybody. Nothing can set another Mountain as his target now.

- **The hole he leaves is a thousand blocks out from him.** Two thousand across, where it used to be three hundred and twenty — the old setting said four hundred but the digger was quietly capped at a hundred and sixty either way. That is a lot of ground to make and then take away, so the digging was rebuilt to cope: it opens faster the bigger it is, it only holds open the ring of ground it is working on instead of the whole hole (sixteen thousand loaded chunks would have been the end of your server), and it tells you how far along it is while it goes. It still takes a few minutes to finish opening, and it will add a good chunk of size to your world file. `/mountain unmake radius <n>` takes it back down if that is too much, and `/mountain unmake wave <n>` sets how far the wave behind it carries.

- **He comes back after the last thing he does.** Asking him to finish it used to end them for good in practice. Ten Minecraft days later another one gets up, **within seven thousand blocks of where the last one fell** rather than anywhere up to ten thousand. That holds for any Mountain that was the only one standing, not just the world's own one. `/mountain where` counts the days down for you.

## What's new in 1.20.3

- **One book, and now that is a real rule.** There was only ever supposed to be one of his book in a world, but the only place that was ever checked was the moment you wrote one — so a copy out of the creative menu, or a `/give`, walked straight past it, and you could end up carrying three. The real one now carries a mark of its own, written into the book itself, and the world remembers which mark is the real one. Every other copy comes apart in your hands within half a second of turning up, wherever it came from and whatever it is sitting in — your pack or your ender chest, it makes no difference ("It comes apart in your hands. There is only ever one, and this was not it.").

- **A copy is worth nothing even in that half second.** It will not keep him off you, your safe list does not come back with it, and the pages give him no orders. Only the marked one owns him.

- **An old world with several sorts itself out.** The first one of them he sees is taken on as the real one and the rest go, so you don't have to do anything about the spares you already have.

- **If the real one is truly lost**, `/mountain book lost` still lets another be written, and the next book to turn up becomes the real one.

- **The book goes out of a marked man's hands the same second.** Whoever asks him to finish it could hold his book for another half second afterwards. Now it burns the moment the mark lands. Burning a *copy* off them no longer frees the real one up to be rewritten either, which used to hand somebody a second real book.

## What's new in 1.20.2

- **Five hits with the book in your hand and he is done with you.** The book keeps him off you, and he will wear a certain amount of being hit by somebody it is protecting — five times, and the hotbar counts them down for you each time ("He lets it go. 2 more and he won't."). On the fifth he stops reading it: the book in your hands means nothing, your safe list means nothing, and he comes for you. It wears off the way any of his grudges does, in about two Minecraft days, or `/mountain settle` clears it at once. `freeHits` in the settings file changes the number, and `0` turns it off.

## What was new in 1.20

- **The last thing he does.** A second red line on the book's **Him** page, and the opposite of the first one: you can only ask this of him when he has a quarter of himself left or less. Breaking the world needs him whole. This one needs him nearly finished, and it kills him.

  It asks twice, same as the other, and the word you type is UNMAKE. Then twenty seconds — not five. The sky goes black over the whole world and stays black, it starts to pour, every eye he has left comes open and burns everything alive for hundreds of blocks in every direction, and everyone online is told, near him or not. Long enough to run, nowhere near long enough to get clear.

  Then he comes down. The hole is four hundred blocks across and a hundred deep, five times the one "Break the world" leaves. A wave goes out behind it another thousand blocks, flattening trees and throwing everything living off its feet as it passes. Everything alive anywhere near him dies. His heart is left on a spike of bedrock in the middle of the hole so it doesn't get buried, still beating. And that's the end of him — he doesn't get knocked down, he dies, and the world's ten-day wait for the next one starts.

- **And it follows you.** Whoever asks is marked, and it never comes off. **No book will ever stay in your hands again** — it burns the second you touch one, wherever it came from, and the world lets another be written so nobody else loses theirs over it. **No safe list will ever cover you**, yours or anybody else's. And **every Mountain there will ever be goes for you on sight** — calm, guardian, hunter, it doesn't matter, they all know you. `/mountain unmake unmark <player>` is the only way back, and that's the console, not the game.

- **And if you just kill him, none of it happens.** The warning page says so in as many words, in green under all the red: the mark only ever comes from asking. Put him down the ordinary way and every Mountain after him treats you like anyone else.

- **"Break the world" is gone.** The one you could only ask of him while he was whole has been taken out entirely — the line, the crater, the three days of being hunted, the settings and the commands. There is one thing like this in the book now and it is the one above.

- **It goes in beats, not in noise.** Twenty seconds of continuous roaring was frantic; this is dramatic. One low note while the sky goes over and he hasn't moved. Then he comes up, slowly. Then he's held at the top and his eyes open a bank at a time, each with its own sound. Then everything he has left at once. And then — forty ticks of nothing. The beams go out, the ash stops, the sound cuts, and he just stands there at his full height in dead silence. Then he comes down. The stillness is the loudest part of it.

- `/mountain unmake on|off` takes the whole thing out of the book, and `unmakeRadius` / `unmakeWave` in the settings file set how far the hole and the wave carry.

## What was new in 1.19.4

- **The safe list is absolute now.** He would still come at you: pick you out, roar, walk over and swing — and then land nothing, because the list was only ever checked at the moment damage was dealt, never when he was choosing who to go after. Four places were picking targets without asking the list at all: a hunter grabbing the nearest player every half second, a guardian going for a spared kind of creature, the book aiming an attack at whoever happened to be closest, and his memory of whoever last hurt him. Hitting him no longer puts you in his sights either, if you are on the list — he flinches, he looks at you, and he lets it go. The list beats every grudge he has.

## What was new in 1.19.3

- **The eye storm takes the whole crowd at once.** Every living thing he can see gets a beam of its own out of an eye of its own — eight of them, eight beams; fifty of them, fifty beams. Nothing standing there is skipped. It used to stop at eight, and worse, you only ever saw four of those: the game only syncs three look points about him, so the drawing had nothing to aim the rest at. The list now goes over on its own, one number per creature, so the picture matches what he is actually doing. The beams thin out a little as the crowd grows so fifty of them don't white out the screen.

- **He never takes himself under the ground any more.** Digging is the book's to give and nothing else's — he will not decide on his own that something is far enough away to be worth burrowing for.

- **Two days between orders to go under**, down from three. `/mountain digcooldown` still changes it.

## What was new in 1.19.2

- **He was freezing solid instead of walking.** This was the real reason the book only ever said "loaded" and the number never moved. He held his own piece of world open from inside his own tick — so the moment he missed a beat he couldn't ask for it back, and couldn't take a beat because it was shut. A statue, loaded forever, never moving. Under the ground it happened every time: he covers three blocks a tick down there, which is further than the piece of world he was holding open is wide, so he simply ran off the edge of it.

  Three fixes. He holds a wider piece open, more often, while he is under the ground. The check for whether anybody is near him now runs off the game's own clock instead of his, so a Mountain who has stopped running is still found. And if he has stopped running at all — for any reason, whoever is standing nearby — he is written down and put straight back where he should be, because a calculation is better than a statue.

- **He steps out as soon as the game would stop running him**, not at a fixed 800 blocks. That was the other half of it: the game let go of him long before he ever got 800 blocks from you, so the rule never fired. Now it follows the game's own simulation distance. `/mountain away blocks <n>` sets a number of your own if you would rather see him from further off; `0` means follow the game.

- **Teleporting to where the book says he is no longer bounces him.** He was loading for a second, being judged "alone" before he had taken a single tick, and being put away again three hundred blocks back the way he came — with a half-drawn health bar on the way past. He now gets ten seconds on his feet before anything decides he is alone out there.

- **He tries to inhale more often.** The breath comes round every five and a half seconds in a fight instead of seven and a half, and he holds the pull for longer each time.

## What was new in 1.19.1

- **He steps out of the world when nobody is near him.** This is the one that was quietly broken. He used to hold his own piece of world open the whole time he had somewhere to go, so he never unloaded, never became a calculation, and "Where is he?" could only ever answer for a Mountain that was loaded — usually one standing still a few hundred blocks away doing nothing.

  Now, once there is no player within about 800 blocks of him, the whole of him is written down — health, which legs are broken, which eyes are burst, what he was told to do, what he holds against you — and he is taken out of the game. From then on he is a line on a map: where he set off from, where he is going, when he left and how fast he covers ground. The book works out where he ought to be from that, to the tick. The moment anybody comes within about 720 blocks of that spot he is put back there, whole, still walking the same way. Test run: he left the world at x=7 heading for 4,000, the answer climbed 7 → 8 → 20 while he was gone, and he came back at x=21 with the same 442 health and the same 170 eyes he left with.

- **The book still reaches him out there.** While he is nothing but a calculation you can still turn him: "Come to me" points him at you, "Walk where I look" and the X/Z boxes re-aim him from wherever the sum says he is, "Go under to those" runs him there at his digging speed, and "Stand still" stops the sum where it is. Each one tells you how far off he is and roughly how many minutes of walking he has left. Anything that needs him standing in front of you — attacks, being carried, waking him — says so instead of quietly doing nothing.

- **`/mountain where`** prints the lot: whether he is loaded and where, his health, eyes and broken legs, what the world's sum says, and how long his journey has left. `/mountain away on|off`, `/mountain away blocks <n>` and `/mountain away now` control it, and `/mountain goto` and `/mountain stay` reach him while he is out there too.

## What was new in 1.19

- **The book costs him something now.** Two things are tracked and both show as a bar at the top of the book.

  **Wind** is the short one. Every line you press takes some out of him — the eye storm a lot, a leg sweep hardly any, an order to go under the ground more than anything. In a fight you get six or seven real attacks out of him before it is gone, and then the big moves are simply greyed out until he has had a moment. It comes back on its own over about a minute and a half, faster while he sleeps or lies under the ground.

  **Grudge** is the slow one, and it only goes up when you push him past empty, or send him at something that costs him a piece of himself — bursting his own eyes, digging again the moment he is up, ordering an attack on broken legs, being knocked off his back. At a quarter he starts dragging his feet and orders land half a second late. At half he ignores about one line in three outright. Higher and he does the attack but picks his own target instead of the one you pointed at. At the top he stops reading the book at all, drops you off your own safe list, and comes for you. It falls on its own — about a third of it per Minecraft day, four times that while he sleeps — and feeding him something takes the edge off for everybody. `/mountain settle` puts him right at once.

- **Breaking the world.** A new line in red at the bottom of the book's **Him** page. It asks twice: a page telling you exactly what it costs, with the button dead for three seconds so you have to read it, and then a second page where you type BREAK before the button will light. Say it twice and he rears right up, holds there long enough for everyone to see it coming, and comes down.

  It takes a quarter of everything he has, so he will only do it while he is at three quarters health or better. The ground goes for 160 blocks across and 40 down, leaving a burnt hole with blackstone and magma in the bottom. Anything standing anywhere near is thrown off its feet. The book burns in your hands the moment you ask — and then he hunts you for three Minecraft days, and nothing on your safe list will save you. He will never do this by himself; it only ever happens because somebody asked twice. `/mountain worldbreaker off` takes the line out of the book entirely.

- **He leaves a heart.** Killing him now drops **The Mountain's Heart**, a block you can only get that way. Set it down and it beats and glows to itself. Knock on it and it answers in his voice: everything within 48 blocks that means you harm is thrown off its feet, slowed, weakened and made to forget what it was doing, and lit up so you can see where they all went. Five minutes before it will answer you again, and that rest follows you rather than the block, so digging it up and putting it back down is no use.

- **The book is one kill's worth again.** It used to want three nether stars and two eyes, which meant killing him three times over to make the thing that stops you having to. Now it is four flesh, one eye, one nether star, one horn, one goo and a plain book — everything but the book comes off one body.

- **Everything checked over.** A full pass over the whole mod turned up a dozen real problems, all fixed: a crafted packet could burst every eye at once and kill him outright; the book could be handed off in the five seconds before he landed and a second one written; a crater could leave lumps of untouched world standing in the middle of it where the ground had not loaded yet; the wind for an order was spent even when the order was refused; the seat could drop you for one tick as the hand closed; loot vanished if the body was reloaded while it was still falling; his back counted as ground for somebody standing in another dimension; the goo bucket emptied on your screen when the server had kept it; and clocks, waiting orders and the list of Mountains carried over from one world into the next.

## What was new in 1.18

- **His health is back to the 6,000 it was meant to be.** Your settings file had been left on 100, which is why he felt made of paper: a whole full-size Mountain had a hundred health, so an eye was worth half a heart and a leg broke in one hit. Everything about his body is a fraction of that number, so putting it right fixed the lot — at 6,000 a leg takes 90 to break, an arm 48, and an eye is worth about 29. `/mountain health <n>` changes it.
- **His breath really pulls now.** Far out it is a tug you can walk out of. The closer you get the harder it takes hold, and inside the last thirty-odd blocks it lifts you off your feet and the ground stops being anything to hold on to. The mouth also counts as reached from further out, so you actually go down it instead of hovering in front of it. The tongue reels harder too, and it no longer gives up half way with somebody on the end of it.
- **The eye storm fires one heavy beam per target.** Not a hail of thin lines: one thick red beam with a white core comes out of one eye and lands on one of them, and each thing it has caught gets its own beam out of its own eye.
- **He varies his moves.** The last three he used are pushed right down in what he picks next, the one before last hardest, so he works through what he has instead of leaning on the same two.
- **"Where is he?" is right to the tick, and says what he is doing.** While he is walking somewhere with nobody near him the answer is worked out from his speed on the spot, not from the last time anything was written down, and it tells you where he is heading and roughly how many minutes out he is. A second line now says whether he is loaded and walking, standing, under the ground or asleep — so a distance that isn't changing tells you why. Any Mountain, not just the one the world keeps, writes down where he is every couple of seconds while he is running.
- **His insides are a place to fight in.** The room is half again as wide and half again as tall. His goo lies in pools with dry ground between them instead of covering every inch. A shelf of gristle runs round the wall at two heights with gaps in it, stairs of bone climb to the lower one at four places, twenty-odd stumps stand out of the pools at heights you can hop between, and cords hang from the roof. Fifteen mounds instead of six, and the ribs arch across the whole of it.

## What was new in 1.17

- **He keeps walking while nobody is watching.** Send him somewhere, above ground or under it, and if everybody leaves him behind the world writes the journey down: where he set off from, where he is going, and how fast he covers ground. From then on it works out where he ought to be at any moment, the finder points at that spot, and the moment anybody gets near enough to see it he is put there and carries on. A trip survives a restart, and so does the order that started it.
- **The book stays yours when you log off with it.** Log out with it on you and you are still the one who has it: your safe list goes on doing its job until somebody else actually picks it up. Saved with the world, so a restart doesn't lose it either.

### Fixed in 1.17

A pass over everything added in 1.15 and 1.16 turned up a handful of real problems, all fixed:

- **He could shut you in the seat.** Putting him to sleep, or killing him, stopped him running the part of himself that lets you get off — so you sat in an invisible seat with the camera stuck on him until he woke up or rotted. Getting on and off is handled separately from his thinking now, so it keeps working while he sleeps, while he is on his belly and all the way through dying. He also sets you down himself before lying down, and lets go of you when he falls.
- **Two people could end up on the same seat.** Somebody could take the reins while he was still lifting or lowering somebody else, which left the first one riding an invisible seat with no way out. He only takes a new driver when his hands are free.
- **Saying "never mind" mid-lift dropped you.** You were let go wherever the hand had got to, with the fall still counting. Now he sets you down the same way he would anyway.
- **A creature in his fist stayed there.** While somebody rode him the hand he had something in stopped moving, so whatever he had caught hung in it forever. It keeps being passed along now, and he drops whatever he is holding when he picks you up.
- **Pressing a name twice on the safe list could take the wrong one off.** Rows are named now rather than counted to, so a double press can't remove somebody else. Lists longer than ten also have pages instead of the extra names being unreachable.
- **The safe list did nothing in the Nether or the End.** It was reading a different, empty copy of the world's notes. Everything reads the overworld's copy now.
- **The compass had east and west the wrong way round.** Only due north and due south were right.
- **A "stand still" he was only holding for a moment could be saved forever**, and asking to be carried quietly cancelled a real one.
- A long creature name from another mod could break the safe-list packet and throw you off the server.

## What was new in 1.16

- **He picks you up in creative too.** He would walk over and then just stand there: the code that stops him grabbing people as prey was also stopping him picking up anybody in creative, even when they had asked him to. Asking to be carried is asking, and he does it now whatever mode you are in.
- **He has to be standing over you before a hand comes down.** The reach is worked out from how far his own body spreads, so it is right whether he is the size of a horse or the size of a hill.
- **"Come to me" keeps coming.** It used to be one spot noted down, so if you moved he walked to where you had been and stopped. Now he follows you until he is standing with you, tells you how far off he is on the way, and says when he is there.
- **He'll spare a whole kind of creature.** "Spare who I look at" works on anything alive, not just players. Look at a cow and he leaves cows alone; look at a creeper and he leaves creepers alone. They show up on your safe list page by name next to the people, and pressing one takes it off again.
- **The book counts wherever it is on you.** Putting it in your pack is not putting it down. Your safe list stops meaning anything when the book actually leaves you.
- **He doesn't go missing underground.** Sent to dig to a far-off spot he used to walk out of everything that was keeping him running and be left half way there. Now he keeps going the whole way, the run survives the world putting him away and picking him back up, and he tells whoever sent him where he came up.
- **Wherever he is put away is written down.** When the piece of world he is standing in stops being kept open, the spot goes in the world's own notes before he goes, so the finder still points at him and he is never simply lost.
- **"Never mind."** While he is on his way to collect you the book's line says so, instead of looking like the same button you just pressed.

## What was new in 1.15

- **He comes and picks you up.** "Come and carry me" on the book's orders page sends him walking to you. When he is standing over you a hand comes down, closes round you and lifts you up to a hollow in his back with his arms folded in over the top of you. Only when you are in it do you get him. Ask to get off and a hand takes you back out of the seat and carries you down to the ground beside his foot; he doesn't drop you and he doesn't snatch you straight back up.
- **You go with him.** Your body is not left standing in a field any more, so the world loads around him normally and there is no limit on how far you can take him. The mouse turns him, W and S walk him, the attack keys run down the left, the scroll wheel (or `[` and `]`) moves the view in and out, and **G** asks him to put you down.
- **Anything that lands a hit on you up there still throws you off**, and he still won't have you back for an hour. His own doing doesn't count — his goo, his own hands, the drop into the seat. Something else has to get you.
- **Everybody has their own safe list.** The list of people he leaves alone belongs to the player who wrote it, and only the list of whoever is **carrying the book** is in force. Put the book down and he can turn on you and on everyone you had listed, until you have it in your hands again. There's a new **Safe list** page in the book showing your own list by name; press a name to take it off, and the line under it tells you whether the list is doing anything right now. (In 1.16 the book counts anywhere on you, not just in your hand.)
- **The wait between burrow orders is yours to set.** `/mountain digcooldown <days>` — three Minecraft days to start, `0` for no wait at all.

### Fixed in 1.15

- He no longer throws you out of the seat for standing in his own goo.
- The old shared safe list from 1.13 and 1.14 is gone; add the names again on the new page.

## What was new in 1.14

- **You take him over instead of riding him.** "Step into him" on the book's orders page moves you into him. Your own body stays standing where you left it and you watch him from behind and above, far enough back to see the whole of him. The mouse turns him, W and S walk him forward and back, and the list of what every key does sits down the left of the screen with a live countdown on each line. **G** puts you back in yourself.
  - The keys are **1**–**9** and **0** for the first ten attacks and **Z X C V** for the last four.
  - **The scroll wheel pulls the view in and out**, or `[` and `]` if you'd rather hold a key. It goes from right up against him out to a long way back, and it remembers where you put it.
  - **Anything that hits the body you left behind throws you straight out of him, and he will not have you back for an hour.** You are standing there helpless while you drive him, so somewhere safe to leave yourself is now part of the fight.
  - He drops you if you stop steering for five seconds.
- **Taking out an eye costs him exactly one eye's worth.** Before, three eyes could finish a full-size one. Now each of his two hundred and ten eyes is worth one two-hundred-and-tenth of his health, so taking them all out is what kills him and nothing less. A swing that only wounds an eye still barely scratches him.
- **You can only send him underground once every five days.** "Go under to where I look" and `/mountain burrow` are a five-Minecraft-day thing now, and the book tells you how long is left. He still goes under whenever he feels like it himself.
- **"Use where I stand" works.** The button on the orders page now really fills the X and Z boxes with where you are standing.
- **Climbing a leg carries you onto his body.** At the top of a leg the leg stops being something to hold and his flank takes over, so instead of hanging under his hip you get hauled up over it and onto his side.

### Fixed in 1.14

- He no longer picks a target on the far side of the world when you order an attack and there is nothing near him, which used to throw goo at hundreds of blocks a tick and drag the whole world in behind it.
- Nothing he throws travels faster than a throw should, and he no longer builds terrain thousands of blocks away just to work out where a lob would land.

## What was new in 1.13

- **No more cutting up the body.** It was free loot for swinging at a corpse. Everything he carries drops the moment he hits the ground, and the body itself lies there for thirty seconds and then goes into the earth. Hitting it does nothing.
- **Riding puts you on his shoulders, not in his mouth.** The seat is worked out from the crest of his back just behind his neck and set clear above it, so you look out over the top of his head instead of down his own throat.
- **Climbing is completely rebuilt.** You take hold of whatever piece of him you are against and stay stuck to the outside of it. The hold is worked out again every tick from where that piece is *now*, so when he walks or turns he carries you instead of leaving you hanging in the air. You can't clip into him any more because you're always placed on the outside of the surface. And because it always grabs the nearest piece, climbing a leg puts you onto his flank at the hip and carries on up onto his back. Jump goes up, crouch goes down, letting go of both drops you off.
- **The countdowns in the book run live.** They tick down while you watch them instead of being frozen at whatever they were when the page opened.
- **The book can find him.** "Where is he?" on the orders page gives his distance, direction and coordinates, or how many days until the next one gets up.
- **A list of people he leaves alone.** "Never touch me" and "Spare who I look at" on the Him page put players on a list, saved with the world, that he will not attack whatever mood he's in. Pressing it again takes them off.
- **The book no longer mends his limbs.** (`/mountain mendlimbs` still does.)
- **Ten days, not ten minutes.** After one is killed the next takes ten Minecraft days to get up.
- **The finder costs nothing of his.** Iron, redstone and a compass, so you can make one before you have ever met him.

## What was new in 1.12

- **The book opens.** Right-click it and you get pages instead of guessing at right-clicks. Three of them. **Orders**: come to me, walk where I look, kill what I look at, leave it, stand still, lie down, climb on his head, go under to where I look, and a pair of boxes to type an X and a Z and send him there (or send him there underground). **Attacks**: every one of his fourteen attacks as a line you can press, each greying out with a countdown while he gets his breath back — twenty seconds on the one you used, three seconds before any other. **Him**: calm, hunting or guardian, take a deep breath, stop or start the goo, stop or start him wrecking blocks, cough up what's inside him, mend his limbs, forget his grudges, burst an eye. The top of every page says how far away he is, how much of him is left, what he is and what he's doing.
- **Climbing is the jump key now.** Press yourself against any part of him and hold jump to go up, crouch to come down. Holding forward does nothing special any more.
- **Riding is a line in the book.** "Climb on his head" puts you up there and "Get down" takes you off, so you don't have to know that right-clicking him was the way in. Crouching still drops you off.

## What was new in 1.11

- **There is one of him in the world, always.** A new world puts him somewhere within fifteen thousand blocks of spawn, asleep and half-buried, without you doing anything. Kill him and another gets up within ten thousand blocks of where that one fell, about ten minutes later. The world remembers where he is even when nobody is near enough to load him.
- **Something to find him with.** The Finder of the Mountain — iron, his flesh and a block of goo — tells you how far away he is, which way, and the exact coordinates. If he's dead it tells you roughly how long until the next one gets up.
- **The book.** The Mountain's Book takes three nether stars, two of his eyes, a horn, his flesh and a book, so it costs about three kills. There is only ever one of them in a world; write a second and it rots in your hands. Whoever carries it owns him. He will not touch you. Right-click nothing and he comes to you. Right-click a spot and he walks there. Right-click a creature or a player and he goes after them and does not stop. Crouch and right-click to switch him between calm, hunting and guardian, or to make him stand still.
- **Ride him.** Right-click him with the book and you're on top of his head. He walks where you look, as fast as you push forward, and crouch drops you off. His own arms leave the rider alone.
- **He burrows.** Something a long way off and he folds down into the ground, runs under it as a ridge of broken earth that throws anything standing on it, and bursts up where he was going. Nothing can touch him while he's under and he can't touch anything either. `/mountain burrow <pos>` sends him under on command.
- **He sheds.** Nearly dead, lumps of his own flesh drop off him, get up and come after whatever he's after.
- **The world gets out of his way.** Animals bolt, mobs run, anything with legs makes for open ground when he's near. Things he's hunting stand their ground.
- **Day, night and weather.** He beds down within half a minute of being left alone in daylight, and stays up for hours at night. A thunderstorm makes him faster and quicker to swing.
- **More of you, more of him.** His health goes up with the number of players fighting him, up to about three times at six.

### Fixed in 1.11

You couldn't cut up his body after all. A swing at a corpse that big can land on his own hitbox rather than one of the boxes along him, and that path refused every hit once he was dead. Both paths carve him now, and the test swings a real sword at both.

### New commands

| Command | What it does |
| --- | --- |
| `/mountain burrow <pos>` | He goes under and comes up there |
| `/mountain book [lost]` | Whether the one book exists; `lost` lets another be written |

## What was new in 1.10

- **He has his own voice.** Every sound he made was borrowed from the Warden or a ravager. He now has seventeen sounds of his own, built from scratch: four different footfalls that are almost all sub-bass, the drag of breath going in and the blast coming out, a roar, a scream, a heartbeat, the wet noise of the goo, a long collapsing death, and the sound he makes when he turns on you. `/mountain volume 40` drops him to 40%, `/mountain volume off` shuts him up entirely.
- **The ground shakes when he walks.** Each footfall shoves the camera, harder the closer you are and the bigger he is, and when he finally goes over the whole screen lurches. It never moves your aim, only your view. `/mountain shake off` if you don't want it.
- **He fights differently as he comes apart.** Past half health he stops, roars, and comes back faster, attacking more often, with a new attack: he throws his head up and hoses the whole area down with goo, a long stream of lumps fanned across the ground instead of a few careful lobs. Past a quarter he turns again and his eyes sit red even between attacks, and he gets the eye storm: every eye still open picks something of its own and they all burn at once, over and over.
- **Shoot him in the mouth.** When his face is open, anything that goes in past his teeth does six times what it would do to his hide. Nothing tells you about it and nothing glows; you either work it out or you don't.
- **You can climb all of him.** Walk into any part of him — a leg, his flank, his head, his back — and hold forward, and you go up it like a ladder. Sneak to come back down.
- **You can fight out of his hand.** When a hand closes on you, hammer the jump key. A bar of progress shows on screen, and when the fingers give you're thrown clear and he can't grab again for a few seconds. Bigger mountains take more.
- **He dies properly.** His legs buckle one at a time over several seconds, bone cracking, and he sinks lower with each one. Then he comes down: a shockwave that knocks everything around him flat, and everything he was carrying drops right there. The body lies where it fell for about a minute and a quarter — you can stand on it and cut it up with any weapon for flesh, goo, bones and the odd eye — before it goes into the ground. (`carcassSeconds` in the config changes how long.)
- **Loot worth the fight.** Mountain flesh (edible, and not pleasant), a bucket of his goo that you can pour out, one of his eyes — hold it up and every living thing within 140 blocks shows through the world for twenty seconds — a horn that calls him to you from anywhere in the world, and a full set of armour cut from his hide, a little tougher than diamond and heavy with knockback resistance.
- **He remembers you.** Hurt him and run and he keeps you on a list, saved with the world. He works out where you are and starts walking, and when he finds you he roars once and is already angry, whatever kind of mountain he is. `/mountain forgive` wipes the list.

### Fixed in 1.10.1

His back end came apart on screen while he died: every leg breaks at once as he collapses, and his back feet were being dragged about while the legs were still being worked out, which tore the model. Everything holds still now once he starts dying. His hitboxes also stopped being hittable the moment he died, so the body couldn't be cut up at all — that was the whole point of leaving it there.

### New commands

| Command | What it does |
| --- | --- |
| `/mountain volume [0-200\|off]` | How loud he is |
| `/mountain shake on\|off` | The ground shaking under his feet |
| `/mountain forgive` | He forgets everyone who has hurt him |
| `/mountain attack goo_storm` | The goo storm (also comes on its own under half health) |
| `/mountain attack eye_storm` | The eye storm (also comes on its own under a quarter) |

## What was new in 1.9

- **He shows up properly when he's big.** Four separate things were stopping him at large sizes, and all four are fixed. He is put down a long way in front of you, further the bigger he is, and that piece of the world wasn't running, so he never took his first tick: he sat out there unloaded and invisible until someone walked to him. The game also only draws a creature if the single block it counts as standing on has already been built on your screen, and at a big size you are hundreds of blocks from that block, so he wasn't drawn at all even while he was towering over your head. He is only sent to players within their own render distance of that same block, which meant standing at his tail made him vanish. And the parts of him standing in world your computer hadn't loaded read as pitch black, so he turned into a black shape. He now holds his own ground open, is sent out far enough to cover his whole length, is drawn wherever he is, and lights himself from whatever part of him you can actually see.
- **He doesn't fade into the fog.** The game fades everything out at the edge of your render distance. He's bigger than that, so at a large size you couldn't back off far enough to see all of him without standing past the fog. His own fog is pushed out to match his size.
- **Different eyes watch different things.** He picks the nearest few things around him and splits his eyes between them, so one set follows you while others track whatever else is near, instead of all 210 staring at the same thing.
- **`/mountain goto` keeps him fighting.** Sending him somewhere used to make him forget whatever he was after. He now walks to the spot and keeps fighting on the way.
- **You can tell him what to attack.** `/mountain attack <player or creature>` sends him after them, even things a guardian would normally spare, and he works down the list as they die. `/mountain attack nobody` calls him off.
- **The bars can be turned off.** `/mountain bossbar off` takes his health bar and eye bar off the top of the screen.

### New commands

| Command | What it does |
| --- | --- |
| `/mountain attack <targets>` | Sends him after those players or creatures (a selector like `@e[type=zombie]` works) |
| `/mountain attack nobody` | He forgets the list |
| `/mountain bossbar on\|off` | His health bar and eye bar at the top of the screen |

## What was new in 1.8

- **His legs and arms can be broken.** Every leg and every arm has its own health now, and his legs have their own hitboxes, so you can go for one. Enough hits and it gives way: a broken leg stops stepping and drags along behind him, and he walks slower for every leg he's lost. A broken arm hangs limp, drops whatever it was holding and can't grab again.
- **Break four on one side and he comes down.** He drops onto his belly for about seven seconds, can't walk or attack, and takes much more damage while he's down. Then he heaves himself up on one mended leg. Left alone for a while he knits the rest back together.
- **He flinches.** The part you hit jerks, and his head snaps round at whoever hit him before going back to watching.
- **Trees snap and water flies.** His feet break the trees they come down on, and stepping into water throws it up and shoves anything swimming there. (Both follow the `/mountain breakblocks` setting.)
- **He hits mobs harder than players.** Twice as hard to start with; `/mountain damage mobs <number>` changes it.
- **His gaze burns several things at once.** It lines up everything it can see near his target and sweeps from one to the next instead of picking one.
- **His own goo doesn't blind him.** Goo blocks lying around him used to block his line of sight, so he'd lose track of anything standing close.
- **He runs faster far away.** Past 150 blocks he's drawn with every 2x2x2 lump of his blocks merged into one: 574,000 faces instead of 1,418,000, and at that distance you can't tell. `/mountain detail off` turns it off, `/mountain detail <blocks>` sets when it starts.

### New commands

| Command | What it does |
| --- | --- |
| `/mountain damage` | Shows his attack and the mob multiplier |
| `/mountain damage <number>` | His base attack (10 is normal) |
| `/mountain damage mobs <number>` | How much harder he hits things that aren't players |
| `/mountain health` | Shows his health at full size, and the nearby one's |
| `/mountain health <number>` | Sets his health at full size (mountains already out there update too) |
| `/mountain detail on\|off\|<blocks>` | The simpler drawing far away, and where it starts |
| `/mountain breakleg [count]` | Breaks the legs nearest you |
| `/mountain mendlimbs` | Every leg and arm whole again |

## What was new in 1.7

- **Legs work on real terrain now.** This was the big one. To draw his legs, the game on your computer looked up the ground height from a height map that is only kept up to date on the server. On your side it was blank, so it said the ground was at the bottom of the world. On a flat world that's only a few blocks off, so it looked fine. On hills, mountains and anywhere else, the drawn legs reached down toward bedrock and fell apart. The legs now find the ground from the actual blocks, so they plant properly on hills, slopes and ledges.
- **He leans the right way on slopes.** When the ground was higher under one side of him, his body used to lean the wrong way. He now leans with the slope, and his body bends a little along its length to follow hills, so the legs on both ends can still reach the ground.
- **He spawns on the ground.** If you summoned him where the world wasn't loaded yet, he could start inside the ground and climb out. He now always starts on top.
- **No more floating black eyes.** Some of the clusters of little black eyes were hanging in the air, under his chin and next to his face, not touching him at all. Those are gone. The ones sitting where his face splits open used to get torn in half and leave holes when his mouth opened. Each bit now stays on the part of the face it sits on.
- **No more holes in his head.** When his face opened, eyes and black eyes that belonged to the other half pulled away and left holes behind. The blocks under them are filled in now.
- **Hands aren't cut off anymore.** A few hands on his back had their fingertips sliced flat by the top of the model's box, a few had chunks bitten out by eyes sitting on them, and arms that crossed each other were missing pieces. All of them are whole now. Hands resting on his head also no longer leave hand-shaped holes when they move.
- **No eyeball in his throat.** An eye buried inside his head showed up at the back of his throat when his mouth was open. It's gone.

## What was new in 1.6

- **The cut-off legs are fixed.** The model is built inside a box, and that box was too narrow. Four of his longest legs (two in the middle of each side) stuck out past its walls, so everything past the wall got sliced off. On those legs the lower part stopped under the knee with a flat bottom and hung in the air. The box is bigger now, so all four legs go all the way down and plant on the ground like the rest.
- **The back leg at his tail** had its foot sliced off the same way. It reaches the ground now too.
- **His tail end is round.** It was sliced flat by the same wall. A few hands at the ends of his arms were clipped too, and they're whole now.

## What was new in 1.5

- **No more tentacles at his feet.** The dragging tentacles that twitched around his feet are gone. He walks on 39 legs now: the crab legs and the spider legs.
- **No more dead leg stumps.** In 1.4 every leg left a short stump on his body where it joined. The stumps never moved and never reached the ground, so they looked like broken legs hanging off him. They're gone. Where a leg joins his body there is now skin underneath, so nothing shows when the leg swings.
- **Leg sweep instead of the tentacle whip.** With the tentacles gone, the leg nearest you swings out to the side and sweeps across the ground through where you're standing.

## What was new in 1.4

- **No more balls on his legs.** The round knee joints from 1.3 are gone, and his legs look the way they did before.
- **Limbs don't look cut off anymore.** Each piece of a leg or arm now carries a little of the next piece with it. When a knee, hip or elbow bends, the joint stays rounded and covered in skin instead of opening on a flat cut. His arms got this too.
- **His whole face splits open.** Before, a strip at the top and bottom of his mouth stayed shut while the rest opened. Now the whole front of his head splits in two, top to bottom.
- **The inside of his mouth.** When his face opens you see raw red gums, his teeth and a dark throat going back into him, instead of a hollow shell.
- **Three new attacks:** scream, tongue and leg stomp (see the table below).

## What was new in 1.3

- **He can sleep.** Asleep, he lies flat: his belly comes down to the ground, his legs spread out, his tail sags down low, his arms go limp and all his eyes shut. His hands won't grab you, so you can walk around on him. Wake him and his eyes open first and turn to look at you, then he gets up. See "Sleeping" below.
- **No more dirt on his feet.** The brown and black blobs spread round each foot are gone.
- **No more cut-off looking legs.** His legs were hollow inside, so a bent knee showed the empty inside and looked sliced off. The legs and arms are solid now, and each knee and hip has a round joint in the leg's own colours that covers the bend.
- **Walking on his back is smooth.** You walk straight up small bumps on him without jumping, you don't sink into him when he breathes in, and being carried and turned along with him is smooth instead of jerky.
- **New commands:** `/mountain sleep`, `/mountain goo on/off` and `/mountain breakblocks on/off`.

## What was new in 1.2

- **You can stand on his back.** Before, you fell straight through him. Now his back is solid ground you can walk on, and you get carried along when he walks and turns.
- **Leg fixes.** Feet that floated in the air, hung off the edge of a block, or stayed stuck to one spot now land on real ground and keep stepping. His knees have round joints now, so the pieces of a leg don't split apart when it bends.
- **Only one set of health bars.** Going inside him and coming back out no longer leaves a second copy of his health bars on your screen.
- **He stares less.** There are at least 35 seconds between stares now, and he only does it when you're a good distance away.
- **Two new long-range attacks:** tentacle eruption and loose eyes (see the table below).
- **Guardian mode.** A new kind of Mountain that fights monsters and leaves players alone until someone hits him.
- **`/mountain mode`** switches a Mountain between calm, hunting and guardian.

## What was new in 1.1

- Each foot plants on the real ground and stays there while his body moves over it.
- He turns like something heavy, around the middle of his body.
- You can ride him.
- Seven new attacks.
- Tentacles, leeches and loose eyes to fight inside him.
- `/mountain cleangoo` clears up his goo.

## Installing

It's already installed. `mountain-that-breathes-1.20.2.jar` is in your `.minecraft\mods` folder next to the Cerberus and Fabric API. Open the launcher, pick the **fabric-loader-1.21.1** profile and press Play.

The nine older files still sitting there — `1.17.0` through `1.20.1` — have been emptied out — there is nothing left inside them but a note, so the game loads past them instead of refusing to start. Delete both whenever you like.

To install on another PC, put `mountain-that-breathes-1.20.2.jar` and `fabric-api-0.116.17+1.21.1.jar` in that PC's mods folder. Delete any older `mountain-that-breathes` jar first, because two real copies of the mod will stop the game from starting.

## Getting him

Turn cheats on for the world, then do one of these:

- **Spawn eggs:** the Spawn Eggs tab has the **Calm**, **Hunting** and **Guardian** Mountain That Breathes Spawn Eggs.
- **Commands:** `/mountain summon calm`, `/mountain summon hunter` or `/mountain summon guardian` makes a full-size one. Add a size to make a smaller one, like `/mountain summon hunter 0.1`. Any size from 0.02 to 2 works.

**Calm** wanders and leaves you alone until you hit him or stand in front of his mouth. **Hunting** comes for the nearest player.

**Guardian** goes after zombies, skeletons, creepers and every other monster near him, and uses all his attacks on them. He leaves players, villagers and animals alone. His boss bar is green while he's friendly. If a player hurts him, the bar turns red and he fights everyone for about a minute, then calms down again. His goo trail still slows you down, so don't walk in it.

To switch one that's already out, stand near him and use `/mountain mode calm`, `/mountain mode hunter` or `/mountain mode guardian`.

## Sleeping

- **Put one to sleep:** stand near him and type `/mountain sleep`. He lies down over a few seconds.
- **Summon one already asleep:** add `asleep` to the end, like `/mountain summon calm asleep` or `/mountain summon hunter 0.3 asleep`.
- **How he wakes up:**
  - A **hunting** one wakes a few seconds after you climb on him or come close, and then he comes for you.
  - A **calm** or **guardian** one sleeps on for 30 to 60 seconds while you're on him or next to him, then slowly wakes up and leaves you alone.
  - Hitting him wakes any of them straight away, and he fights back.
  - Either way, his eyes open first, one after another, and turn to look at you. Then he stands up.
- **Sleep mode:** once you've put him to sleep, he lies back down whenever he's had nothing to do and nobody near him for about two minutes. Type `/mountain sleep` while he's asleep to wake him up and turn this off.
- **Getting on him:** his tail end is the lowest part of him when he's lying down. At full size you still need a pillar or some stairs to get up there.
- Creative and spectator players don't wake him.

## What he does

- **He walks mouth first,** on 39 legs of two wrong kinds: crab legs with the knees up above his back, and spider legs that bend the wrong way. Each foot lands, holds, and crushes what it lands on.
- **Goo pours from his mouth all the time.** It leaves a black trail that slows you down and darkens your screen. It eats a trench into soft ground and dries up after a few minutes.
- **He breathes.** He swells up and his face splits open. Breathing in pulls everything toward his mouth, and anything that reaches it gets swallowed. Breathing out is a blast that throws you back. A wall between you and his mouth protects you from the blast.
- **His arms:** about 70 on his back. They grab anyone up there and pass them hand to hand toward his mouth. Some hands have a mouth in the palm that bites.
- **His back:** you can climb up and walk around on it. You ride along when he moves, but that's where his hands and the hand slam get you.
- **His eyes:** 210 of them, and they all follow the nearest player.

## His attacks

| Attack | What happens | How to deal with it |
|---|---|---|
| **Goo lob** | He tips his head back and throws big lumps of goo high in the air. They come down on you and around you. | Keep moving. |
| **Leg sweep** | The leg nearest you swings out to the side and sweeps across the ground through where you're standing, knocking you flying. | Get out from beside him, or jump it. |
| **Hand slam** | The hands on his back rise together and slam down on whoever is up there, throwing them off. | Don't stay in one spot on his back. |
| **The stare** | Every eye turns red and stares at you. After a moment they burn you, making you weak and slow. | Get behind something solid before they fire. |
| **Body slam** | He rears his whole front end up into the sky and drops it. A shock ring rolls out across the ground. | Jump when the ring reaches you. |
| **Flood** | His face splits wide and a torrent of goo pours onto the ground in front of him. | Get out of the way. |
| **Darts** | The holes in his skin nearest you shoot fast poison darts. | Don't hang around close to him. |
| **Tentacle eruption** (far away) | Something digs under the ground from his mouth toward you, throwing up dirt as it goes. When it reaches you, 4 or 5 tentacles burst out of the ground around you. They lash and grab for about 10 seconds, then sink back down. | It follows you for the first few seconds, then it can't turn anymore. Run to the side once it stops turning. |
| **Scream** (in front) | His face splits wide open and he screams. A wave of sound rolls out in front of him. It throws you back, makes your screen swim and goes dark for a moment. | Get out from in front of his face. It spreads out as it goes, so getting to his side works best. |
| **Tongue** (in front) | A long tongue shoots out of his mouth at you. If it touches you, it wraps around you and pulls you into his mouth. If it misses, it slides back in. | Dodge sideways. If it has you, hit him and he lets go. |
| **Leg stomp** (beside him) | The walking leg nearest you lifts high over you and stamps down. | Watch for a leg rising over you and get out from under it. |
| **Loose eyes** (far away) | He tears some of his eyes off his body and throws them at you. They float around you and spit goo for about half a minute. | Hit them out of the air, or hide under something until they're gone. |

## How to beat him

- **Hit his eyes.** Hitting flesh does only a tenth of the damage. An eye takes full damage, and once popped it stays dark. The second boss bar counts the eyes still open.
- **Inside him** (if he swallows you): you land in a room with his heart hanging in the middle. Heart hits do **triple damage**. Hurt it enough and he coughs you back out.
  - The goo floor burns a little. The raised flesh mounds have no goo on them, so stand on those.
  - **Gut tentacles** burst up out of the floor near you. They either lash down at you or wrap around you and squeeze. Hit one enough and it lets go.
  - **Leeches** crawl out of the holes in the wall. If one bites you it latches onto your neck and drinks. Kill it quickly.
  - **Loose eyes** float around the room. When one glows red it's about to spit goo.
  - **His heart** squeezes hard every so often. It glows, then a wave of blood rolls across the floor and brings more leeches with it. Stand on a mound or jump to avoid the wave.
  - The more hurt he is, the more of these show up.
- **When he dies**, he takes one last huge breath and blasts it out. Then his legs fold, he sinks into the ground and vanishes, and his loot drops.

## All the commands (cheats on)

| Command | What it does |
|---|---|
| `/mountain summon [calm/hunter/guardian] [size] [asleep]` | makes one in front of you, facing you (add `asleep` to have him lying there asleep) |
| `/mountain sleep` | the nearest one lies down to sleep; type it again to wake him up |
| `/mountain goo on` / `/mountain goo off` | turns his goo on or off for every Mountain. Off means no goo trail, no goo puddles from his attacks and no goo pouring from his mouth. Type `/mountain goo` on its own to see which it is. |
| `/mountain breakblocks on` / `/mountain breakblocks off` | turns block breaking on or off for every Mountain. Off means his goo doesn't eat into the ground and his feet and slams don't crush plants, leaves or snow. Type `/mountain breakblocks` on its own to see which it is. |
| `/mountain mode <calm/hunter/guardian>` | switches the nearest one to that kind |
| `/mountain stay` | he stands still, but still turns, breathes and fights (type it again to let him wander) |
| `/mountain goto <x y z>` | walks the nearest one to a spot |
| `/mountain breathe` | makes him take a big breath right now |
| `/mountain attack <name>` | makes him do an attack right now: `goo_lob`, `leg_sweep`, `hand_slam`, `gaze`, `body_slam`, `vomit`, `darts`, `tentacle_eruption`, `loose_eyes`, `scream`, `tongue`, `leg_stomp` |
| `/mountain popeye [count]` | pops some of his eyes |
| `/mountain swallow <player>` | swallows a player straight into the heart room |
| `/mountain cough` | coughs up everyone inside him (works from inside too) |
| `/mountain cleangoo [radius]` | removes all his goo within 300 blocks (or the radius you give, up to 3000) and fills in the trenches it ate |
| `/mountain burrow <x y z>` | sends him under the ground to come up at that spot |
| `/mountain digcooldown [days]` | how many Minecraft days he'll put up with between orders to go under (2 to start, 0 for none) |
| `/mountain unmake on/off` | whether the last thing he does is in the book at all |
| `/mountain unmake unmark <player>` | lifts the mark it leaves, so a book will stay in their hands again |
| `/mountain wind on/off` | whether the book tires him at all |
| `/mountain wind seconds <n>` | how long he takes to get all his wind back (90 to start) |
| `/mountain wind grudge <rate>` | how fast he sours on people, `1` normal, `0` never |
| `/mountain settle [player]` | full wind, and he holds nothing against them |
| `/mountain where` | whether he is loaded and where, his health, and what the world's sum says while he isn't |
| `/mountain away on/off` | whether he is taken out of the world while nobody is near him |
| `/mountain away blocks <n>` | how far away the nearest player has to be for that |
| `/mountain away now` | takes the nearest one out of the world this second |
| `/mountain mendlimbs` | puts his broken legs and arms back together |
| `/mountain forgive [player]` | he forgets who has hurt him |
| `/mountain bossbar on/off` | shows or hides his health bars |
| `/mountain volume <0-100>` | how loud his own sounds are (0 turns them off) |
| `/mountain shake on/off` | whether his footfalls shake your screen |
| `/mountain book lost` | lets a new book be written after the only one was lost |
| `/mountain scars` | what every fight has left on him |
| `/mountain scars clear` | takes every scar and ruined limb off him |
| `/mountain scars on/off` | whether what is broken and mended comes back the same |
| `/mountain ward` | whether his heart is holding him off anywhere, and for how long |
| `/mountain ward off` | stops it, and lets it be used again straight away |
| `/mountain ward blocks <n>` | how far his heart holds him off; `0` turns it off |
| `/mountain limit [n]` | how many of him the world holds at once; summoning past it pushes the oldest out |
| `/mountain attack draw_in` | the long breath in |
| `/mountain attack tear_off` | he pulls a lump out of his own side |
| `/mountain unmake radius <n>` | how far out the hole he leaves goes |
| `/mountain unmake wave <n>` | how far the wave behind it carries |
| `/mountain ward minutes <n>` | how long the heart holds him off once it is woken |
| `/mountain ward rest <n>` | how many minutes it sits dark afterwards |
| `/mountain ward on` | wakes the nearest heart (or starts it where you stand) |
| `/mountain remove` | removes every Mountain in that dimension, with no loot |

## Settings

The file `.minecraft\config\mountain_breathes.json` appears after the first launch. In it you can change:

- `spawnEggScale`: the size the eggs make him
- `health`: his health
- `damageMultiplier`: how hard he hits
- `griefing`: whether the goo digs trenches and his feet flatten plants (the same as `/mountain breakblocks`)
- `gooTrail`: whether he leaves goo at all (the same as `/mountain goo`)
- `fightInside`: whether he swallows you into the heart room, or chews and spits you out instead
- `chunkLoading`: whether he keeps the area around him loaded
- `renderDistance`: how far away he's still drawn
- `burrowGapDays`: Minecraft days between one order to go under and the next, 2 to start (the same as `/mountain digcooldown`)
- `canBurrow`: whether he can be sent under the ground at all. He never goes under of his own accord either way.
- `offscreenTravel`: whether he is taken out of the world and kept as a calculation while nobody is near
- `awayBlocks`: how far the nearest player has to be before that happens; `0` follows the game's own simulation distance
- `bookCosts`: whether every line of the book takes wind out of him
- `windSeconds`: how long he takes to get all his wind back from nothing
- `grudgeRate`: how fast he sours on whoever is pushing him (`0` means never)
- `freeHits`: how many times somebody his book protects can hit him before he stops listening to it
- `unmake`: whether the last thing he does is in the book at all
- `unmakeRadius`: how wide that hole is, in blocks out from him, 1000 to start (the same as `/mountain unmake radius`)
- `unmakeWave`: how far the wave behind it carries (the same as `/mountain unmake wave`)
- `scars`: whether a broken limb comes back the same (the same as `/mountain scars`)
- `crippleAt`: what share of his legs have to be broken before he is down for good, `0.70` to start
- `maxMountains`: how many of him the world holds at once, 1 to start, `0` for no limit (the same as `/mountain limit`)
- `respawnBlocks`: how far from where he fell the next one gets up, 7000 to start
- `wardBlocks`: how far his heart holds him off while it beats, 700 to start (the same as `/mountain ward blocks`)
- `wardSeconds`: how long it holds him off for, 1200 (twenty minutes) to start (the same as `/mountain ward minutes`)
- `wardRestSeconds`: how long it sits dark afterwards, also twenty minutes (the same as `/mountain ward rest`)

## Tips

- Use a render distance of **10 or more** chunks for the full-size one.
- He needs cheats and a **1.21.1** world. Don't open your 26.x worlds in 1.21.1.
- He works alongside the Fire & Ice Cerberus mod.
- If you were inside him in 1.0, the room gets its new mounds and wall holes the next time you're swallowed.
- The easiest way onto his back is to use `/mountain stay`, build a pillar next to him, and step across.
