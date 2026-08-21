package de.cancelcloud.villagerplants.model

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Crops a personal villager can farm. */
enum class Crop(val seed: Material, val block: Material, val soil: Material, val display: String) {
    WHEAT(Material.WHEAT_SEEDS, Material.WHEAT, Material.FARMLAND, "Wheat"),
    CARROT(Material.CARROT, Material.CARROTS, Material.FARMLAND, "Carrots"),
    POTATO(Material.POTATO, Material.POTATOES, Material.FARMLAND, "Potatoes"),
    BEETROOT(Material.BEETROOT_SEEDS, Material.BEETROOTS, Material.FARMLAND, "Beetroot"),
    NETHER_WART(Material.NETHER_WART, Material.NETHER_WART, Material.SOUL_SAND, "Nether Wart");

    companion object {
        fun bySeed(material: Material): Crop? = entries.firstOrNull { it.seed == material }
        fun byBlock(material: Material): Crop? = entries.firstOrNull { it.block == material }
    }
}

/** Upgrade tiers of a workstation. */
object Tiers {
    const val MIN = 1
    const val MAX = 4

    /** Maximum working-area edge length for a tier. */
    fun maxArea(tier: Int): Int = when (tier) {
        1 -> 4
        2 -> 8
        else -> 16
    }

    /** Upgrade cost to reach [tier] (from tier - 1). */
    fun cost(tier: Int): Pair<Material, Int>? = when (tier) {
        2 -> Material.IRON_BLOCK to 2
        3 -> Material.GOLD_BLOCK to 2
        4 -> Material.NETHERITE_BLOCK to 1
        else -> null
    }

    fun name(tier: Int): String = when (tier) {
        1 -> "Basic"
        2 -> "Iron"
        3 -> "Gold"
        else -> "Netherite"
    }
}

/**
 * A personal villager workstation. While placed, [location] points at the
 * workstation block; while picked up it is null and the data lives inside
 * the workstation item until placed again.
 */
class Workstation(
    val id: UUID,
    val owner: UUID,
    var location: Location?,
    var tier: Int = Tiers.MIN,
    var areaSize: Int = 4,
    var crop: Crop? = null,
    /** Seed items deposited by the owner, consumed by the villager when planting. */
    var input: Long = 0,
    /** Harvested drops, effectively infinite capacity. */
    val storage: MutableMap<Material, Long> = linkedMapOf(),
    var villagerId: UUID? = null,
    /** Optional player-provided farming tool (hoe); used for drop calculation. */
    var tool: ItemStack? = null,
) {
    val placed: Boolean get() = location != null

    fun maxArea(): Int = Tiers.maxArea(tier)

    fun addDrop(material: Material, amount: Long) {
        if (amount <= 0) return
        storage.merge(material, amount) { a, b -> a + b }
    }

    fun takeDrop(material: Material, amount: Long): Long {
        val have = storage[material] ?: return 0
        val taken = minOf(have, amount)
        if (have - taken <= 0) storage.remove(material) else storage[material] = have - taken
        return taken
    }
}
