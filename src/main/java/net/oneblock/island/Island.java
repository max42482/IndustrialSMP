package net.oneblock.island;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Island {
    final long id;
    UUID owner;
    final Set<UUID> members = new LinkedHashSet<>();
    final int gridX;
    final int gridZ;
    double homeX;
    double homeY;
    double homeZ;
    float homeYaw;
    float homePitch;
    long blocksBroken;
    long best;
    String selectedPhase;
    final Set<String> bonusPhases = new LinkedHashSet<>();
    boolean open = true;
    final long created;

    Island(long id, UUID owner, int gridX, int gridZ, long created) {
        this.id = id;
        this.owner = owner;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.created = created;
    }

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public long blocksBroken() {
        return blocksBroken;
    }

    public Set<UUID> everyone() {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(owner);
        all.addAll(members);
        return Collections.unmodifiableSet(all);
    }

    public boolean isMember(UUID player) {
        return owner.equals(player) || members.contains(player);
    }
}
