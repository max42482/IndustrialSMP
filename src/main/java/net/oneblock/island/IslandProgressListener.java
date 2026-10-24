package net.oneblock.island;

public interface IslandProgressListener {
    void blocksBroken(Island island, long before, long after);

    void phaseUnlocked(Island island, Phase phase);
}
