package de.cancelcloud.villagerplants.listener

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.model.Workstation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import com.destroystokyo.paper.entity.ai.VanillaGoal
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

class EggPlaceListener(private val plugin: VillagerPlantsPlugin) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        val player = event.player
        val clicked = event.clickedBlock ?: return
        val target = clicked.getRelative(event.blockFace)

        // Place a personal villager egg -> new workstation + baby villager.
        if (Items.isPersonalEgg(plugin, item)) {
            event.isCancelled = true
            if (!target.type.isAir) {
                player.sendMessage(Component.text("Not enough space here.", NamedTextColor.RED))
                return
            }
            item.amount -= 1
            val ws = Workstation(
                id = UUID.randomUUID(),
                owner = player.uniqueId,
                location = target.location,
            )
            placeWorkstation(ws, target)
            spawnVillager(ws)
            plugin.workstations.register(ws)
            plugin.farming.track(ws)
            player.sendMessage(
                Component.text(
                    "Personal villager spawned! Right-click the villager to choose a crop, " +
                        "right-click the workstation to manage it.",
                    NamedTextColor.GREEN,
                )
            )
            return
        }

        // Re-place a picked-up workstation item.
        val wsId = Items.workstationIdOf(plugin, item) ?: return
        event.isCancelled = true
        val ws = plugin.workstations.byId(wsId)
        if (ws == null) {
            player.sendMessage(Component.text("This workstation no longer exists.", NamedTextColor.RED))
            return
        }
        if (ws.owner != player.uniqueId) {
            player.sendMessage(Component.text("This workstation belongs to someone else.", NamedTextColor.RED))
            return
        }
        if (ws.placed) {
            player.sendMessage(Component.text("This workstation is already placed.", NamedTextColor.RED))
            return
        }
        if (!target.type.isAir) {
            player.sendMessage(Component.text("Not enough space here.", NamedTextColor.RED))
            return
        }
        item.amount -= 1
        ws.location = target.location
        placeWorkstation(ws, target)
        plugin.workstations.index(ws)
        spawnVillager(ws)
        plugin.farming.track(ws)
        player.sendMessage(Component.text("Workstation placed.", NamedTextColor.GREEN))
    }

    private fun placeWorkstation(ws: Workstation, block: Block) {
        block.type = Material.SMITHING_TABLE
    }

    private fun spawnVillager(ws: Workstation) = VillagerSpawner.spawn(plugin, ws)
}

/** Spawns (or respawns) the personal baby villager of a workstation. */
object VillagerSpawner {
    fun spawn(plugin: VillagerPlantsPlugin, ws: Workstation) {
        val loc = standSpot(ws) ?: return
        val villager = loc.world.spawn(loc, Villager::class.java)
        villager.isPersistent = true
        villager.removeWhenFarAway = false
        prepare(plugin, villager, ws)
        villager.persistentDataContainer.set(
            plugin.keys.owner, PersistentDataType.STRING, ws.owner.toString()
        )
        villager.persistentDataContainer.set(
            plugin.keys.workstationId, PersistentDataType.STRING, ws.id.toString()
        )
        villager.customName(Items.name("Personal Farmer", NamedTextColor.AQUA))
        villager.isCustomNameVisible = true
        plugin.workstations.setVillager(ws, villager.uniqueId)
    }

    /** Beside the workstation, on the ground. Standing on the table makes him jump off into the crops. */
    fun standSpot(ws: Workstation): Location? {
        val base = ws.location ?: return null
        return base.clone().add(1.5, 0.0, 0.5)
    }

    /** Keeps a loaded villager a baby with the workstation hoe. Growing up clears both. */
    fun prepare(plugin: VillagerPlantsPlugin, villager: Villager, ws: Workstation) {
        villager.setBaby()
        villager.ageLock = true
        villager.setCanPickupItems(false)
        calm(villager)
        villager.equipment.setItemInMainHand(ws.tool?.clone())
        villager.equipment.itemInMainHandDropChance = 0f
    }

    /**
     * Drops the goals that path him across the map toward a village.
     * Local random stroll stays, so he still paces between actions.
     * If that stroll was already removed, a short pace goal is added back.
     */
    fun calm(villager: Villager) {
        val goals = Bukkit.getMobGoals()
        for (key in VILLAGE_TRAVEL) {
            @Suppress("UNCHECKED_CAST")
            goals.removeGoal(villager, key as GoalKey<Villager>)
        }
        @Suppress("UNCHECKED_CAST")
        val hasStroll = goals.hasGoal(villager, VanillaGoal.RANDOM_STROLL as GoalKey<Villager>) ||
            goals.hasGoal(villager, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL as GoalKey<Villager>)
        if (!hasStroll && !goals.hasGoal(villager, PaceGoal.KEY)) {
            goals.addGoal(villager, 8, PaceGoal(villager))
        }
    }

    private val VILLAGE_TRAVEL = listOf(
        VanillaGoal.STROLL_THROUGH_VILLAGE,
        VanillaGoal.MOVE_BACK_TO_VILLAGE,
        VanillaGoal.MOVE_THROUGH_VILLAGE,
    )
}

/** Short random step, a couple of blocks, only when he is not already walking to a crop. */
private class PaceGoal(private val villager: Villager) : Goal<Villager> {
    private var ticks = 0

    override fun getKey(): GoalKey<Villager> = KEY

    override fun getTypes(): EnumSet<GoalType> = EnumSet.of(GoalType.MOVE)

    override fun shouldActivate(): Boolean =
        villager.isValid && !villager.pathfinder.hasPath() && ThreadLocalRandom.current().nextInt(80) == 0

    override fun start() {
        ticks = 0
        val angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2
        val dist = 1.5 + ThreadLocalRandom.current().nextDouble() * 2.0
        val dest = villager.location.clone().add(cos(angle) * dist, 0.0, sin(angle) * dist)
        villager.pathfinder.moveTo(dest, 0.6)
    }

    override fun shouldStayActive(): Boolean {
        ticks++
        return ticks < 40 && villager.isValid && villager.pathfinder.hasPath()
    }

    companion object {
        val KEY: GoalKey<Villager> = GoalKey.of(Villager::class.java, NamespacedKey("villagerplants", "pace"))
    }
}
