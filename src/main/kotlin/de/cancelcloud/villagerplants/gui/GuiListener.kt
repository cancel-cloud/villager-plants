package de.cancelcloud.villagerplants.gui

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Tiers
import de.cancelcloud.villagerplants.util.giveOrDrop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack

class GuiListener(private val plugin: VillagerPlantsPlugin) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? VPHolder ?: return
        val player = event.whoClicked as? Player ?: return
        val ws = holder.ws

        when (holder.type) {
            GuiType.WORKER -> handleWorkerClick(event)
            GuiType.MAIN -> {
                if (event.rawSlot >= event.inventory.size) {
                    // Bottom inventory: allow normal interaction, block shift-clicking into the GUI.
                    if (event.isShiftClick) event.isCancelled = true
                    return
                }
                event.isCancelled = true
                when (event.rawSlot) {
                    Guis.MAIN_CROP_SLOT -> if (event.isShiftClick) refundSeeds(player, holder)
                    Guis.MAIN_TOOL_SLOT -> handleToolClick(event, player, holder)
                    Guis.MAIN_UPGRADE_SLOT -> Guis.openUpgrade(plugin, player, ws)
                    Guis.MAIN_AREA_SLOT -> Guis.openArea(plugin, player, ws)
                    Guis.MAIN_HARVEST_SLOT -> Guis.openHarvest(plugin, player, ws)
                    Guis.MAIN_PICKUP_SLOT -> pickup(player, holder)
                }
            }
            GuiType.UPGRADE -> {
                event.isCancelled = true
                if (event.rawSlot >= event.inventory.size) return
                when (event.rawSlot) {
                    Guis.UPGRADE_CONFIRM_SLOT -> upgrade(player, holder)
                    Guis.BACK_SLOT -> Guis.openMain(plugin, player, ws)
                }
            }
            GuiType.AREA -> {
                event.isCancelled = true
                if (event.rawSlot >= event.inventory.size) return
                when (event.rawSlot) {
                    Guis.AREA_SMALL_SLOT -> selectArea(player, holder, 4)
                    Guis.AREA_MEDIUM_SLOT -> selectArea(player, holder, 8)
                    Guis.AREA_LARGE_SLOT -> selectArea(player, holder, 16)
                    Guis.BACK_SLOT -> Guis.openMain(plugin, player, ws)
                }
            }
            GuiType.HARVEST -> handleHarvestClick(event, player, holder)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder as? VPHolder ?: return
        if (holder.type != GuiType.WORKER) {
            if (event.rawSlots.any { it < event.inventory.size }) event.isCancelled = true
            return
        }
        // Worker GUI: only allow dragging valid seeds into the input slots.
        val intoTop = event.rawSlots.filter { it < event.inventory.size }
        if (intoTop.isEmpty()) return
        val validSlots = intoTop.all { it in Guis.WORKER_INPUT_SLOTS.toList() }
        val validItem = Crop.bySeed(event.oldCursor.type) != null
        if (!validSlots || !validItem) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? VPHolder ?: return
        if (holder.type != GuiType.WORKER) return
        val player = event.player as? Player ?: return
        absorbWorkerItems(player, holder)
    }

    // ---------------------------------------------------------------- worker

    private fun handleWorkerClick(event: InventoryClickEvent) {
        val topSize = event.inventory.size
        if (event.rawSlot < topSize) {
            // Click inside the GUI: only input slots are interactive.
            if (event.rawSlot !in Guis.WORKER_INPUT_SLOTS) {
                event.isCancelled = true
                if (event.rawSlot == Guis.WORKER_INFO_SLOT && event.isShiftClick) {
                    val holder = event.inventory.holder as? VPHolder ?: return
                    val player = event.whoClicked as? Player ?: return
                    refundSeeds(player, holder)
                }
                return
            }
            val cursor = event.cursor
            if (!cursor.type.isAir && Crop.bySeed(cursor.type) == null) {
                event.isCancelled = true // only seeds may be placed
            }
        } else {
            // Click in the player's own inventory: block shift-clicking invalid items in.
            if (event.isShiftClick) {
                val item = event.currentItem ?: return
                if (Crop.bySeed(item.type) == null) {
                    event.isCancelled = true
                }
            }
        }
    }

    private fun absorbWorkerItems(player: Player, holder: VPHolder) {
        val ws = holder.ws
        val inv = holder.inventory
        val rejected = mutableListOf<ItemStack>()

        for (slot in Guis.WORKER_INPUT_SLOTS) {
            val item = inv.getItem(slot) ?: continue
            if (item.type.isAir) continue
            inv.setItem(slot, null)

            val crop = Crop.bySeed(item.type)
            if (crop == null) {
                rejected += item
                continue
            }
            if (ws.crop == null || ws.seedCount <= 0) {
                ws.crop = crop
            }
            if (crop == ws.crop) {
                ws.seedCount += item.amount
            } else {
                rejected += item
            }
        }

        for (item in rejected) {
            player.giveOrDrop(item)
        }
        if (rejected.isNotEmpty()) {
            player.sendMessage(
                Component.text(
                    "The workstation only accepts one crop at a time (${ws.crop?.display ?: "none"}).",
                    NamedTextColor.YELLOW,
                )
            )
        }
    }

    /** Gives all stored seeds back to the player and frees the crop slot. */
    private fun refundSeeds(player: Player, holder: VPHolder) {
        val ws = holder.ws
        val crop = ws.crop
        if (crop == null || ws.seedCount <= 0) {
            ws.crop = null
            refreshCropInfo(holder)
            return
        }
        val total = ws.seedCount
        var remaining = ws.seedCount
        ws.seedCount = 0
        ws.crop = null
        while (remaining > 0) {
            val amount = minOf(remaining, crop.seed.maxStackSize.toLong())
            remaining -= amount
            player.giveOrDrop(ItemStack(crop.seed, amount.toInt()))
        }
        player.sendMessage(
            Component.text(
                "Took back $total x ${crop.display}. Deposit a new crop via the villager.",
                NamedTextColor.GREEN,
            )
        )
        refreshCropInfo(holder)
    }

    private fun refreshCropInfo(holder: VPHolder) {
        val player = holder.inventory.viewers.firstOrNull() as? Player ?: return
        when (holder.type) {
            GuiType.MAIN -> Guis.openMain(plugin, player, holder.ws)
            GuiType.WORKER -> Guis.openWorker(plugin, player, holder.ws)
            else -> {}
        }
    }

    private fun handleToolClick(event: InventoryClickEvent, player: Player, holder: VPHolder) {
        val ws = holder.ws
        val cursor = event.cursor

        if (cursor.type.isAir) {
            // Take the tool back.
            val tool = ws.tool ?: return
            ws.tool = null
            player.setItemOnCursor(tool)
            updateVillagerHand(ws)
            holder.inventory.setItem(Guis.MAIN_TOOL_SLOT, Guis.toolButton(ws))
            return
        }

        if (!cursor.type.name.endsWith("_HOE")) {
            player.sendMessage(
                Component.text("The villager only works with hoes.", NamedTextColor.RED)
            )
            return
        }
        if (cursor.amount != 1) {
            player.sendMessage(
                Component.text("Place a single hoe, not a stack.", NamedTextColor.RED)
            )
            return
        }
        val previous = ws.tool
        ws.tool = cursor.clone()
        player.setItemOnCursor(previous)
        updateVillagerHand(ws)
        holder.inventory.setItem(Guis.MAIN_TOOL_SLOT, Guis.toolButton(ws))
        player.sendMessage(
            Component.text("The villager now harvests with your tool.", NamedTextColor.GREEN)
        )
    }

    private fun updateVillagerHand(ws: de.cancelcloud.villagerplants.model.Workstation) {
        val villager = ws.villagerId?.let { Bukkit.getEntity(it) } as? org.bukkit.entity.Villager ?: return
        villager.equipment?.setItemInMainHand(ws.tool?.clone())
        villager.equipment?.itemInMainHandDropChance = 0f // never drop the player's tool
    }

    // ---------------------------------------------------------------- main actions

    private fun pickup(player: Player, holder: VPHolder) {
        val ws = holder.ws
        val loc = ws.location ?: return

        ws.villagerId?.let { id ->
            Bukkit.getEntity(id)?.remove()
        }
        plugin.workstations.setVillager(ws, null)
        loc.block.type = Material.AIR
        ws.location = null
        plugin.workstations.index(ws)
        plugin.farming.untrack(ws)

        player.closeInventory()
        val item = Items.workstationItem(plugin, ws)
        player.giveOrDrop(item)
        player.sendMessage(Component.text("Workstation picked up.", NamedTextColor.GREEN))
        plugin.workstations.saveAsync()
    }

    private fun upgrade(player: Player, holder: VPHolder) {
        val ws = holder.ws
        if (ws.tier >= Tiers.MAX) return
        val next = ws.tier + 1
        val (mat, amount) = Tiers.cost(next) ?: return

        val cost = ItemStack(mat, amount)
        if (!player.inventory.containsAtLeast(cost, amount)) {
            player.sendMessage(
                Component.text(
                    "You need $amount x ${mat.name.lowercase().replace('_', ' ')} to upgrade.",
                    NamedTextColor.RED,
                )
            )
            return
        }
        player.inventory.removeItem(cost)
        ws.tier = next
        player.sendMessage(
            Component.text("Workstation upgraded to ${Tiers.name(next)} tier!", NamedTextColor.GREEN)
        )
        Guis.openUpgrade(plugin, player, ws)
    }

    private fun selectArea(player: Player, holder: VPHolder, size: Int) {
        val ws = holder.ws
        if (size > ws.maxArea()) {
            player.sendMessage(
                Component.text("This area size requires a higher tier.", NamedTextColor.RED)
            )
            return
        }
        ws.areaSize = size
        player.sendMessage(
            Component.text("Working area set to ${size}x${size}.", NamedTextColor.GREEN)
        )
        Guis.openArea(plugin, player, ws)
    }

    // ---------------------------------------------------------------- harvest

    private fun handleHarvestClick(event: InventoryClickEvent, player: Player, holder: VPHolder) {
        event.isCancelled = true
        if (event.rawSlot >= event.inventory.size) return
        val ws = holder.ws
        val pg = Guis.harvestPage(ws, holder.page)

        if (pg.paginated && event.rawSlot >= Guis.ITEMS_PER_PAGE) {
            when (event.rawSlot) {
                Guis.HARVEST_BACK_SLOT -> Guis.openMain(plugin, player, ws)
                Guis.HARVEST_PREV_SLOT -> Guis.openHarvest(plugin, player, ws, pg.page - 1)
                Guis.HARVEST_NEXT_SLOT -> Guis.openHarvest(plugin, player, ws, pg.page + 1)
            }
            return
        }
        if (!pg.paginated && event.rawSlot == Guis.HARVEST_BACK_SINGLE_SLOT) {
            Guis.openMain(plugin, player, ws)
            return
        }

        val clicked = event.currentItem ?: return
        if (clicked.type.isAir || clicked.type == Material.GRAY_STAINED_GLASS_PANE) return

        val taken = ws.withdraw(clicked.type, clicked.type.maxStackSize.toLong())
        if (taken <= 0) return
        player.giveOrDrop(ItemStack(clicked.type, taken.toInt()))

        val remaining = ws.storage[clicked.type] ?: 0L
        if (remaining > 0) {
            clicked.editMeta { meta ->
                meta.lore(
                    listOf(
                        Items.lore("Stored: $remaining"),
                        Items.lore("Click to take a stack."),
                    )
                )
            }
            event.inventory.setItem(event.rawSlot, clicked)
        } else if (pg.page == pg.totalPages - 1 && event.rawSlot == pg.items.lastIndex) {
            Guis.openHarvest(plugin, player, ws, holder.page)
        } else {
            holder.inventory.setItem(event.rawSlot, null)
        }
    }
}
