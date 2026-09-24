# Run /function killzai/prep_hollowbell first and let the chunks load.
execute unless entity @e[name=killzai_hollowbell] run tellraw @s {"rawtext":[{"text":"§cNo Hollowbell anchor here. Stand where you want it and run /function killzai/prep_hollowbell first, then wait for the chunks."}]}
# Hollowbell — 656,901 blocks.
# Radially symmetric. The canopy hangs high; the threads reach the ground.
# 201 x 201 footprint, 204 tall. The threads land at your feet.

execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_000 ~-81 ~0 ~-98
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_001 ~-100 ~0 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_002 ~-88 ~0 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_010 ~-81 ~64 ~-76
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_011 ~-90 ~64 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_012 ~-81 ~64 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_020 ~-63 ~128 ~-72
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_021 ~-84 ~128 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_022 ~-72 ~128 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_100 ~-36 ~0 ~-100
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_101 ~-36 ~0 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_102 ~-36 ~0 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_110 ~-36 ~64 ~-90
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_111 ~-36 ~64 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_112 ~-36 ~64 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_113 ~-13 ~81 ~92
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_120 ~-36 ~128 ~-85
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_121 ~-36 ~128 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_122 ~-36 ~128 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_131 ~-33 ~192 ~-32
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_132 ~-12 ~192 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_200 ~28 ~0 ~-84
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_201 ~28 ~0 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_202 ~28 ~0 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_210 ~28 ~64 ~-87
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_211 ~28 ~64 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_212 ~28 ~64 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_220 ~28 ~128 ~-73
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_221 ~28 ~128 ~-36
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_222 ~28 ~128 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_231 ~28 ~192 ~-18
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_301 ~92 ~0 ~-1
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_302 ~92 ~0 ~28
execute at @e[name=killzai_hollowbell,c=1] run structure load killzai:hollowbell_311 ~92 ~81 ~6
tellraw @a {"rawtext":[{"text":"§6Hollowbell §7placed — 656,901 blocks. Radially symmetric. The canopy hangs high; the threads reach the ground."}]}
