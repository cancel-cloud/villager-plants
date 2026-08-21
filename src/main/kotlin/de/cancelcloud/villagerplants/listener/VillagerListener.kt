package de.cancelcloud.villagerplants.listener

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.gui.Guis
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.ThreadLocalRandom

class VillagerListener(private val plugin: VillagerPlantsPlugin) : Listener {

    /** Tag every villager that does NOT spawn naturally. */
    @EventHandler
    fun onSpawn(event: CreatureSpawnEvent) {
        val villager = event.entity as? Villager ?: return
        val natural = event.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL ||
            event.spawnReason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
        if (natural) return
        val pdc = villager.persistentDataContainer
        if (pdc.has(plugin.keys.unnatural, PersistentDataType.BYTE)) return
        pdc.set(plugin.keys.unnatural, PersistentDataType.BYTE, 1)
    }

    /** Rare spawn-egg drop from naturally spawned villagers killed by a player. */
    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val villager = event.entity as? Villager ?: return
        val pdc = villager.persistentDataContainer

        // Personal villager died: unlink it from its workstation.
        if (pdc.has(plugin.keys.owner, PersistentDataType.STRING)) {
            plugin.workstations.byVillager(villager.uniqueId)?.let { plugin.workstations.setVillager(it, null) }
            return
        }

        if (villager.killer == null) return
        if (pdc.has(plugin.keys.unnatural, PersistentDataType.BYTE)) return
        if (ThreadLocalRandom.current().nextDouble() < plugin.eggDropChance) {
            event.drops.add(ItemStack(Material.VILLAGER_SPAWN_EGG))
        }
    }

    /** Right-click a personal villager: owner opens the worker UI. */
    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val villager = event.rightClicked as? Villager ?: return
        val ownerRaw = villager.persistentDataContainer
            .get(plugin.keys.owner, PersistentDataType.STRING) ?: return

        event.isCancelled = true
        val player: Player = event.player
        if (player.uniqueId.toString() != ownerRaw) {
            player.sendMessage(Component.text("This villager belongs to someone else.", NamedTextColor.RED))
            return
        }
        val ws = plugin.workstations.byVillager(villager.uniqueId)
        if (ws == null) {
            player.sendMessage(Component.text("This villager has no workstation.", NamedTextColor.RED))
            return
        }
        Guis.openWorker(plugin, player, ws)
    }

    /** Personal workers take no damage. */
    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (isWorker(event.entity)) event.isCancelled = true
    }

    /** Mobs must not target personal workers. */
    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetEvent) {
        val target = event.target ?: return
        if (isWorker(target)) event.isCancelled = true
    }

    private fun isWorker(entity: Entity): Boolean =
        entity.persistentDataContainer.has(plugin.keys.workstationId, PersistentDataType.STRING) &&
            plugin.workstations.byVillager(entity.uniqueId) != null
}
