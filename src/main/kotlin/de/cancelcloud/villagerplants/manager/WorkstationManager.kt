package de.cancelcloud.villagerplants.manager

import de.cancelcloud.villagerplants.VillagerPlantsPlugin
import de.cancelcloud.villagerplants.model.Crop
import de.cancelcloud.villagerplants.model.Workstation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import de.cancelcloud.villagerplants.delayTicks
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class WorkstationManager(private val plugin: VillagerPlantsPlugin) {

    private val byId = linkedMapOf<UUID, Workstation>()
    private val byBlock = hashMapOf<String, Workstation>()
    private val file = File(plugin.dataFolder, "data.yml")

    fun all(): Collection<Workstation> = byId.values

    fun byId(id: UUID): Workstation? = byId[id]

    fun byVillager(id: UUID): Workstation? = byId.values.firstOrNull { it.villagerId == id }

    fun at(block: Block): Workstation? = byBlock[key(block.location)]

    fun register(ws: Workstation) {
        byId[ws.id] = ws
        index(ws)
    }

    fun remove(ws: Workstation) {
        byId.remove(ws.id)
        ws.location?.let { byBlock.remove(key(it)) }
    }

    /** Re-index after a location change (place / pickup). */
    fun index(ws: Workstation) {
        byBlock.entries.removeIf { it.value === ws }
        ws.location?.let { byBlock[key(it)] = ws }
    }

    private fun key(loc: Location): String =
        "${loc.world.uid}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"

    // ---------------------------------------------------------------- persistence

    fun save() {
        val yaml = YamlConfiguration()
        for (ws in byId.values) {
            val path = "workstations.${ws.id}"
            yaml.set("$path.owner", ws.owner.toString())
            yaml.set("$path.tier", ws.tier)
            yaml.set("$path.area", ws.areaSize)
            yaml.set("$path.crop", ws.crop?.name)
            yaml.set("$path.input", ws.input)
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
        plugin.dataFolder.mkdirs()
        yaml.save(file)
    }

    fun load() {
        byId.clear()
        byBlock.clear()
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
                input = sec.getLong("input", 0),
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
                runCatching { save() }.onFailure {
                    plugin.logger.warning("Autosave failed: ${it.message}")
                }
            }
        }
    }
}
