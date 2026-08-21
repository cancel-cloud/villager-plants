package de.cancelcloud.villagerplants

import de.cancelcloud.villagerplants.ai.FarmingService
import de.cancelcloud.villagerplants.gui.GuiListener
import de.cancelcloud.villagerplants.item.Items
import de.cancelcloud.villagerplants.listener.EggPlaceListener
import de.cancelcloud.villagerplants.listener.VillagerListener
import de.cancelcloud.villagerplants.listener.WorkstationBlockListener
import de.cancelcloud.villagerplants.manager.WorkstationManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.CoroutineContext

class VillagerPlantsPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: VillagerPlantsPlugin
            private set
    }

    lateinit var keys: Keys
        private set
    lateinit var workstations: WorkstationManager
        private set
    lateinit var farming: FarmingService
        private set
    lateinit var scope: CoroutineScope
        private set
    lateinit var mainDispatcher: CoroutineDispatcher
        private set

    var eggDropChance: Double = 0.05
        private set
    var workIntervalTicks: Long = 60
        private set
    var netheriteSpeedMultiplier: Double = 2.0
        private set
    var searchYRange: Int = 3
        private set
    var particleIntervalTicks: Long = 10
        private set
    var particleViewDistanceBlocks: Double = 48.0
        private set

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        loadSettings()

        keys = Keys(this)
        mainDispatcher = BukkitMainDispatcher(this)
        scope = CoroutineScope(SupervisorJob() + mainDispatcher)

        workstations = WorkstationManager(this)
        workstations.load()

        farming = FarmingService(this)

        Items.registerRecipe(this)

        val pm = server.pluginManager
        pm.registerEvents(VillagerListener(this), this)
        pm.registerEvents(EggPlaceListener(this), this)
        pm.registerEvents(WorkstationBlockListener(this), this)
        pm.registerEvents(GuiListener(this), this)

        farming.start()
        workstations.startAutosave()

        logger.info("VillagerPlants enabled - ${workstations.all().size} workstation(s) loaded.")
    }

    override fun onDisable() {
        if (this::scope.isInitialized) scope.cancel()
        if (this::workstations.isInitialized) workstations.save()
        logger.info("VillagerPlants disabled.")
    }

    private fun loadSettings() {
        eggDropChance = config.getDouble("egg-drop-chance", 0.05)
        workIntervalTicks = config.getLong("work-interval-ticks", 60)
        netheriteSpeedMultiplier = config.getDouble("netherite-speed-multiplier", 2.0)
        searchYRange = config.getInt("search-y-range", 3)
        particleIntervalTicks = config.getLong("particle-interval-ticks", 10)
        particleViewDistanceBlocks = config.getDouble("particle-view-distance-blocks", 48.0)
    }
}

/** All persistent data keys used by the plugin. */
class Keys(plugin: VillagerPlantsPlugin) {
    /** Marker on the craftable personal villager egg item. */
    val personalEgg = NamespacedKey(plugin, "personal_egg")
    /** Marker (byte) on villagers that did NOT spawn naturally. */
    val unnatural = NamespacedKey(plugin, "unnatural")
    /** Owner UUID (string) on personal villagers. */
    val owner = NamespacedKey(plugin, "owner")
    /** Workstation UUID (string) on personal villagers and workstation items. */
    val workstationId = NamespacedKey(plugin, "workstation_id")
    /** Recipe key. */
    val recipe = NamespacedKey(plugin, "personal_villager_egg")
}

/** Coroutine dispatcher that runs continuations on the Bukkit main thread. */
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class BukkitMainDispatcher(private val plugin: JavaPlugin) : CoroutineDispatcher(), Delay {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Bukkit.isPrimaryThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.isEnabled) {
            Bukkit.getScheduler().runTask(plugin, block)
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val ticks = ((timeMillis + 49) / 50).coerceAtLeast(1)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!plugin.isEnabled || !continuation.isActive) return@Runnable
            continuation.tryResume(Unit)?.let { continuation.completeResume(it) }
        }, ticks)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val ticks = ((timeMillis + 49) / 50).coerceAtLeast(1)
        val task = Bukkit.getScheduler().runTaskLater(plugin, block, ticks)
        return DisposableHandle { task.cancel() }
    }
}

/** Suspends for the given amount of game ticks (1 tick = 50 ms). */
suspend fun delayTicks(ticks: Long) = delay(ticks * 50)
