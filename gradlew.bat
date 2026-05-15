package gg.summit.customarmor.db;

import java.util.UUID;

/**
 * Mutable in-memory snapshot of one armor piece's state.
 * level, xp, and owner are all persisted to storage.
 */
public class ArmorData {

    private volatile int  level;
    private volatile int  xp;
    private volatile UUID owner; // null = unbound

    public ArmorData(int level, int xp, UUID owner) {
        this.level = level;
        this.xp    = xp;
        this.owner = owner;
    }

    public int  getLevel()           { return level; }
    public int  getXp()              { return xp; }
    public UUID getOwner()           { return owner; }
    public void setLevel(int level)  { this.level = level; }
    public void setXp(int xp)        { this.xp = xp; }
    public void setOwner(UUID owner) { this.owner = owner; }
}
