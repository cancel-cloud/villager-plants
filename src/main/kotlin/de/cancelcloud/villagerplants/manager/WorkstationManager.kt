package de.cancelcloud.villagerplants.manager

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Workstation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.cancelcloud.villagerplants.delayTicks
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class WorkstationManager(private val plugin: VillagerPlantsPlugin) {

    private val byId = linkedMapOf<UUID, Workstation>()
    private val byBlock = hashMapOf<UUID, HashMap<Long, Workstation>>()
    private val blockIndex = hashMapOf<UUID, BlockIndexEntry>()
    private val byVillager = hashMapOf<UUID, Workstation>()
    private val file = File(plugin.dataFolder, "data.yml")
    private val tmpFile = File(plugin.dataFolder, "data.yml.tmp")

    /** Packed block position a workstation is currently indexed under. */
    private class BlockIndexEntry(val worldId: UUID, val packed: Long)

    fun all(): Collection<Workstation> = byId.values

    fun byId(id: UUID): Workstation? = byId[id]

    fun byVillager(id: UUID): Workstation? = byVillager[id]

    fun at(block: Block): Workstation? =
        byBlock[block.world.uid]?.get(pack(block.x, block.y, block.z))

    fun register(ws: Workstation) {
        byId[ws.id] = ws
        index(ws)
        ws.villagerId?.let { byVillager[it] = ws }
    }

    fun remove(ws: Workstation) {
        byId.remove(ws.id)
        unindex(ws)
        ws.villagerId?.let { old -> if (byVillager[old] === ws) byVillager.remove(old) }
    }

    /** Single mutation point for [Workstation.villagerId]; keeps the villager index in sync. */
    fun setVillager(ws: Workstation, villagerId: UUID?) {
        ws.villagerId?.let { old -> if (byVillager[old] === ws) byVillager.remove(old) }
        ws.villagerId = villagerId
        villagerId?.let { byVillager[it] = ws }
    }

    /** Re-index after a location change (place / pickup). */
    fun index(ws: Workstation) {
        unindex(ws)
        val loc = ws.location ?: return
        val packed = pack(loc.blockX, loc.blockY, loc.blockZ)
        byBlock.getOrPut(loc.world.uid) { hashMapOf() }[packed] = ws
        blockIndex[ws.id] = BlockIndexEntry(loc.world.uid, packed)
    }

    private fun unindex(ws: Workstation) {
        val entry = blockIndex.remove(ws.id) ?: return
        byBlock[entry.worldId]?.remove(entry.packed)
    }

    /** Packs (x, y, z) into a Long: 26 bits x | 12 bits y | 26 bits z, sign-safe via masking. */
    private fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFFL) shl 38) or ((z.toLong() and 0x3FFFFFFL) shl 12) or (y.toLong() and 0xFFFL)

    // ---------------------------------------------------------------- persistence

    private fun snapshot(): YamlConfiguration {
        val yaml = YamlConfiguration()
        for (ws in byId.values) {
            val path = "workstations.${ws.id}"
            yaml.set("$path.owner", ws.owner.toString())
            yaml.set("$path.tier", ws.tier)
            yaml.set("$path.area", ws.areaSize)
            yaml.set("$path.crop", ws.crop?.name)
            yaml.set("$path.input", ws.seedCount)
            yaml.set("$path.villager", ws.villagerId?.toString())
            yaml.set("$path.tool", ws.tool)
            ws.location?.let { loc ->
                yaml.set("$path.loc.world", loc.world.uid.toString())
                yaml.set("$path.loc.x", loc.blockX)
                yaml.set("$path.loc.y", loc.blockY)
                yaml.set("$path.loc.z", loc.blockZ)
            }
            for ((mat, count) in ws.storage) {
                yaml.set("$path.storage.${mat.name}", count)
            }
        }
        return yaml
    }

    fun save() {
        plugin.dataFolder.mkdirs()
        snapshot().save(file)
    }

    fun saveAsync() {
        val data = snapshot().saveToString()
        plugin.scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    plugin.dataFolder.mkdirs()
                    Files.writeString(tmpFile.toPath(), data)
                    Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Autosave failed: ${e.message}")
            }
        }
    }

    fun load() {
        byId.clear()
        byBlock.clear()
        blockIndex.clear()
        byVillager.clear()
        if (!file.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        val root = yaml.getConfigurationSection("workstations") ?: return
        for (idStr in root.getKeys(false)) {
            val sec = root.getConfigurationSection(idStr) ?: continue
            val id = runCatching { UUID.fromString(idStr) }.getOrNull() ?: continue
            val owner = runCatching { UUID.fromString(sec.getString("owner") ?: "") }.getOrNull() ?: continue

            var location: Location? = null
            if (sec.contains("loc.world")) {
                val worldId = runCatching { UUID.fromString(sec.getString("loc.world") ?: "") }.getOrNull()
                val world = worldId?.let { Bukkit.getWorld(it) }
                if (world != null) {
                    location = Location(
                        world,
                        sec.getInt("loc.x").toDouble(),
                        sec.getInt("loc.y").toDouble(),
                        sec.getInt("loc.z").toDouble(),
                    )
                }
            }

            val ws = Workstation(
                id = id,
                owner = owner,
                location = location,
                tier = sec.getInt("tier", 1).coerceIn(1, 4),
                areaSize = sec.getInt("area", 4),
                crop = sec.getString("crop")?.let { runCatching { Crop.valueOf(it) }.getOrNull() },
                seedCount = sec.getLong("input", 0),
                villagerId = sec.getString("villager")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                tool = sec.getItemStack("tool"),
            )
            sec.getConfigurationSection("storage")?.let { st ->
                for (matName in st.getKeys(false)) {
                    val mat = Material.matchMaterial(matName) ?: continue
                    ws.storage[mat] = st.getLong(matName)
                }
            }
            register(ws)
        }
    }

    fun startAutosave() {
        plugin.scope.launch {
            while (isActive) {
                delayTicks(20L * 60 * 5) // every 5 minutes
                saveAsync()
            }
        }
    }
}
