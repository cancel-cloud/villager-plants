package de.cancelcloud.villagerplants.listener

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.model.Workstation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
import java.util.UUID

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
        val loc: Location = (ws.location ?: return).clone().add(0.5, 1.0, 0.5)
        val villager = loc.world.spawn(loc, Villager::class.java)
        villager.setBaby()
        villager.isPersistent = true
        villager.removeWhenFarAway = false
        villager.persistentDataContainer.set(
            plugin.keys.owner, PersistentDataType.STRING, ws.owner.toString()
        )
        villager.persistentDataContainer.set(
            plugin.keys.workstationId, PersistentDataType.STRING, ws.id.toString()
        )
        villager.customName(Items.name("Personal Farmer", NamedTextColor.AQUA))
        villager.isCustomNameVisible = true
        ws.tool?.let { villager.equipment?.setItemInMainHand(it.clone()) }
        villager.equipment?.itemInMainHandDropChance = 0f
        ws.villagerId = villager.uniqueId
    }
}
