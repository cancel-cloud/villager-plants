package de.cancelcloud.villagerplants.gui

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Tiers
import de.cancelcloud.villagerplants.model.Workstation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

enum class GuiType { MAIN, WORKER, UPGRADE, AREA, HARVEST }

class VPHolder(
    val type: GuiType,
    val ws: Workstation,
    size: Int,
    title: Component,
    var page: Int = 0,
) : InventoryHolder {
    private val inv: Inventory = Bukkit.createInventory(this, size, title)
    override fun getInventory(): Inventory = inv
}

object Guis {

    const val MAIN_CROP_SLOT = 10
    const val MAIN_TOOL_SLOT = 11
    const val MAIN_UPGRADE_SLOT = 12
    const val MAIN_AREA_SLOT = 13
    const val MAIN_HARVEST_SLOT = 14
    const val MAIN_PICKUP_SLOT = 16

    const val WORKER_INFO_SLOT = 4
    val WORKER_INPUT_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16)

    const val UPGRADE_CONFIRM_SLOT = 13
    const val BACK_SLOT = 22

    const val AREA_SMALL_SLOT = 11
    const val AREA_MEDIUM_SLOT = 13
    const val AREA_LARGE_SLOT = 15

    const val HARVEST_SIZE = 54
    const val ITEMS_PER_PAGE = 45 // bottom row reserved for navigation when paginating
    const val HARVEST_BACK_SLOT = 45
    const val HARVEST_PREV_SLOT = 48
    const val HARVEST_PAGE_SLOT = 49
    const val HARVEST_NEXT_SLOT = 50
    const val HARVEST_BACK_SINGLE_SLOT = 53

    private fun filler(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { it.displayName(Component.text(" ")) }
        return item
    }

    private fun button(material: Material, title: String, vararg loreLines: String): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(Items.name(title))
            if (loreLines.isNotEmpty()) meta.lore(loreLines.map { Items.lore(it) })
        }
        return item
    }

    private fun create(type: GuiType, ws: Workstation, size: Int, title: Component, page: Int = 0): VPHolder =
        VPHolder(type, ws, size, title, page)

    // ---------------------------------------------------------------- main

    fun openMain(plugin: VillagerPlantsPlugin, player: Player, ws: Workstation) {
        val holder = create(GuiType.MAIN, ws, 27, Component.text("Workstation - ${Tiers.name(ws.tier)} Tier"))
        val inv = holder.inventory
        for (i in 0 until 27) inv.setItem(i, filler())

        val cropIcon = ws.crop?.seed ?: Material.BARRIER
        inv.setItem(
            MAIN_CROP_SLOT,
            button(
                cropIcon,
                "Crop: ${ws.crop?.display ?: "none"}",
                "Seeds the villager holds: ${ws.seedCount}",
                "Right-click the villager to deposit seeds.",
                "Shift-click to take the seeds back and",
                "free the slot for a different crop.",
            )
        )
        inv.setItem(MAIN_TOOL_SLOT, toolButton(ws))
        inv.setItem(
            MAIN_UPGRADE_SLOT,
            button(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "Upgrade",
                "Current tier: ${Tiers.name(ws.tier)} (${ws.tier}/${Tiers.MAX})",
                "Max working area: ${ws.maxArea()}x${ws.maxArea()}",
            )
        )
        inv.setItem(
            MAIN_AREA_SLOT,
            button(
                Material.MAP,
                "Working Area",
                "Current: ${ws.areaSize}x${ws.areaSize}",
                "Set the area the villager works in.",
            )
        )
        inv.setItem(
            MAIN_HARVEST_SLOT,
            button(
                Material.CHEST,
                "Harvest Storage",
                "Stored items: ${ws.storage.values.sum()}",
                "Open the harvest inventory.",
            )
        )
        inv.setItem(
            MAIN_PICKUP_SLOT,
            button(
                Material.SMITHING_TABLE,
                "Pick Up Workstation",
                "Removes the workstation and villager.",
                "All data is kept in the item.",
            )
        )
        player.openInventory(inv)
    }

    fun toolButton(ws: Workstation): ItemStack {
        val tool = ws.tool
        return if (tool == null) {
            button(
                Material.WOODEN_HOE,
                "Farming Tool: none",
                "Click with a hoe on your cursor to give it",
                "to the villager. Enchantments like Fortune",
                "increase the harvested drops.",
            )
        } else {
            val item = tool.clone()
            item.editMeta { meta ->
                meta.displayName(Items.name("Farming Tool (click to take back)"))
                meta.lore(
                    listOf(
                        Items.lore("The villager harvests with this tool."),
                        Items.lore("Fortune increases the drop amount."),
                    )
                )
            }
            item
        }
    }

    // ---------------------------------------------------------------- worker (crop input)

    fun openWorker(plugin: VillagerPlantsPlugin, player: Player, ws: Workstation) {
        val holder = create(GuiType.WORKER, ws, 27, Component.text("Worker - Place Seeds"))
        val inv = holder.inventory
        for (i in 0 until 27) {
            if (i !in WORKER_INPUT_SLOTS) inv.setItem(i, filler())
        }
        inv.setItem(
            WORKER_INFO_SLOT,
            button(
                ws.crop?.seed ?: Material.BARRIER,
                "Crop: ${ws.crop?.display ?: "none"}",
                "Seeds the villager holds: ${ws.seedCount}",
                "Place seeds (wheat seeds, carrots, potatoes,",
                "beetroot seeds or nether wart) below.",
                "They are absorbed when you close this menu.",
                "Shift-click here to take the seeds back and",
                "switch to a different crop.",
            )
        )
        player.openInventory(inv)
    }

    // ---------------------------------------------------------------- upgrade

    fun openUpgrade(plugin: VillagerPlantsPlugin, player: Player, ws: Workstation) {
        val holder = create(GuiType.UPGRADE, ws, 27, Component.text("Workstation - Upgrade"))
        val inv = holder.inventory
        for (i in 0 until 27) inv.setItem(i, filler())

        if (ws.tier >= Tiers.MAX) {
            inv.setItem(UPGRADE_CONFIRM_SLOT, button(Material.BARRIER, "Maximum tier reached", "This workstation is fully upgraded."))
        } else {
            val next = ws.tier + 1
            val (mat, amount) = Tiers.cost(next)!!
            val benefit = if (next == 4)
                "Villager works ${plugin.netheriteSpeedMultiplier}x faster"
            else
                "Max working area: ${Tiers.maxArea(next)}x${Tiers.maxArea(next)}"
            inv.setItem(
                UPGRADE_CONFIRM_SLOT,
                button(
                    Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                    "Upgrade to ${Tiers.name(next)} Tier",
                    "Cost: $amount x ${mat.name.lowercase().replace('_', ' ')}",
                    benefit,
                    "Click to upgrade (materials taken from your inventory).",
                )
            )
        }
        inv.setItem(BACK_SLOT, button(Material.ARROW, "Back"))
        player.openInventory(inv)
    }

    // ---------------------------------------------------------------- area

    fun openArea(plugin: VillagerPlantsPlugin, player: Player, ws: Workstation) {
        val holder = create(GuiType.AREA, ws, 27, Component.text("Workstation - Working Area"))
        val inv = holder.inventory
        for (i in 0 until 27) inv.setItem(i, filler())

        val sizes = listOf(4 to AREA_SMALL_SLOT, 8 to AREA_MEDIUM_SLOT, 16 to AREA_LARGE_SLOT)
        for ((size, slot) in sizes) {
            val allowed = size <= ws.maxArea()
            val selected = ws.areaSize == size
            val item = when {
                selected -> button(Material.LIME_STAINED_GLASS, "${size}x${size} (selected)")
                allowed -> button(Material.GRASS_BLOCK, "${size}x${size}", "Click to select.")
                else -> button(
                    Material.BARRIER, "${size}x${size} (locked)",
                    "Requires a higher workstation tier.",
                )
            }
            inv.setItem(slot, item)
        }
        inv.setItem(BACK_SLOT, button(Material.ARROW, "Back"))
        player.openInventory(inv)
    }

    // ---------------------------------------------------------------- harvest (paginated double chest)

    data class HarvestPage(
        val items: List<Pair<Material, Long>>,
        val paginated: Boolean,
        val page: Int,
        val totalPages: Int,
    )

    fun harvestEntries(ws: Workstation): List<Pair<Material, Long>> =
        ws.storage.entries.sortedBy { it.key.name }.map { it.key to it.value }

    fun harvestPage(ws: Workstation, requested: Int): HarvestPage {
        val entries = harvestEntries(ws)
        val paginated = entries.size > HARVEST_SIZE - 1 // single page keeps back button at slot 53
        val totalPages = if (paginated) ((entries.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE).coerceAtLeast(1) else 1
        val page = requested.coerceIn(0, totalPages - 1)
        val items = if (paginated)
            entries.drop(page * ITEMS_PER_PAGE).take(ITEMS_PER_PAGE)
        else
            entries.take(HARVEST_SIZE - 1)
        return HarvestPage(items, paginated, page, totalPages)
    }

    fun openHarvest(plugin: VillagerPlantsPlugin, player: Player, ws: Workstation, page: Int = 0) {
        val pg = harvestPage(ws, page)

        val title = Component.text("Harvest - Page ${pg.page + 1}/${pg.totalPages}")
        val holder = create(GuiType.HARVEST, ws, HARVEST_SIZE, title, pg.page)
        val inv = holder.inventory

        pg.items.forEachIndexed { index, (mat, count) ->
            val stack = ItemStack(mat)
            stack.editMeta { meta ->
                meta.lore(
                    listOf(
                        Items.lore("Stored: $count"),
                        Items.lore("Click to take a stack."),
                    )
                )
            }
            inv.setItem(index, stack)
        }

        if (pg.paginated) {
            for (i in HARVEST_BACK_SLOT until HARVEST_SIZE) inv.setItem(i, filler())
            inv.setItem(HARVEST_BACK_SLOT, button(Material.ARROW, "Back", "Back to the main menu."))
            if (pg.page > 0) inv.setItem(HARVEST_PREV_SLOT, button(Material.SPECTRAL_ARROW, "Previous Page"))
            inv.setItem(HARVEST_PAGE_SLOT, button(Material.PAPER, "Page ${pg.page + 1}/${pg.totalPages}"))
            if (pg.page < pg.totalPages - 1) inv.setItem(HARVEST_NEXT_SLOT, button(Material.SPECTRAL_ARROW, "Next Page"))
        } else {
            inv.setItem(HARVEST_BACK_SINGLE_SLOT, button(Material.ARROW, "Back", "Back to the main menu."))
        }
        player.openInventory(inv)
    }
}
