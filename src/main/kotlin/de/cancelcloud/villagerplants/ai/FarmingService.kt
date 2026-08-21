package de.cancelcloud.villagerplants.ai

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.delayTicks
import de.cancelcloud.villagerplants.listener.VillagerSpawner
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Workstation
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Runs one coroutine per placed workstation that lets its villager plant and
 * harvest crops, plus a global particle loop marking every workstation.
 */
class FarmingService(private val plugin: VillagerPlantsPlugin) {

    private val jobs = hashMapOf<UUID, Job>()

    fun start() {
        // Ambient workstation particles, spawned only while a player is nearby.
        plugin.scope.launch {
            while (isActive) {
                delayTicks(plugin.particleIntervalTicks)
                val viewDistanceSq = plugin.particleViewDistanceBlocks * plugin.particleViewDistanceBlocks
                for (ws in plugin.workstations.all()) {
                    val loc = ws.location ?: continue
                    if (!loc.isChunkLoaded) continue
                    val world = loc.world
                    val centerX = loc.x + 0.5
                    val centerY = loc.y + 1.2
                    val centerZ = loc.z + 0.5
                    var hasViewer = false
                    for (player in world.players) {
                        val pLoc = player.location
                        val dx = pLoc.x - centerX
                        val dy = pLoc.y - centerY
                        val dz = pLoc.z - centerZ
                        if (dx * dx + dy * dy + dz * dz <= viewDistanceSq) {
                            hasViewer = true
                            break
                        }
                    }
                    if (!hasViewer) continue
                    val center = loc.clone().add(0.5, 1.2, 0.5)
                    world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, center, 1, 0.25, 0.25, 0.25, 0.0)
                    world.spawnParticle(Particle.PORTAL, center, 5, 0.3, 0.2, 0.3, 0.02)
                }
            }
        }
        // Farming loops for everything loaded from disk.
        for (ws in plugin.workstations.all()) {
            if (ws.placed) track(ws)
        }
    }

    fun track(ws: Workstation) {
        if (jobs[ws.id]?.isActive == true) return
        jobs[ws.id] = plugin.scope.launch {
            while (isActive && ws.placed) {
                val interval = if (ws.tier >= 4)
                    (plugin.workIntervalTicks / plugin.netheriteSpeedMultiplier).toLong().coerceAtLeast(1)
                else
                    plugin.workIntervalTicks
                delayTicks(interval)
                runCatching { workCycle(ws) }.onFailure {
                    plugin.logger.warning("Farming cycle failed for ${ws.id}: ${it.message}")
                }
            }
        }
    }

    fun untrack(ws: Workstation) {
        jobs.remove(ws.id)?.cancel()
    }

    // ---------------------------------------------------------------- work cycle

    private suspend fun workCycle(ws: Workstation) {
        val loc = ws.location ?: return
        if (!loc.isChunkLoaded) return

        val villager = resolveVillager(ws) ?: return

        // Keep the villager near its working area instead of wandering off.
        if (!containVillager(ws, villager)) return

        val crop = ws.crop ?: return

        val scan = scanArea(ws, crop)

        // 1) Harvest any mature crop in the area - no matter who planted it.
        val harvestable = scan.harvestable
        if (harvestable != null) {
            moveAndAwait(villager, harvestable.location)
            harvest(ws, villager, harvestable)
            return
        }

        // 2) Plant a new crop if seeds are available (replenish from storage if needed).
        if (ws.seedCount <= 0 && (ws.storage[crop.seed] ?: 0) > 0) {
            ws.seedCount += ws.withdraw(crop.seed, 64)
        }
        if (ws.seedCount > 0) {
            val plantable = scan.plantable
            if (plantable != null) {
                moveAndAwait(villager, plantable.location)
                plant(ws, villager, plantable, crop)
            }
        }
    }

    private fun resolveVillager(ws: Workstation): Villager? {
        val existing = ws.villagerId?.let { Bukkit.getEntity(it) } as? Villager
        if (existing != null && existing.isValid) return existing
        // Respawn a missing villager at the workstation.
        VillagerSpawner.spawn(plugin, ws)
        return ws.villagerId?.let { Bukkit.getEntity(it) } as? Villager
    }

    /**
     * Returns false if the villager was out of bounds and is being brought back
     * (skipping this work cycle).
     */
    private suspend fun containVillager(ws: Workstation, villager: Villager): Boolean {
        val home = (ws.location ?: return false).clone().add(0.5, 1.0, 0.5)
        val maxDistance = maxOf(plugin.maxWanderDistanceBlocks, ws.areaSize / 2.0 + 1.0)
        val distSq = if (villager.world == home.world)
            villager.location.distanceSquared(home)
        else
            Double.MAX_VALUE

        if (distSq <= maxDistance * maxDistance) return true
        if (distSq > 32.0 * 32.0) {
            villager.teleport(home) // lost it completely - bring it home
            return true
        }
        villager.pathfinder.moveTo(home, 1.0)
        delayTicks(20)
        return false
    }

    /** Walks the villager towards the target and waits until it stands on it (or times out). */
    private suspend fun moveAndAwait(villager: Villager, target: Location) {
        val dest = target.clone().add(0.5, 0.0, 0.5)
        villager.pathfinder.moveTo(dest, 1.0)
        var waited = 0L
        while (waited < 200 && villager.isValid) { // max 10 seconds
            if (villager.location.distanceSquared(dest) <= 2.25) break // within 1.5 blocks
            delayTicks(10)
            waited += 10
            if (!villager.pathfinder.hasPath()) villager.pathfinder.moveTo(dest, 1.0)
        }
        // Let the villager settle on the spot before it works.
        villager.pathfinder.stopPathfinding()
        villager.lookAt(dest)
        delayTicks(8)
    }

    // ---------------------------------------------------------------- block scanning

    private fun areaBlocks(ws: Workstation): Sequence<Block> {
        val center = ws.location ?: return emptySequence()
        val world = center.world
        val half = ws.areaSize / 2
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        val yRange = plugin.searchYRange
        return sequence {
            for (x in (cx - half + 1)..(cx + half)) {
                for (z in (cz - half + 1)..(cz + half)) {
                    if (!world.isChunkLoaded(x shr 4, z shr 4)) continue
                    for (y in (cy - yRange)..(cy + yRange)) {
                        yield(world.getBlockAt(x, y, z))
                    }
                }
            }
        }
    }

    /** First mature crop block and first free planting spot in one pass. */
    private fun scanArea(ws: Workstation, crop: Crop): ScanResult {
        var harvestable: Block? = null
        var plantable: Block? = null
        for (block in areaBlocks(ws)) {
            if (harvestable == null && isMature(block)) {
                harvestable = block
            } else if (plantable == null && block.type == crop.soil && block.getRelative(0, 1, 0).type.isAir) {
                plantable = block.getRelative(0, 1, 0)
            }
            if (harvestable != null && plantable != null) break
        }
        return ScanResult(harvestable, plantable)
    }

    private class ScanResult(val harvestable: Block?, val plantable: Block?)

    private fun isMature(block: Block): Boolean {
        val data = block.blockData as? Ageable ?: return false
        return data.age >= data.maximumAge
    }

    // ---------------------------------------------------------------- actions

    private val replantItems = mapOf(
        Material.WHEAT to Material.WHEAT_SEEDS,
        Material.CARROTS to Material.CARROT,
        Material.POTATOES to Material.POTATO,
        Material.BEETROOTS to Material.BEETROOT_SEEDS,
        Material.NETHER_WART to Material.NETHER_WART,
        Material.MELON_STEM to Material.MELON_SEEDS,
        Material.PUMPKIN_STEM to Material.PUMPKIN_SEEDS,
    )

    private fun harvest(ws: Workstation, villager: Villager, block: Block) {
        if (!isMature(block)) return
        val type = block.type
        val blockData = block.blockData
        // The player-provided tool affects the drops (e.g. Fortune on a hoe).
        val drops = (ws.tool?.let { block.getDrops(it) } ?: block.getDrops())
            .filter { !it.type.isAir && it.amount > 0 }
            .toMutableList()

        if (type == Material.SWEET_BERRY_BUSH) {
            val ageable = blockData as Ageable
            ageable.age = 1
            block.blockData = ageable
        } else {
            block.type = Material.AIR
            replant(block, type, drops)
        }

        for (drop in drops) ws.store(drop.type, drop.amount.toLong())

        block.world.spawnParticle(
            Particle.BLOCK,
            block.location.add(0.5, 0.5, 0.5),
            25, 0.25, 0.25, 0.25,
            blockData,
        )
        villager.swingMainHand()
        block.world.playSound(block.location, Sound.BLOCK_CROP_BREAK, 1f, 1f)
    }

    private fun replant(block: Block, type: Material, drops: MutableList<ItemStack>) {
        val plantItem = replantItems[type] ?: return
        val soil = if (type == Material.NETHER_WART) Material.SOUL_SAND else Material.FARMLAND
        if (block.getRelative(0, -1, 0).type != soil) return
        val index = drops.indexOfFirst { it.type == plantItem }
        if (index < 0) return
        val drop = drops[index]
        drop.amount -= 1
        if (drop.amount <= 0) drops.removeAt(index)
        block.type = type
    }

    private fun plant(ws: Workstation, villager: Villager, block: Block, crop: Crop) {
        if (!block.type.isAir) return
        if (block.getRelative(0, -1, 0).type != crop.soil) return
        if (ws.seedCount <= 0) return
        ws.seedCount -= 1
        block.type = crop.block
        villager.swingMainHand()
        block.world.playSound(block.location, Sound.ITEM_CROP_PLANT, 1f, 1f)
    }
}
