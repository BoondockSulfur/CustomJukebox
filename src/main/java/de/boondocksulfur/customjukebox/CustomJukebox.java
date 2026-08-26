package de.boondocksulfur.customjukebox;

import de.boondocksulfur.customjukebox.commands.CJBCommand;
import de.boondocksulfur.customjukebox.gui.AdminGUI;
import de.boondocksulfur.customjukebox.gui.CategoryCreationWizard;
import de.boondocksulfur.customjukebox.gui.CategoryEditorGUI;
import de.boondocksulfur.customjukebox.gui.DiscCreationWizard;
import de.boondocksulfur.customjukebox.gui.DiscEditorGUIv2;
import de.boondocksulfur.customjukebox.gui.PlaylistEditorGUI;
import de.boondocksulfur.customjukebox.gui.ZoneEditorGUI;
import de.boondocksulfur.customjukebox.integrations.PlaceholderAPIExpansion;
import de.boondocksulfur.customjukebox.listeners.*;
import de.boondocksulfur.customjukebox.manager.DiscManager;
import de.boondocksulfur.customjukebox.manager.ConfigManager;
import de.boondocksulfur.customjukebox.manager.PlaybackManager;
import de.boondocksulfur.customjukebox.manager.LanguageManager;
import de.boondocksulfur.customjukebox.manager.IntegrationManager;
import de.boondocksulfur.customjukebox.manager.AmbientZoneManager;
import de.boondocksulfur.customjukebox.manager.NowPlayingManager;
import de.boondocksulfur.customjukebox.manager.PlayerPreferencesManager;
import de.boondocksulfur.customjukebox.utils.ConfigWriter;
import de.boondocksulfur.customjukebox.utils.SchedulerUtil;
import de.boondocksulfur.customjukebox.utils.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomJukebox extends JavaPlugin {

    /**
     * Oldest server this plugin can run on. 1.21.4 is where the
     * CustomModelDataComponent float API arrived, which every disc item depends
     * on. The plugin's api-version is '1.21', so a 1.21.0-1.21.3 server happily
     * loads the jar and then dies the first time a disc is built.
     */
    private static final String MIN_SERVER_VERSION = "1.21.4";

    private static CustomJukebox instance;
    private ConfigWriter configWriter;
    private DiscManager discManager;
    private ConfigManager configManager;
    private PlaybackManager playbackManager;
    private LanguageManager languageManager;
    private IntegrationManager integrationManager;
    private AmbientZoneManager ambientZoneManager;
    private PlayerPreferencesManager playerPreferencesManager;
    private NowPlayingManager nowPlayingManager;
    private PlaylistEditorGUI playlistEditorGUI;
    private ZoneEditorGUI zoneEditorGUI;
    private AdminGUI adminGUI;
    private DiscEditorGUIv2 discEditorGUIv2;
    private DiscCreationWizard discCreationWizard;
    private CategoryCreationWizard categoryCreationWizard;
    private CategoryEditorGUI categoryEditorGUI;
    private JukeboxListener jukeboxListener;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting CustomJukebox initialization...");

        if (!isSupportedServerVersion()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers (order matters!)
        // The config writer persists all JSON files off the server thread and
        // must exist before the first manager can save anything
        configWriter = new ConfigWriter(this);
        // ConfigManager creates config.json if it doesn't exist
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);  // After ConfigManager
        integrationManager = new IntegrationManager(this);  // After ConfigManager
        
        // DiscManager creates disc.json and auto-discovers sounds from resourcepack
        discManager = new DiscManager(this);
        // Per-player settings (music on/off, personal volume, favourites) are
        // consulted by every playback path, so they come before it
        playerPreferencesManager = new PlayerPreferencesManager(this);
        playbackManager = new PlaybackManager(this);
        // AmbientZoneManager needs discs, integrations and config (all ready above)
        ambientZoneManager = new AmbientZoneManager(this);
        // Reads from both playback managers, so it comes last
        nowPlayingManager = new NowPlayingManager(this);

        // Initialize GUIs
        playlistEditorGUI = new PlaylistEditorGUI(this);
        zoneEditorGUI = new ZoneEditorGUI(this);
        adminGUI = new AdminGUI(this);
        discEditorGUIv2 = new DiscEditorGUIv2(this);
        discCreationWizard = new DiscCreationWizard(this);
        categoryCreationWizard = new CategoryCreationWizard(this);
        categoryEditorGUI = new CategoryEditorGUI(this);

        // Register commands and all aliases for proper tab-completion
        CJBCommand cjbCommand = new CJBCommand(this);

        // Register main command
        getCommand("cjb").setExecutor(cjbCommand);
        getCommand("cjb").setTabCompleter(cjbCommand);

        // Register all aliases explicitly for tab-completion support
        String[] aliases = {"customjukebox", "jukebox", "jb"};
        for (String alias : aliases) {
            org.bukkit.command.PluginCommand cmd = getCommand(alias);
            if (cmd != null) {
                cmd.setExecutor(cjbCommand);
                cmd.setTabCompleter(cjbCommand);
            } else {
                getLogger().warning("Could not register alias '" + alias + "' - command not found");
            }
        }

        // Register listeners
        registerListeners();

        // Initialize bStats metrics
        initializeMetrics();

        // Register PlaceholderAPI expansion
        registerPlaceholderAPI();

        // Check for updates
        checkForUpdates();

        // Start ambient zones (auto-playing looping playlists in regions/radii).
        // Done last so worlds and all managers are fully initialized.
        ambientZoneManager.start();
        nowPlayingManager.start();

        getLogger().info("CustomJukebox has been enabled!");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Loaded " + discManager.getAllDiscs().size() + " custom discs");
        getLogger().info("Using JSON configuration (JEXT-compatible)");
    }

    /**
     * Verifies the server is new enough for the item API this plugin builds on.
     * Says so plainly instead of letting it surface later as a NoSuchMethodError
     * from deep inside disc creation.
     *
     * @return true if the plugin may continue starting up
     */
    private boolean isSupportedServerVersion() {
        // "1.21.4-R0.1-SNAPSHOT" / "26.1.2-R0.1-SNAPSHOT" - the part before the
        // first dash is the Minecraft version. Available on every server flavour,
        // unlike the Paper-only getMinecraftVersion().
        String raw = getServer().getBukkitVersion();
        String version = raw.split("-")[0];

        if (UpdateChecker.compareVersions(version, MIN_SERVER_VERSION) < 0) {
            getLogger().severe("═══════════════════════════════════════════════════════════");
            getLogger().severe("Minecraft " + version + " is not supported.");
            getLogger().severe("CustomJukebox requires " + MIN_SERVER_VERSION + " or newer: custom discs use");
            getLogger().severe("the item model API that arrived in " + MIN_SERVER_VERSION + ".");
            getLogger().severe("On this server every disc would fail to be created.");
            getLogger().severe("═══════════════════════════════════════════════════════════");
            return false;
        }

        return true;
    }

    @Override
    public void onDisable() {
        // Hide progress bars before the music they describe goes away
        if (nowPlayingManager != null) {
            nowPlayingManager.stop();
        }

        // Stop ambient zones (cancels scanner + track timers, stops zone sounds)
        if (ambientZoneManager != null) {
            ambientZoneManager.stop();
        }

        // Stop all active playbacks before shutdown
        if (playbackManager != null) {
            playbackManager.stopAllPlaybacks();
        }

        // Cancel any pending scheduler tasks to prevent async operations after disable.
        // The Bukkit scheduler API throws UnsupportedOperationException on Folia -
        // there, Folia retires the plugin's scheduled tasks itself on disable.
        if (!SchedulerUtil.isFolia()) {
            getServer().getScheduler().cancelTasks(this);
        }

        // Last: make sure every queued config write reaches disk before we go
        if (configWriter != null) {
            configWriter.shutdown();
        }

        getLogger().info("CustomJukebox has been disabled!");
    }

    private void registerListeners() {
        jukeboxListener = new JukeboxListener(this);
        getServer().getPluginManager().registerEvents(jukeboxListener, this);
        getServer().getPluginManager().registerEvents(new JukeboxBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscDropListener(this), this);
        getServer().getPluginManager().registerEvents(new ParrotDanceListener(this), this);
        getServer().getPluginManager().registerEvents(new LootGenerateListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new UpdateNotifyListener(this), this);
        getServer().getPluginManager().registerEvents(playlistEditorGUI, this);
        getServer().getPluginManager().registerEvents(zoneEditorGUI, this);
        getServer().getPluginManager().registerEvents(adminGUI, this);
        getServer().getPluginManager().registerEvents(discEditorGUIv2, this);
        getServer().getPluginManager().registerEvents(discCreationWizard, this);
        getServer().getPluginManager().registerEvents(categoryCreationWizard, this);
        getServer().getPluginManager().registerEvents(categoryEditorGUI, this);
    }

    private void initializeMetrics() {
        try {
            // You can find the plugin id of your plugin on the page https://bstats.org/what-is-my-plugin-id
            int pluginId = 28570; // CustomJukebox plugin ID from bStats.org
            Metrics metrics = new Metrics(this, pluginId);

            // Custom chart: Number of custom discs
            metrics.addCustomChart(new SingleLineChart("custom_discs", () -> discManager.getAllDiscs().size()));

            // Custom chart: Language
            metrics.addCustomChart(new SimplePie("language", () -> configManager.getLanguage()));

            // Custom chart: GUI enabled
            metrics.addCustomChart(new SimplePie("gui_enabled", () -> configManager.isGuiEnabled() ? "Enabled" : "Disabled"));

            // Custom chart: WorldGuard integration
            metrics.addCustomChart(new SimplePie("worldguard_integration", () ->
                integrationManager.isWorldGuardEnabled() ? "Enabled" : "Disabled"));

            // Custom chart: GriefPrevention integration
            metrics.addCustomChart(new SimplePie("griefprevention_integration", () ->
                integrationManager.isGriefPreventionEnabled() ? "Enabled" : "Disabled"));

            // Custom chart: Fragment system enabled
            metrics.addCustomChart(new SimplePie("fragment_crafting", () ->
                configManager.isCraftingEnabled() ? "Enabled" : "Disabled"));

            // Custom chart: Parrot dancing enabled
            metrics.addCustomChart(new SimplePie("parrot_dancing", () ->
                configManager.isParrotDancingEnabled() ? "Enabled" : "Disabled"));

            getLogger().info("bStats metrics initialized successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats metrics: " + e.getMessage());
        }
    }

    private void registerPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PlaceholderAPIExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion registered successfully");
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not found (soft-dependency)");
        }
    }

    private void checkForUpdates() {
        // Modrinth project ID for CustomJukebox
        // https://modrinth.com/plugin/bs-customjukebox
        String modrinthProjectId = "bs-customjukebox";

        updateChecker = new UpdateChecker(this, modrinthProjectId);
        updateChecker.checkForUpdates();
    }

    public void reload() {
        configManager.reload();
        languageManager.reload();
        integrationManager.reload();
        discManager.reload();
        playerPreferencesManager.reload();
        // Reload zones after discs so playlists resolve; this restarts the
        // scanner and every zone timeline with the new config.
        ambientZoneManager.reload();
        nowPlayingManager.reload();
    }

    public static CustomJukebox getInstance() {
        return instance;
    }

    /**
     * Asynchronous persistence for the plugin's JSON config files.
     * @return the shared config writer
     */
    public ConfigWriter getConfigWriter() {
        return configWriter;
    }

    public DiscManager getDiscManager() {
        return discManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlaybackManager getPlaybackManager() {
        return playbackManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    public AmbientZoneManager getAmbientZoneManager() {
        return ambientZoneManager;
    }

    /**
     * Per-player music settings: on/off, personal volume, favourites.
     * @return the preferences manager
     */
    public PlayerPreferencesManager getPlayerPreferencesManager() {
        return playerPreferencesManager;
    }

    /**
     * Drives the "now playing" progress bar.
     * @return the now-playing manager
     */
    public NowPlayingManager getNowPlayingManager() {
        return nowPlayingManager;
    }

    public PlaylistEditorGUI getPlaylistEditorGUI() {
        return playlistEditorGUI;
    }

    public ZoneEditorGUI getZoneEditorGUI() {
        return zoneEditorGUI;
    }

    public AdminGUI getAdminGUI() {
        return adminGUI;
    }

    public DiscEditorGUIv2 getDiscEditorGUIv2() {
        return discEditorGUIv2;
    }

    public DiscCreationWizard getDiscCreationWizard() {
        return discCreationWizard;
    }

    public CategoryCreationWizard getCategoryCreationWizard() {
        return categoryCreationWizard;
    }

    public CategoryEditorGUI getCategoryEditorGUI() {
        return categoryEditorGUI;
    }

    public JukeboxListener getJukeboxListener() {
        return jukeboxListener;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
