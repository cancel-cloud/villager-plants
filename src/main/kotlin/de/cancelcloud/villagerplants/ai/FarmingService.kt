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
import org.bukkit.entity.Villager
import java.util.UUID

/**
 * Runs one coroutine per placed workstation that lets its villager plant and
 * harvest crops, plus a global particle loop marking every workstation.
 */
class FarmingService(private val plugin: VillagerPlantsPlugin) {

    private val jobs = hashMapOf<UUID, Job>()

    fun start() {
        // Obsidian tear particles above every placed workstation.
        plugin.scope.launch {
            while (isActive) {
                delayTicks(10)
                for (ws in plugin.workstations.all()) {
                    val loc = ws.location ?: continue
                    if (!loc.isChunkLoaded) continue
                    val center = loc.clone().add(0.5, 1.2, 0.5)
                    loc.world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, center, 3, 0.25, 0.25, 0.25, 0.0)
                    loc.world.spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, center, 1, 0.2, 0.1, 0.2, 0.0)
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

        // 1) Harvest any mature crop in the area - no matter who planted it.
        val harvestable = findHarvestable(ws)
        if (harvestable != null) {
            moveAndAwait(villager, harvestable.location)
            if (harvestable.type == crop.block || Crop.byBlock(harvestable.type) != null) {
                harvest(ws, villager, harvestable)
            }
            return
        }

        // 2) Plant a new crop if seeds are available (replenish from storage if needed).
        if (ws.input <= 0 && (ws.storage[crop.seed] ?: 0) > 0) {
            ws.input += ws.takeDrop(crop.seed, 64)
        }
        if (ws.input > 0) {
            val plantable = findPlantable(ws, crop)
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
        val maxDistance = ws.areaSize / 2.0 + 6.0
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
            for (x in (cx - half) until (cx + half)) {
                for (z in (cz - half) until (cz + half)) {
                    for (y in (cy - yRange)..(cy + yRange)) {
                        yield(world.getBlockAt(x, y, z))
                    }
                }
            }
        }
    }

    private fun findHarvestable(ws: Workstation): Block? =
        areaBlocks(ws).firstOrNull { block ->
            Crop.byBlock(block.type) != null && isMature(block)
        }

    private fun isMature(block: Block): Boolean {
        val data = block.blockData as? org.bukkit.block.data.Ageable ?: return false
        return data.age >= data.maximumAge
    }

    private fun findPlantable(ws: Workstation, crop: Crop): Block? =
        areaBlocks(ws).firstOrNull { block ->
            block.type == crop.soil && block.getRelative(0, 1, 0).type.isAir
        }?.getRelative(0, 1, 0)

    // ---------------------------------------------------------------- actions

    private fun harvest(ws: Workstation, villager: Villager, block: Block) {
        if (!isMature(block)) return
        // The player-provided tool affects the drops (e.g. Fortune on a hoe).
        val drops = ws.tool?.let { block.getDrops(it) } ?: block.getDrops()
        val blockData = block.blockData
        block.type = Material.AIR

        // Replenish first: keep one seed for replanting the spot just harvested.
        val crop = ws.crop
        var seedKept = false
        for (drop in drops) {
            var amount = drop.amount.toLong()
            if (!seedKept && crop != null && drop.type == crop.seed && amount > 0) {
                ws.input += 1
                amount -= 1
                seedKept = true
            }
            ws.addDrop(drop.type, amount)
        }

        block.world.spawnParticle(
            Particle.BLOCK,
            block.location.add(0.5, 0.5, 0.5),
            25, 0.25, 0.25, 0.25,
            blockData,
        )
        villager.swingMainHand()
        block.world.playSound(block.location, Sound.BLOCK_CROP_BREAK, 1f, 1f)
    }

    private fun plant(ws: Workstation, villager: Villager, block: Block, crop: Crop) {
        if (!block.type.isAir) return
        if (block.getRelative(0, -1, 0).type != crop.soil) return
        if (ws.input <= 0) return
        ws.input -= 1
        block.type = crop.block
        villager.swingMainHand()
        block.world.playSound(block.location, Sound.ITEM_CROP_PLANT, 1f, 1f)
    }
}
