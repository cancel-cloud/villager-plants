# VillagerPlants

Purpur plugin for MC **26.2** — craft personal farmer villagers with upgradeable workstations.

## Gameplay

1. **Get an egg**: killing a *naturally spawned* villager has a 5% chance (configurable) to drop a villager spawn egg. Villagers from breeding, spawn eggs, spawners etc. never drop one.
2. **Craft**: `Gold Block + Villager Spawn Egg + Gold Block` (one row) → **Personal Villager Egg**.
3. **Place the egg** on a block: a custom workstation (smithing table with obsidian-tear particles dripping above it) is placed and your personal **baby villager** spawns on top.
4. **Right-click the villager** (owner only) → Worker UI: place the crop the villager should plant (wheat seeds, carrots, potatoes, beetroot seeds, nether wart). Items are absorbed when closing the menu; one crop type at a time.
5. **Right-click the workstation** (owner only) → main menu:
   - **Working Area** — 4x4, 8x8 or 16x16 cube the villager works in (limited by tier). The footprint stays exactly NxN but is anchored so the workstation sits as centered as an even-sized square allows.
   - **Upgrade** (smithing template icon) — Tier 2: `2x Iron Block` → 8x8 · Tier 3: `2x Gold Block` → 16x16 · Tier 4: `1x Netherite Block` → villager works 2x faster.
   - **Harvest Storage** — double-chest GUI with infinite capacity. With many item types the bottom row becomes navigation (back / previous / next) and the title shows the current page. Click an item to withdraw a stack.
   - **Pick Up** — removes villager + workstation into an item that keeps all data (tier, area, storage), so you can move it.
6. The villager plants seeds from the workstation on farmland (soul sand for nether wart) in the area and harvests **any** mature crop there whose growth is ageable — including player-planted ones and crops it didn't plant (cocoa excluded). Sweet berry bushes are picked (reset to age 1) instead of destroyed; after clearing a crop it replants from the drops when the soil matches (farmland / soul sand for nether wart). Drops go into the workstation storage. Your personal villager takes no damage, cannot be targeted by mobs and never picks up items. Only the owner can use, upgrade, move or empty the workstation; the block is protected against breaking, explosions, pistons, fire and wither-style block changes. A villager that dies is respawned automatically at the workstation.

## Tech

- Purpur API `26.2.build.2620-stable`, Kotlin 2.4.10 + **kotlinx-coroutines** 1.11.0 (custom Bukkit main-thread dispatcher, one coroutine per workstation; coroutine delays are tick-aligned via a custom `Delay` implementation on the dispatcher), Bukkit YAML for `config.yml` + `data.yml` persistence (autosave every 5 min).
- Build: `./gradlew build` (needs JDK 25; the `copyJar` task drops the jar into `purpur26-2/plugins/`).

## Config (`config.yml`)

| Key | Default | Description |
|---|---|---|
| `egg-drop-chance` | `0.05` | Egg drop chance from natural villagers |
| `work-interval-ticks` | `60` | Ticks between villager work actions |
| `netherite-speed-multiplier` | `2.0` | Tier 4 speed factor |
| `search-y-range` | `3` | Vertical scan range around the workstation |
| `particle-interval-ticks` | `10` | Ticks between workstation ambient particle bursts |
| `particle-view-distance-blocks` | `48.0` | Particles only spawn when a player is within this distance |
| `max-wander-distance-blocks` | `10.0` | How far the villager may wander from its workstation before being guided back (always covers the working area) |


## Start Command for the server

```bash
#!/bin/bash
JAVA_HOME=$(/usr/libexec/java_home -v 25)
javaCmd=$(cat <<'EOF'
java -server -Xms4096M -Xmx4096M \
-XX:+AlwaysPreTouch \
-XX:+DisableExplicitGC \
-XX:+ParallelRefProcEnabled \
-XX:+PerfDisableSharedMem \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseG1GC \
-XX:G1HeapRegionSize=8M \
-XX:G1HeapWastePercent=5 \
-XX:G1MaxNewSizePercent=40 \
-XX:G1MixedGCCountTarget=4 \
-XX:G1MixedGCLiveThresholdPercent=90 \
-XX:G1NewSizePercent=30 \
-XX:G1RSetUpdatingPauseTimePercent=5 \
-XX:G1ReservePercent=20 \
-XX:InitiatingHeapOccupancyPercent=15 \
-XX:MaxGCPauseMillis=200 \
-XX:MaxTenuringThreshold=1 \
-XX:SurvivorRatio=32 \
-Dusing.aikars.flags=https://mcflags.emc.gs \
-Daikars.new.flags=true \
-jar purpur.jar nogui
EOF
)

while true; do
eval "$javaCmd"
echo "Server restarting..."
echo "Press CTRL + C to stop."
sleep 2
done
```
