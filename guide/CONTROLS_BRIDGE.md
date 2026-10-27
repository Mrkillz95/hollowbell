# Giants Controls: the bridge (version 1)

Giants Performance has a **Controls** tab (the second tab at the top of the screen). It lists every key of every
installed giant, and the Guide's own, grouped by giant. Each key shows what it does, its current key, and a
**Reset** button. **Reset all** is at the top. Keys that clash are shown red, as in Minecraft's Controls. It is
client-only and per player: nothing goes to the server, and there are no operator checks.

Rebinding works the same way as Minecraft's own Controls screen: `options.setKey(key, input)`, then
`KeyMapping.resetMapping()`, then `options.save()`. A change made on the tab shows in Minecraft's Controls, and a
change made there shows on the tab.

A giant mod needs nothing new for its keys to show. This file covers the optional extras: plain words for what
each key does, extra categories, and **gestures**.

## How the tab finds a giant's keys

It reads Minecraft's `options.keyMappings`. A key belongs to a giant when any one of these is true:

- its category is `key.categories.<his mod id>`;
- its category starts with `key.categories.<his mod id>_` (for example Swarmforge's `key.categories.swarmforge_build`);
- its own name starts with `key.<his mod id>.`;
- his `giants_controls.json` lists its category under `categories`.

The Guide's own keys are under `key.categories.jj_giants`. Keys from Minecraft or other mods are not listed, but
the clash check still counts them. The mod ids are the guide's `Giant.modId()`: `mountain_breathes` (Pitchgut),
`furrowmaw`, `fire_ice_cerberus`, `hollowbell`, `lanternwillow`, `wreckback`, `swarmforge` and `gloamwick`.

**Clashes.** Two keys on the same input clash, unless they are the *same job on two different giants*. The job is
the last part of the key's name, so `key.hollowbell.armour_power` and `key.gloamwick.armour_power` share R on
purpose, and every `attack_look` shares V (Swarmforge's `go_after_look` counts as `attack_look`). Each giant reads
his key only when it is his turn (his armour on, his book in hand), so a shared key is fine. Name a new key the
same way in every mod (for example `key.<mod id>.sit_on_giant`) and it shares the same way.

## `assets/<mod id>/giants_controls.json`

Ship this file in your jar. The guide reads it once, from the mod with that id. Every field is optional.

```json
{
  "controls": 1,
  "categories": ["key.categories.swarmforge_build"],
  "keys": {
    "key.hollowbell.armour_power": "With his full armour on: Great Toll.",
    "key.hollowbell.sit_on_giant": "Standing on him (on his safe list): sit down on him."
  },
  "gestures": [
    {
      "id": "triple_crouch_sit",
      "name": "Triple crouch to sit",
      "description": "Crouch three times within a second while standing on him (on his safe list) to sit down.",
      "config": "tripleCrouchSit",
      "key": "key.hollowbell.sit_on_giant",
      "default": true
    }
  ]
}
```

| field | meaning |
|---|---|
| `controls` | the file's version. If it is newer than the guide's (1), the guide skips the whole file. |
| `categories` | extra Controls categories that are yours, if the rules above don't already catch them (at most 8). |
| `keys` | what each key does, in plain words (at most 240 characters each, 128 keys). It can also be a language key. Without it, the guide uses its own words for that key, or for its job (`armour_power`, `attack_look`, `sit_on_giant`), or else just the key's own name. |
| `gestures` | things done without a key of their own, each turned on or off by one of your client settings (at most 16). |

Each gesture:

| field | meaning |
|---|---|
| `id` | its own id: lower-case letters, digits, `_ . -`, 64 at most. Needed. |
| `name` | its name on the toggle (48 characters, or a language key). Needed. |
| `description` | one line on what it does (240 characters, or a language key). `what` is accepted too. |
| `config` | the name of your client setting that turns it on or off: letters, digits, `_ . -`. Needed. |
| `key` | the key mapping it goes with. The toggle sits on that key's row. Leave it out, or name a key that isn't there, and the gesture gets a row of its own. |
| `default` | `true` or `false`: on or off to start with (true if left out). Used by Reset all. |

The guide checks everything. A gesture it can't use is left out and the rest still show. A file that isn't JSON
is ignored.

## Gesture on/off: two bridge actions

The toggle reads and sets your setting through your existing `GiantsBridge.giants(server, action, arg)`, on the
player's own game, with `server` null:

- **`controlget`**, arg the gesture's `config` name. Return `["true"]` or `["false"]`.
- **`controlset`**, arg `{"controls":1,"config":"tripleCrouchSit","value":false}`. Apply it, save it in your own
  client settings file, and return any non-null list (for example `["ok"]`).

Return `null` for a `config` name you don't know, as you already do for an unknown action. Until a mod answers
`controlget`, its gestures still show, but greyed out with "?", and the tooltip says that mod needs to be newer.

## The "sit on giant" control (for all 8 mods)

What the guide expects from each mod:

1. A normal key mapping `key.<mod id>.sit_on_giant` ("Sit on giant"), unbound by default, in your own key
   category. It shows on the tab, and shares with the other giants' sit keys without a clash.
2. A client setting such as `tripleCrouchSit` (default on), answered through `controlget` and `controlset`.
3. In `assets/<mod id>/giants_controls.json`, the gesture above, with `"key": "key.<mod id>.sit_on_giant"` so the
   **Triple crouch to sit: On/Off** toggle sits beside the key.

## Testing

`src/gametest/java/net/jj/giants/test/ControlsGameTests.java` covers the grouping, the clash rules, reading this
file (good, bad and from a jar), and the two bridge actions against `net.jj.fakeperf.GiantsBridge`. With
`-Pgiants` it also checks that every key and category in each real giant's language file lands in his group.
`tools/controls_autotest.txt` takes the tab's pictures. Set `GUIDE_SCRIPT` to its full path, then run
`runAutotest -Pgiants`.
