package me.cnaude.plugin.AutoWhitelist;

import java.io.File;
import java.util.TimerTask;

public class FileWatcher extends TimerTask {

    private File m_File;
    private long m_LastModified;
    private volatile boolean m_WasChanged;
    private final Whitelist plugin;

    FileWatcher(File file, Whitelist instance) {
        this.m_File = file;
        plugin = instance;
        this.m_LastModified = this.m_File.lastModified();
    }

    @Override
    public void run() {
        if (this.m_LastModified != this.m_File.lastModified()) {
            this.m_LastModified = this.m_File.lastModified();
            if (!this.m_WasChanged) {
                this.m_WasChanged = true;
                plugin.logInfo("Whitelist.txt was updated. Whitelist was scheduled for reloading.");
            }
        }
    }

    public boolean wasFileModified() {
        return this.m_WasChanged;
    }

    public void resetFileModifiedState() {
        this.m_WasChanged = false;
    }
}