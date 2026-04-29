package gg.summit.customarmor.db;

/**
 * Mutable in-memory snapshot of one armor piece's level/xp for a player.
 * All reads/writes during gameplay go through this — never the database.
 */
public class ArmorData {

    private volatile int level;
    private volatile int xp;

    public ArmorData(int level, int xp) {
        this.level = level;
        this.xp    = xp;
    }

    public int  getLevel()          { return level; }
    public int  getXp()             { return xp; }
    public void setLevel(int level) { this.level = level; }
    public void setXp(int xp)       { this.xp = xp; }
}
