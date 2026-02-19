package de.boondocksulfur.customjukebox.listeners;

import de.boondocksulfur.customjukebox.CustomJukebox;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Handles jukebox destruction and ensures playback is stopped properly.
 */
public class JukeboxBreakListener implements Listener {

    private final CustomJukebox plugin;

    public JukeboxBreakListener(CustomJukebox plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles jukebox breaking by player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.JUKEBOX) {
            stopPlaybackAtBlock(block);
        }
    }

    /**
     * Handles jukebox destruction by block explosion.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.JUKEBOX) {
                stopPlaybackAtBlock(block);
            }
        }
    }

    /**
     * Handles jukebox destruction by entity explosion (TNT, Creeper, etc.).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.JUKEBOX) {
                stopPlaybackAtBlock(block);
            }
        }
    }

    /**
     * Stops playback at a specific jukebox block.
     * @param block Jukebox block
     */
    private void stopPlaybackAtBlock(Block block) {
        if (plugin.getPlaybackManager().isPlaying(block.getLocation())) {
            plugin.getPlaybackManager().stopPlayback(block.getLocation());

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Stopped playback due to jukebox destruction at " +
                    block.getLocation());
            }
        }
    }
}
