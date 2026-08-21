package de.cancelcloud.villagerplants.listener

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.gui.Guis
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class WorkstationBlockListener(private val plugin: VillagerPlantsPlugin) : Listener {

    /** Right-click the workstation: owner opens the main GUI. */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        val ws = plugin.workstations.at(block) ?: return

        event.isCancelled = true
        if (ws.owner != event.player.uniqueId) {
            event.player.sendMessage(
                Component.text("Only the owner can use this workstation.", NamedTextColor.RED)
            )
            return
        }
        Guis.openMain(plugin, event.player, ws)
    }

    /** The workstation block can only be moved via the pickup button. */
    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        val ws = plugin.workstations.at(event.block) ?: return
        event.isCancelled = true
        if (ws.owner == event.player.uniqueId) {
            event.player.sendMessage(
                Component.text("Use the workstation menu to pick it up.", NamedTextColor.YELLOW)
            )
        }
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { plugin.workstations.at(it) != null }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { plugin.workstations.at(it) != null }
    }

    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { plugin.workstations.at(it) != null }) event.isCancelled = true
    }

    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { plugin.workstations.at(it) != null }) event.isCancelled = true
    }
}
