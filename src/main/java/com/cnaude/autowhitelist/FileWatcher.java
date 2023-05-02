package com.cnaude.autowhitelist;

import java.io.File;
import org.bukkit.scheduler.BukkitTask;

public class FileWatcher {

    private final File watchFile;
    private long lastModified;
    private volatile boolean wasFileChanged;
    private final AutoWhitelist plugin;
    private final BukkitTask bt;
    protected final String fileName;
    private final Config c;

    public FileWatcher(File file, AutoWhitelist instance, Config config) {
        this.plugin = instance;
        this.watchFile = file;
        this.fileName = file.getName();
        this.c = config;

        plugin.logInfo("Watching " + watchFile.getName() + " for changes...");

        bt = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, new Runnable() {
            @Override
            public void run() {
                plugin.logDebug("Checking " + watchFile.getName() + " for changes.");
                if (lastModified != watchFile.lastModified()) {
                    lastModified = watchFile.lastModified();
                    plugin.logInfo("Reloading " + watchFile.getName());
                    if (plugin.whitelistFile.exists() && !c.uuidMode()) {
                        plugin.loadWhitelist();
                    } else if (plugin.uuidFile.exists() && c.uuidMode()) {
                        plugin.loadUUIDWhitelist();
                    }
                }
            }
        }, 0L, config.fileCheckInterval());
    }

    /**
     *
     */
    public void cancel() {
        bt.cancel();
    }

}
