package de.cancelcloud.villagerplants.util

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun Player.giveOrDrop(item: ItemStack) {
    inventory.addItem(item).values.forEach {
        world.dropItemNaturally(location, it)
    }
}
