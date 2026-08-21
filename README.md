# VillagerPlants

Purpur plugin for MC **26.1.2** — craft personal farmer villagers with upgradeable workstations.

## Gameplay

1. **Get an egg**: killing a *naturally spawned* villager has a 5% chance (configurable) to drop a villager spawn egg. Villagers from breeding, spawn eggs, spawners etc. never drop one.
2. **Craft**: `Gold Block + Villager Spawn Egg + Gold Block` (one row) → **Personal Villager Egg**.
3. **Place the egg** on a block: a custom workstation (smithing table with obsidian-tear particles dripping above it) is placed and your personal **baby villager** spawns on top.
4. **Right-click the villager** (owner only) → Worker UI: place the crop the villager should plant (wheat seeds, carrots, potatoes, beetroot seeds, nether wart). Items are absorbed when closing the menu; one crop type at a time.
5. **Right-click the workstation** (owner only) → main menu:
   - **Working Area** — 4x4, 8x8 or 16x16 cube the villager works in (limited by tier).
   - **Upgrade** (smithing template icon) — Tier 2: `2x Iron Block` → 8x8 · Tier 3: `2x Gold Block` → 16x16 · Tier 4: `1x Netherite Block` → villager works 2x faster.
   - **Harvest Storage** — double-chest GUI with infinite capacity. With many item types the bottom row becomes navigation (back / previous / next) and the title shows the current page. Click an item to withdraw a stack.
   - **Pick Up** — removes villager + workstation into an item that keeps all data (tier, area, storage), so you can move it.
6. The villager plants seeds from the workstation on farmland (soul sand for nether wart) in the area and harvests **any** mature crop there — including player-planted ones. Drops go into the workstation storage. Only the owner can use, upgrade, move or empty the workstation; the block is protected against breaking, explosions and pistons. A villager that dies is respawned automatically at the workstation.

## Tech

- Purpur API `26.1.2.build.2591-stable`, Kotlin + **kotlinx-coroutines** (custom Bukkit main-thread dispatcher, one coroutine per workstation), Bukkit YAML for `config.yml` + `data.yml` persistence (autosave every 5 min).
- Build: `./gradlew build` (needs JDK 25; the `copyJar` task drops the jar into `purpur26-1-2/plugins/`).

## Config (`config.yml`)

| Key | Default | Description |
|---|---|---|
| `egg-drop-chance` | `0.05` | Egg drop chance from natural villagers |
| `work-interval-ticks` | `60` | Ticks between villager work actions |
| `netherite-speed-multiplier` | `2.0` | Tier 4 speed factor |
| `search-y-range` | `3` | Vertical scan range around the workstation |


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
