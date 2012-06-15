package net.immortal_forces.silence.plugin.whitelist;

import java.io.File;
import java.util.TimerTask;

public class FileWatcher extends TimerTask {

    private File m_File;
    private long m_LastModified;
    private volatile boolean m_WasChanged;

    FileWatcher(File file) {
        this.m_File = file;
        this.m_LastModified = this.m_File.lastModified();
    }

    public void run() {
        if (this.m_LastModified != this.m_File.lastModified()) {
            this.m_LastModified = this.m_File.lastModified();
            if (!this.m_WasChanged) {
                this.m_WasChanged = true;
                System.out.println("Whitelist: Whitelist.txt was updated. Whitelist was scheduled for reloading.");
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