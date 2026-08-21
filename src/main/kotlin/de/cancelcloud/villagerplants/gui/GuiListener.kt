package de.cancelcloud.villagerplants.gui

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Tiers
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
                event.isCancelled = true
                if (event.rawSlot >= event.inventory.size) return
                when (event.rawSlot) {
                    10 -> if (event.isShiftClick) refundSeeds(player, holder)
                    11 -> handleToolClick(event, player, holder)
                    12 -> Guis.openUpgrade(plugin, player, ws)
                    13 -> Guis.openArea(plugin, player, ws)
                    14 -> Guis.openHarvest(plugin, player, ws)
                    16 -> pickup(player, holder)
                }
            }
            GuiType.UPGRADE -> {
                event.isCancelled = true
                if (event.rawSlot >= event.inventory.size) return
                when (event.rawSlot) {
                    13 -> upgrade(player, holder)
                    22 -> Guis.openMain(plugin, player, ws)
                }
            }
            GuiType.AREA -> {
                event.isCancelled = true
                if (event.rawSlot >= event.inventory.size) return
                when (event.rawSlot) {
                    11 -> selectArea(player, holder, 4)
                    13 -> selectArea(player, holder, 8)
                    15 -> selectArea(player, holder, 16)
                    22 -> Guis.openMain(plugin, player, ws)
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
                if (event.rawSlot == 4 && event.isShiftClick) {
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
        val inv = holder.inv
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
            if (ws.crop == null || ws.input <= 0) {
                ws.crop = crop
            }
            if (crop == ws.crop) {
                ws.input += item.amount
            } else {
                rejected += item
            }
        }

        for (item in rejected) {
            player.inventory.addItem(item).values.forEach {
                player.world.dropItemNaturally(player.location, it)
            }
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
        if (crop == null || ws.input <= 0) {
            ws.crop = null
            refreshCropInfo(holder)
            return
        }
        val total = ws.input
        var remaining = ws.input
        ws.input = 0
        ws.crop = null
        while (remaining > 0) {
            val amount = minOf(remaining, crop.seed.maxStackSize.toLong())
            remaining -= amount
            val stack = ItemStack(crop.seed, amount.toInt())
            player.inventory.addItem(stack).values.forEach {
                player.world.dropItemNaturally(player.location, it)
            }
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
        val player = holder.inv.viewers.firstOrNull() as? Player ?: return
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
            holder.inv.setItem(11, Guis.toolButton(ws))
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
        holder.inv.setItem(11, Guis.toolButton(ws))
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
        ws.villagerId = null
        loc.block.type = Material.AIR
        ws.location = null
        plugin.workstations.index(ws)
        plugin.farming.untrack(ws)

        player.closeInventory()
        val item = Items.workstationItem(plugin, ws)
        player.inventory.addItem(item).values.forEach {
            player.world.dropItemNaturally(player.location, it)
        }
        player.sendMessage(Component.text("Workstation picked up.", NamedTextColor.GREEN))
        plugin.workstations.save()
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
        val entries = Guis.harvestEntries(ws)
        val paginated = entries.size > Guis.HARVEST_SIZE - 1

        if (paginated && event.rawSlot >= Guis.ITEMS_PER_PAGE) {
            when (event.rawSlot) {
                45 -> Guis.openMain(plugin, player, ws)
                48 -> Guis.openHarvest(plugin, player, ws, holder.page - 1)
                50 -> Guis.openHarvest(plugin, player, ws, holder.page + 1)
            }
            return
        }
        if (!paginated && event.rawSlot == Guis.HARVEST_SIZE - 1) {
            Guis.openMain(plugin, player, ws)
            return
        }

        val clicked = event.currentItem ?: return
        if (clicked.type.isAir || clicked.type == Material.GRAY_STAINED_GLASS_PANE) return

        val taken = ws.takeDrop(clicked.type, clicked.type.maxStackSize.toLong())
        if (taken <= 0) return
        val give = ItemStack(clicked.type, taken.toInt())
        player.inventory.addItem(give).values.forEach {
            player.world.dropItemNaturally(player.location, it)
        }
        Guis.openHarvest(plugin, player, ws, holder.page)
    }
}
