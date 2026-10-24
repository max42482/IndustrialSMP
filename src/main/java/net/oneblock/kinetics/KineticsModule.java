package net.oneblock.kinetics;

import net.oneblock.economy.util.Text;
import net.oneblock.kinetics.pack.ResourcePackHost;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KineticsModule {
    private static final int GOGGLES_INTERVAL = 4;
    private static final int WATER_INTERVAL = 20;
    private static final int LIGHT_INTERVAL = 100;
    private static final double RENDER_DISTANCE_SQ = 80 * 80;
    private static final int CRANK_HOLD_TICKS = 7;
    private static final int HIT_RESET_TICKS = 30;
    private static final int SAVE_INTERVAL = 600;

    final Plugin plugin;
    final PartRegistry registry;
    final KineticsConfig config = new KineticsConfig();
    final NetworkSolver solver;
    final KineticItems items;
    final Machines machines;
    final Processing processing;
    final ItemPorts ports = new ItemPorts(this);
    final Depots depots = new Depots(this);
    final Millstones millstones = new Millstones(this);
    final Presses presses = new Presses(this);
    final Fans fans = new Fans(this);
    final Crushers crushers = new Crushers(this);
    final Belts belts = new Belts(this);
    final Funnels funnels = new Funnels(this);
    final Gauges gauges = new Gauges(this);
    final Windmills windmills = new Windmills(this);
    private final ResourcePackHost pack;
    private final Set<KineticPart> dirty = new LinkedHashSet<>();
    private final Set<KineticPart> activeCranks = new HashSet<>();
    private final Goggles goggles;
    private BukkitTask task;
    long tick;

    public KineticsModule(Plugin plugin) {
        this.plugin = plugin;
        this.registry = new PartRegistry(plugin);
        this.solver = new NetworkSolver(registry, config);
        this.items = new KineticItems(plugin);
        this.machines = new Machines(this);
        this.processing = new Processing(plugin, items);
        this.pack = new ResourcePackHost(plugin);
        this.goggles = new Goggles(this);
    }

    public KineticItems items() {
        return items;
    }

    public void configure(ConfigurationSection kinetics, ConfigurationSection resourcePack) {
        config.load(kinetics);
        pack.start(resourcePack);
        items.useModels(pack.enabled());
        processing.load();
        dirty.addAll(registry.all());
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(new KineticsListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(pack, plugin);
        Bukkit.getPluginManager().registerEvents(new MiningSpeed(this), plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                loadChunk(chunk);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
        pack.stop();
        processing.unload();
        Set<Chunk> chunks = new HashSet<>();
        for (KineticPart p : registry.all()) {
            unloadExtras(p);
            if (p.visual != null) {
                p.visual.remove();
            }
            if (p.dirty && p.world.isChunkLoaded(p.x >> 4, p.z >> 4)) {
                chunks.add(p.world.getChunkAt(p.x >> 4, p.z >> 4));
            }
        }
        for (Chunk c : chunks) {
            registry.save(c.getWorld(), c.getX(), c.getZ());
        }
    }

    private void loadExtras(KineticPart p) {
        switch (p.type) {
            case DEPOT -> depots.refresh(p);
            case BELT -> belts.segment(p);
            case WINDMILL_BEARING -> windmills.load(p);
            default -> {
            }
        }
    }

    private void unloadExtras(KineticPart p) {
        switch (p.type) {
            case DEPOT -> depots.unload(p);
            case BELT -> belts.unload(p);
            case WINDMILL_BEARING -> windmills.unload(p);
            default -> {
            }
        }
    }

    void markDirty(KineticPart p) {
        dirty.add(p);
    }

    void loadChunk(Chunk chunk) {
        for (KineticPart p : registry.read(chunk)) {
            if (registry.get(p.world, p.x, p.y, p.z) != null) {
                continue;
            }
            registry.add(p);
            if (p.block().getType() != p.type.host) {
                setHost(p.block(), p);
            }
            p.powered = p.block().isBlockIndirectlyPowered();
            if (p.type == PartType.WATER_WHEEL) {
                machines.updateWaterWheel(p);
            }
            p.visual = new PartVisual(p);
            p.visual.spawn();
            loadExtras(p);
            dirty.add(p);
        }
    }

    void unloadChunk(Chunk chunk) {
        Set<KineticPart> parts = registry.inChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (parts.isEmpty()) {
            return;
        }
        boolean save = false;
        for (KineticPart p : parts) {
            unloadExtras(p);
            save |= p.dirty;
        }
        if (save) {
            registry.save(chunk.getWorld(), chunk.getX(), chunk.getZ());
        }
        List<KineticPart> neighbors = new ArrayList<>();
        for (KineticPart p : parts) {
            for (NetworkSolver.Edge e : solver.edges(p)) {
                neighbors.add(e.other());
            }
        }
        for (KineticPart p : parts) {
            if (p.visual != null) {
                p.visual.remove();
            }
            registry.remove(p);
            dirty.remove(p);
            activeCranks.remove(p);
        }
        neighbors.removeAll(parts);
        dirty.addAll(neighbors);
    }

    KineticPart partAt(Block b) {
        return registry.get(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    boolean place(Player player, PartType type, Block clicked, BlockFace face, EquipmentSlot hand, ItemStack item) {
        if (!type.placeable()) {
            return false;
        }
        Block target = clicked.isReplaceable() && partAt(clicked) == null ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable() || partAt(target) != null) {
            return false;
        }
        if (!target.getWorld().getNearbyEntities(BoundingBox.of(target), e -> e instanceof LivingEntity).isEmpty()) {
            return false;
        }
        Axis axis = player.isSneaking() ? Faces.axisOf(face) : preferredAxis(type, target, Faces.axisOf(face));
        if (axis == Axis.Y && (type == PartType.MECHANICAL_PRESS || type == PartType.CRUSHING_WHEEL)) {
            axis = Faces.axisOf(player.getFacing());
        }
        BlockFace facing = type == PartType.FUNNEL ? face : face.getOppositeFace();
        if (type == PartType.MECHANICAL_DRILL && !player.isSneaking()) {
            BlockFace input = drillInput(target, clicked, face);
            if (input != null) {
                facing = input.getOppositeFace();
            }
        }
        KineticPart part = new KineticPart(target.getWorld(), target.getX(), target.getY(), target.getZ(), type,
                axis, facing, config.creativeMotorStep);

        BlockState replaced = target.getState();
        setHost(target, part);
        BlockPlaceEvent event = new BlockPlaceEvent(target, replaced, clicked, item, player, true, hand);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            replaced.update(true, false);
            return true;
        }

        Set<Network.Problem> before = new HashSet<>();
        registry.add(part);
        part.powered = target.isBlockIndirectlyPowered();
        for (NetworkSolver.Edge e : solver.edges(part)) {
            if (e.other().network != null) {
                before.add(e.other().network.problem);
            }
        }
        if (type == PartType.WATER_WHEEL) {
            machines.updateWaterWheel(part);
        }
        Network network = solver.solve(part, tick);
        Network.Problem problem = network.problem;
        if (problem != Network.Problem.NONE && problem != Network.Problem.OVERSTRESSED && !before.contains(problem)) {
            List<NetworkSolver.Edge> edges = solver.edges(part);
            registry.remove(part);
            replaced.update(true, false);
            for (NetworkSolver.Edge e : edges) {
                dirty.add(e.other());
            }
            Text.send(player, "<red>Can't place that here: " + problem.message.toLowerCase() + ".");
            player.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
            return true;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        part.visual = new PartVisual(part);
        part.visual.spawn();
        registry.save(part);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1f);
        return true;
    }

    private Axis preferredAxis(PartType type, Block target, Axis fallback) {
        if (type.orientation != PartType.Orientation.AXIS || type == PartType.GEARBOX) {
            return fallback;
        }
        Set<Axis> wanted = EnumSet.noneOf(Axis.class);
        for (BlockFace f : Faces.ALL) {
            KineticPart n = partAt(target.getRelative(f));
            if (n == null) {
                continue;
            }
            if (n.connectFaces().contains(f.getOppositeFace())) {
                wanted.add(Faces.axisOf(f));
            } else if (type.isCog() && n.type.isCog() && n.axis != Faces.axisOf(f)) {
                wanted.add(n.axis);
            }
        }
        return wanted.size() == 1 ? wanted.iterator().next() : fallback;
    }

    private BlockFace drillInput(Block target, Block clicked, BlockFace face) {
        KineticPart c = partAt(clicked);
        if (c != null && c.connectFaces().contains(face)) {
            return face.getOppositeFace();
        }
        for (BlockFace f : Faces.ALL) {
            KineticPart n = partAt(target.getRelative(f));
            if (n != null && n.connectFaces().contains(f.getOppositeFace())) {
                return f;
            }
        }
        return null;
    }

    static void setHost(Block target, KineticPart part) {
        BlockData data = part.type.host.createBlockData();
        if (data instanceof Orientable o && o.getAxes().contains(part.axis)) {
            o.setAxis(part.axis);
        }
        if (data instanceof Slab slab) {
            slab.setType(part.type.halfHost() ? Slab.Type.BOTTOM : Slab.Type.DOUBLE);
        }
        target.setBlockData(data, false);
    }

    KineticPart placeDirect(World world, int x, int y, int z, PartType type, Axis axis, BlockFace facing, int motorSpeed) {
        Block target = world.getBlockAt(x, y, z);
        if (partAt(target) != null || !target.isReplaceable()) {
            return null;
        }
        KineticPart part = new KineticPart(world, x, y, z, type, axis, facing, motorSpeed);
        setHost(target, part);
        registry.add(part);
        part.powered = target.isBlockIndirectlyPowered();
        if (type == PartType.WATER_WHEEL) {
            machines.updateWaterWheel(part);
        }
        part.visual = new PartVisual(part);
        part.visual.spawn();
        loadExtras(part);
        registry.save(part);
        dirty.add(part);
        return part;
    }

    void breakPart(KineticPart part, boolean dropItem) {
        switch (part.type) {
            case BELT -> {
                belts.breakRun(part, dropItem);
                return;
            }
            case DEPOT -> depots.dropContents(part);
            case MILLSTONE -> millstones.dropContents(part);
            case CRUSHING_WHEEL -> crushers.dropContents(part);
            case WINDMILL_BEARING -> {
                if (part.state instanceof Windmills.Sails s && !s.blocks.isEmpty()) {
                    windmills.disassemble(part, s);
                }
            }
            default -> {
            }
        }
        removePart(part);
        if (dropItem) {
            part.world.dropItemNaturally(part.center(), items.part(part.type, 1));
        }
    }

    void removePart(KineticPart part) {
        List<KineticPart> neighbors = new ArrayList<>();
        for (NetworkSolver.Edge e : solver.edges(part)) {
            neighbors.add(e.other());
        }
        unloadExtras(part);
        if (part.visual != null) {
            part.visual.remove();
        }
        registry.remove(part);
        dirty.remove(part);
        activeCranks.remove(part);
        registry.save(part);
        dirty.addAll(neighbors);
    }

    boolean use(Player player, KineticPart part, BlockFace face, EquipmentSlot hand, ItemStack item) {
        PartType held = items.partOf(item);
        if (held == PartType.BELT) {
            belts.connectorClick(player, part, item, hand);
            return true;
        }
        if (part.type == PartType.HAND_CRANK) {
            if (hand == EquipmentSlot.HAND) {
                crank(part, player);
            }
            return true;
        }
        if (held != null) {
            place(player, held, part.block(), face, hand, item);
            return true;
        }
        boolean emptyHand = item == null || item.getType().isAir();
        switch (part.type) {
            case DEPOT -> depots.interact(player, part, item);
            case BELT -> belts.interact(player, part, item);
            case MILLSTONE -> {
                if (!emptyHand && !processing.canProcess(Processing.Kind.MILLING, item)) {
                    return false;
                }
                millstones.interact(player, part, item);
            }
            case WINDMILL_BEARING -> {
                if (!emptyHand) {
                    return false;
                }
                windmills.interact(player, part);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    void pick(Player player, KineticPart part) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (items.partOf(inv.getItem(slot)) == part.type) {
                if (slot < 9) {
                    inv.setHeldItemSlot(slot);
                } else {
                    ItemStack held = inv.getItemInMainHand();
                    inv.setItemInMainHand(inv.getItem(slot));
                    inv.setItem(slot, held);
                }
                return;
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        int empty = inv.getItemInMainHand().isEmpty() ? inv.getHeldItemSlot() : -1;
        for (int slot = 0; slot < 9 && empty < 0; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).isEmpty()) {
                empty = slot;
            }
        }
        if (empty < 0) {
            empty = inv.getHeldItemSlot();
        }
        inv.setItem(empty, items.part(part.type, 1));
        inv.setHeldItemSlot(empty);
    }

    void updatePower(KineticPart part) {
        boolean powered = part.block().isBlockIndirectlyPowered();
        if (powered == part.powered) {
            return;
        }
        for (NetworkSolver.Edge e : solver.edges(part)) {
            dirty.add(e.other());
        }
        part.powered = powered;
        dirty.add(part);
    }

    void crank(KineticPart part, Player player) {
        int direction = player.isSneaking() ? -1 : 1;
        boolean wasRunning = tick <= part.crankUntil;
        part.crankUntil = tick + CRANK_HOLD_TICKS;
        if (!wasRunning || direction != part.crankDirection) {
            part.crankDirection = direction;
            dirty.add(part);
            part.world.playSound(part.center(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.6f, 0.8f);
        }
        activeCranks.add(part);
    }

    void adjustMotor(KineticPart part, Player player) {
        int step = config.creativeMotorStep;
        int max = (int) config.maxSpeed;
        int speed = part.motorSpeed + (player.isSneaking() ? -step : step);
        part.motorSpeed = Math.clamp(speed, -max, max);
        registry.save(part);
        dirty.add(part);
        player.sendActionBar(Text.mm("<light_purple>Creative Motor: <white>" + part.motorSpeed + " RPM"
                + (part.motorSpeed == 0 ? " <gray>(stopped)" : "")));
    }

    void solvePending() {
        while (!dirty.isEmpty()) {
            KineticPart start = dirty.iterator().next();
            dirty.remove(start);
            if (registry.get(start.world, start.x, start.y, start.z) == start) {
                solver.solve(start, tick).parts.forEach(dirty::remove);
            }
        }
    }

    private void tick() {
        tick++;
        if (!activeCranks.isEmpty()) {
            activeCranks.removeIf(p -> {
                if (tick > p.crankUntil) {
                    dirty.add(p);
                    return true;
                }
                return false;
            });
        }
        List<KineticPart> all = new ArrayList<>(registry.all());
        if (tick % WATER_INTERVAL == 0) {
            for (KineticPart p : all) {
                if (p.type == PartType.WATER_WHEEL && machines.updateWaterWheel(p)) {
                    dirty.add(p);
                }
            }
        }
        solvePending();
        for (KineticPart p : all) {
            if (registry.get(p.world, p.x, p.y, p.z) != p) {
                continue;
            }
            switch (p.type) {
                case MECHANICAL_DRILL -> machines.tickDrill(p);
                case MILLSTONE -> millstones.tick(p, tick);
                case MECHANICAL_PRESS -> presses.tick(p, tick);
                case ENCASED_FAN -> fans.tick(p, tick);
                case CRUSHING_WHEEL -> crushers.tick(p, tick);
                case BELT -> belts.tick(p, tick);
                case FUNNEL -> funnels.tick(p, tick);
                case SPEEDOMETER, STRESSOMETER -> gauges.tick(p, tick);
                default -> {
                }
            }
        }
        render(all);
        if (tick % GOGGLES_INTERVAL == 0) {
            goggles.update();
        }
        if (tick % SAVE_INTERVAL == 0) {
            saveDirty(all);
            fans.cleanup();
        }
    }

    private void saveDirty(List<KineticPart> all) {
        Set<Long> chunks = new HashSet<>();
        for (KineticPart p : all) {
            if (!p.dirty) {
                continue;
            }
            if (p.type == PartType.BELT) {
                belts.save(p);
            }
            long key = ((long) (p.x >> 4) << 32) ^ (p.z >> 4) ^ ((long) p.world.getUID().hashCode() << 16);
            if (chunks.add(key)) {
                registry.save(p.world, p.x >> 4, p.z >> 4);
            }
        }
        for (KineticPart p : all) {
            p.dirty = false;
        }
    }

    private void render(List<KineticPart> all) {
        boolean lightTick = tick % LIGHT_INTERVAL == 0;
        for (KineticPart p : all) {
            if (p.visual == null) {
                continue;
            }
            if (lightTick) {
                p.visual.refreshLight();
            }
            if (p.speed == 0 || tick < p.nextRender || !watched(p)) {
                continue;
            }
            float degreesPerTick = (float) (p.speed * 360 / 60 / 20);
            float abs = Math.abs(degreesPerTick);
            int ticks = abs <= 24 ? 5 : abs <= 60 ? 2 : 1;
            p.angle = (p.angle + degreesPerTick * ticks) % 360;
            p.visual.apply(p.angle, ticks + 1);
            if (p.type == PartType.WINDMILL_BEARING) {
                windmills.render(p, p.angle, ticks + 1);
            }
            p.nextRender = tick + ticks;
        }
    }

    private boolean watched(KineticPart p) {
        for (Player player : p.world.getPlayers()) {
            Location l = player.getLocation();
            double dx = l.getX() - p.x;
            double dy = l.getY() - p.y;
            double dz = l.getZ() - p.z;
            if (dx * dx + dy * dy + dz * dz < RENDER_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
    }
}
