package de.boondocksulfur.customjukebox;

import de.boondocksulfur.customjukebox.commands.CJBCommand;
import de.boondocksulfur.customjukebox.gui.AdminGUI;
import de.boondocksulfur.customjukebox.gui.CategoryCreationWizard;
import de.boondocksulfur.customjukebox.gui.CategoryEditorGUI;
import de.boondocksulfur.customjukebox.gui.DiscCreationWizard;
import de.boondocksulfur.customjukebox.gui.DiscEditorGUIv2;
import de.boondocksulfur.customjukebox.gui.PlaylistEditorGUI;
import de.boondocksulfur.customjukebox.integrations.PlaceholderAPIExpansion;
import de.boondocksulfur.customjukebox.listeners.*;
import de.boondocksulfur.customjukebox.manager.DiscManager;
import de.boondocksulfur.customjukebox.manager.ConfigManager;
import de.boondocksulfur.customjukebox.manager.PlaybackManager;
import de.boondocksulfur.customjukebox.manager.LanguageManager;
import de.boondocksulfur.customjukebox.manager.IntegrationManager;
import de.boondocksulfur.customjukebox.utils.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomJukebox extends JavaPlugin {

    private static CustomJukebox instance;
    private DiscManager discManager;
    private ConfigManager configManager;
    private PlaybackManager playbackManager;
    private LanguageManager languageManager;
    private IntegrationManager integrationManager;
    private PlaylistEditorGUI playlistEditorGUI;
    private AdminGUI adminGUI;
    private DiscEditorGUIv2 discEditorGUIv2;
    private DiscCreationWizard discCreationWizard;
    private CategoryCreationWizard categoryCreationWizard;
    private CategoryEditorGUI categoryEditorGUI;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting CustomJukebox initialization...");

        // Initialize managers (order matters!)
        // ConfigManager creates config.json if it doesn't exist
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);  // After ConfigManager
        integrationManager = new IntegrationManager(this);  // After ConfigManager
        
        // DiscManager creates disc.json and auto-discovers sounds from resourcepack
        discManager = new DiscManager(this);
        playbackManager = new PlaybackManager(this);

        // Initialize GUIs
        playlistEditorGUI = new PlaylistEditorGUI(this);
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

        getLogger().info("CustomJukebox has been enabled!");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Loaded " + discManager.getAllDiscs().size() + " custom discs");
        getLogger().info("Using JSON configuration (JEXT-compatible)");
    }

    @Override
    public void onDisable() {
        // Stop all active playbacks before shutdown
        if (playbackManager != null) {
            playbackManager.stopAllPlaybacks();
        }

        // Cancel any pending scheduler tasks to prevent async operations after disable
        getServer().getScheduler().cancelTasks(this);

        getLogger().info("CustomJukebox has been disabled!");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JukeboxListener(this), this);
        getServer().getPluginManager().registerEvents(new JukeboxBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscDropListener(this), this);
        getServer().getPluginManager().registerEvents(new ParrotDanceListener(this), this);
        getServer().getPluginManager().registerEvents(new LootGenerateListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new UpdateNotifyListener(this), this);
        getServer().getPluginManager().registerEvents(playlistEditorGUI, this);
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
    }

    public static CustomJukebox getInstance() {
        return instance;
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

    public PlaylistEditorGUI getPlaylistEditorGUI() {
        return playlistEditorGUI;
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

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
