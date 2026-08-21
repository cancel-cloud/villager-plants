package de.cancelcloud.villagerplants.item

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.model.Tiers
import de.cancelcloud.villagerplants.model.Workstation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object Items {

    fun name(text: String, color: NamedTextColor = NamedTextColor.GOLD): Component =
        Component.text(text, color).decoration(TextDecoration.ITALIC, false)

    fun lore(text: String, color: NamedTextColor = NamedTextColor.GRAY): Component =
        Component.text(text, color).decoration(TextDecoration.ITALIC, false)

    /** The craftable personal villager spawn egg. */
    fun personalEgg(plugin: VillagerPlantsPlugin, amount: Int = 1): ItemStack {
        val item = ItemStack(Material.VILLAGER_SPAWN_EGG, amount)
        item.editMeta { meta ->
            meta.displayName(name("Personal Villager Egg", NamedTextColor.AQUA))
            meta.lore(
                listOf(
                    lore("Right-click a block to spawn your"),
                    lore("personal farmer villager and its workstation."),
                )
            )
            meta.setEnchantmentGlintOverride(true)
            meta.persistentDataContainer.set(plugin.keys.personalEgg, PersistentDataType.BYTE, 1)
        }
        return item
    }

    fun isPersonalEgg(plugin: VillagerPlantsPlugin, item: ItemStack?): Boolean {
        if (item == null || item.type != Material.VILLAGER_SPAWN_EGG || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(plugin.keys.personalEgg, PersistentDataType.BYTE)
    }

    /** The item form of a picked-up workstation (keeps all data via its id). */
    fun workstationItem(plugin: VillagerPlantsPlugin, ws: Workstation): ItemStack {
        val item = ItemStack(Material.SMITHING_TABLE)
        item.editMeta { meta ->
            meta.displayName(name("Villager Workstation", NamedTextColor.LIGHT_PURPLE))
            meta.lore(
                listOf(
                    lore("Tier: ${Tiers.name(ws.tier)} (${ws.tier}/${Tiers.MAX})"),
                    lore("Stored drops: ${ws.storage.values.sum()}"),
                    lore(""),
                    lore("Right-click a block to place it again."),
                )
            )
            meta.setEnchantmentGlintOverride(true)
            meta.persistentDataContainer.set(
                plugin.keys.workstationId, PersistentDataType.STRING, ws.id.toString()
            )
        }
        return item
    }

    fun workstationIdOf(plugin: VillagerPlantsPlugin, item: ItemStack?): UUID? {
        if (item == null || item.type != Material.SMITHING_TABLE || !item.hasItemMeta()) return null
        val raw = item.itemMeta.persistentDataContainer.get(plugin.keys.workstationId, PersistentDataType.STRING)
            ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    /** Gold Block + Villager Spawn Egg + Gold Block. */
    fun registerRecipe(plugin: VillagerPlantsPlugin) {
        val recipe = ShapedRecipe(plugin.keys.recipe, personalEgg(plugin))
        recipe.shape("GEG")
        recipe.setIngredient('G', Material.GOLD_BLOCK)
        recipe.setIngredient('E', Material.VILLAGER_SPAWN_EGG)
        plugin.server.addRecipe(recipe)
    }
}
