package net.oneblock.kinetics;

import net.oneblock.economy.util.Items;
import net.oneblock.economy.util.Text;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Belts {
    private static final double GAP = 0.3;
    private static final double TOP = 0.5;

    static final class BeltItem {
        ItemStack stack;
        double pos;
        boolean pressed;
        float fan;
        Processing.Kind fanKind;
        final FloatingItem shown = new FloatingItem();

        BeltItem(ItemStack stack, double pos) {
            this.stack = stack;
            this.pos = pos;
        }
    }

    static final class Segment {
        final List<BeltItem> items = new ArrayList<>();
    }

    private final KineticsModule kinetics;
    private final Map<UUID, KineticPart> firstShaft = new HashMap<>();

    Belts(KineticsModule kinetics) {
        this.kinetics = kinetics;
    }

    Segment segment(KineticPart belt) {
        if (belt.state instanceof Segment s) {
            return s;
        }
        Segment s = new Segment();
        if (!belt.extra.isEmpty()) {
            for (String entry : belt.extra.split("\\|")) {
                String[] p = entry.split(";", 2);
                if (p.length == 2) {
                    try {
                        s.items.add(new BeltItem(Items.decode(p[1]), Double.parseDouble(p[0])));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        belt.state = s;
        return s;
    }

    void save(KineticPart belt) {
        StringBuilder sb = new StringBuilder();
        for (BeltItem i : segment(belt).items) {
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(String.format(java.util.Locale.US, "%.3f", i.pos)).append(';').append(Items.encode(i.stack));
        }
        belt.extra = sb.toString();
        belt.dirty = true;
    }

    private boolean room(Segment s, double pos) {
        if (s.items.size() >= PartType.BELT.capacity) {
            return false;
        }
        for (BeltItem i : s.items) {
            if (Math.abs(i.pos - pos) < GAP) {
                return false;
            }
        }
        return true;
    }

    ItemStack accept(KineticPart belt, ItemStack stack, BlockFace travel) {
        double pos = travel == belt.facing ? 0.05 : travel == belt.facing.getOppositeFace() ? 0.95 : 0.5;
        Segment s = segment(belt);
        if (!room(s, pos)) {
            return stack;
        }
        s.items.add(new BeltItem(stack.clone(), pos));
        save(belt);
        return null;
    }

    void tick(KineticPart belt, long tick) {
        Segment s = segment(belt);
        if (tick % 4 == 0) {
            absorb(belt, s);
        }
        if (s.items.isEmpty()) {
            return;
        }
        double v = belt.speed / kinetics.config.beltDivider;
        boolean changed = false;
        if (v != 0) {
            changed = move(belt, s, v);
        }
        if (changed) {
            save(belt);
        }
        if (tick % 2 == 0) {
            render(belt, s);
        }
    }

    private boolean move(KineticPart belt, Segment s, double v) {
        boolean forward = v > 0;
        List<BeltItem> order = new ArrayList<>(s.items);
        order.sort(Comparator.comparingDouble((BeltItem i) -> i.pos));
        if (forward) {
            java.util.Collections.reverse(order);
        }
        boolean changed = false;
        BeltItem ahead = null;
        for (BeltItem item : order) {
            double target = item.pos + v;
            if (ahead != null) {
                target = forward ? Math.min(target, ahead.pos - GAP) : Math.max(target, ahead.pos + GAP);
            }
            if (!item.pressed && kinetics.presses.waitsFor(belt, item.stack)) {
                if (forward && item.pos <= 0.5 && target > 0.5 || !forward && item.pos >= 0.5 && target < 0.5) {
                    target = 0.5;
                }
            }
            if (forward ? target > 1 : target < 0) {
                BlockFace dir = forward ? belt.facing : belt.facing.getOppositeFace();
                double edge = forward ? 1 : 0;
                Block next = belt.block().getRelative(dir);
                KineticPart np = kinetics.partAt(next);
                if (np != null && np.type == PartType.BELT && np.facing == belt.facing) {
                    double entry = forward ? target - 1 : target + 1;
                    Segment ns = segment(np);
                    if (room(ns, entry)) {
                        s.items.remove(item);
                        item.pos = entry;
                        ns.items.add(item);
                        save(np);
                        changed = true;
                        continue;
                    }
                    target = edge;
                } else if (kinetics.ports.isReceiver(next)) {
                    ItemStack left = kinetics.ports.insert(next, item.stack, dir);
                    if (left == null) {
                        s.items.remove(item);
                        item.shown.remove();
                        changed = true;
                        continue;
                    }
                    item.stack = left;
                    target = edge;
                } else if (!next.getType().isSolid()) {
                    Location at = belt.center().add(dir.getModX() * 0.6, TOP - 0.4, dir.getModZ() * 0.6);
                    ItemPorts.drop(at, item.stack, dir);
                    s.items.remove(item);
                    item.shown.remove();
                    changed = true;
                    continue;
                } else {
                    target = edge;
                }
            }
            item.pos = target;
            ahead = item;
        }
        return changed;
    }

    private void render(KineticPart belt, Segment s) {
        for (BeltItem i : s.items) {
            i.shown.show(itemLocation(belt, i.pos), i.stack);
        }
    }

    private static Location itemLocation(KineticPart belt, double pos) {
        double offset = pos - 0.5;
        return new Location(belt.world, belt.x + 0.5 + belt.facing.getModX() * offset, belt.y + TOP + 0.02,
                belt.z + 0.5 + belt.facing.getModZ() * offset);
    }

    private void absorb(KineticPart belt, Segment s) {
        BoundingBox box = new BoundingBox(belt.x, belt.y + 0.3, belt.z, belt.x + 1, belt.y + 1.2, belt.z + 1);
        boolean changed = false;
        for (var e : belt.world.getNearbyEntities(box, e -> e instanceof Item)) {
            Item item = (Item) e;
            double along = switch (belt.facing) {
                case EAST -> item.getLocation().getX() - belt.x;
                case WEST -> 1 - (item.getLocation().getX() - belt.x);
                case SOUTH -> item.getLocation().getZ() - belt.z;
                default -> 1 - (item.getLocation().getZ() - belt.z);
            };
            double pos = Math.clamp(along, 0.05, 0.95);
            if (room(s, pos)) {
                s.items.add(new BeltItem(item.getItemStack().clone(), pos));
                item.remove();
                changed = true;
            }
        }
        if (changed) {
            save(belt);
        }
    }

    BeltItem waitingForPress(KineticPart belt) {
        for (BeltItem i : segment(belt).items) {
            if (!i.pressed && Math.abs(i.pos - 0.5) < 0.02 && kinetics.processing.canProcess(Processing.Kind.PRESSING, i.stack)) {
                return i;
            }
        }
        return null;
    }

    void replace(KineticPart belt, BeltItem item, List<ItemStack> results) {
        Segment s = segment(belt);
        item.pressed = true;
        if (results.isEmpty()) {
            s.items.remove(item);
            item.shown.remove();
        } else {
            item.stack = results.getFirst();
            for (int n = 1; n < results.size(); n++) {
                double pos = Math.max(0.05, item.pos - GAP * n);
                if (room(s, pos)) {
                    BeltItem extra = new BeltItem(results.get(n), pos);
                    extra.pressed = true;
                    s.items.add(extra);
                } else {
                    belt.world.dropItem(itemLocation(belt, item.pos), results.get(n));
                }
            }
        }
        save(belt);
    }

    void fanProcess(KineticPart belt, Processing.Kind kind, float step) {
        Segment s = segment(belt);
        for (BeltItem item : new ArrayList<>(s.items)) {
            if (!kinetics.processing.canProcess(kind, item.stack)) {
                continue;
            }
            if (item.fanKind != kind) {
                item.fanKind = kind;
                item.fan = 0;
            }
            item.fan += step;
            if (item.fan >= 1) {
                item.fan = 0;
                boolean pressed = item.pressed;
                Location at = itemLocation(belt, item.pos);
                replace(belt, item, kinetics.processing.processStack(kind, item.stack));
                item.pressed = pressed;
                Fans.effect(at, kind);
            }
        }
    }

    void interact(Player p, KineticPart belt, ItemStack hand) {
        Segment s = segment(belt);
        if (hand != null && !hand.getType().isAir()) {
            if (room(s, 0.5)) {
                s.items.add(new BeltItem(hand.clone(), 0.5));
                hand.setAmount(0);
                save(belt);
            }
            return;
        }
        for (BeltItem i : new ArrayList<>(s.items)) {
            for (ItemStack leftover : p.getInventory().addItem(i.stack).values()) {
                p.getWorld().dropItem(p.getLocation(), leftover);
            }
            i.shown.remove();
            s.items.remove(i);
        }
        save(belt);
        p.playSound(belt.center(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
    }

    void connectorClick(Player p, KineticPart clicked, ItemStack connector, EquipmentSlot hand) {
        if (clicked.type != PartType.SHAFT || clicked.axis == Axis.Y) {
            Text.send(p, "<red>Belts go between two horizontal shafts.");
            return;
        }
        KineticPart first = firstShaft.get(p.getUniqueId());
        if (first == null || first == clicked || kinetics.partAt(first.block()) != first || first.world != clicked.world) {
            firstShaft.put(p.getUniqueId(), clicked);
            p.sendActionBar(Text.mm("<gray>First shaft selected - now right-click the second one."));
            return;
        }
        firstShaft.remove(p.getUniqueId());
        String problem = build(p, first, clicked, connector, hand);
        if (problem != null) {
            Text.send(p, "<red>" + problem);
        }
    }

    private String build(Player p, KineticPart a, KineticPart b, ItemStack connector, EquipmentSlot hand) {
        if (a.axis != b.axis || a.y != b.y) {
            return "Both shafts need the same axis and height.";
        }
        BlockFace facing;
        int length;
        if (a.axis == Axis.X && a.x == b.x) {
            facing = b.z > a.z ? BlockFace.SOUTH : BlockFace.NORTH;
            length = Math.abs(b.z - a.z) + 1;
        } else if (a.axis == Axis.Z && a.z == b.z) {
            facing = b.x > a.x ? BlockFace.EAST : BlockFace.WEST;
            length = Math.abs(b.x - a.x) + 1;
        } else {
            return "The shafts must be in a straight line, across their axis.";
        }
        if (length < 2 || length > kinetics.config.beltMaxLength) {
            return "Belts can be 2 to " + kinetics.config.beltMaxLength + " blocks long.";
        }
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            Block b2 = a.block().getRelative(facing, i);
            if (i > 0 && i < length - 1 && (!b2.isReplaceable() || kinetics.partAt(b2) != null)) {
                return "Something is in the way.";
            }
            blocks.add(b2);
        }
        for (Block b2 : blocks) {
            BlockState before = b2.getState();
            BlockPlaceEvent event = new BlockPlaceEvent(b2, before, b2, connector, p, true, hand);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return "You can't build there.";
            }
        }
        kinetics.removePart(a);
        kinetics.removePart(b);
        for (Block b2 : blocks) {
            b2.setType(Material.AIR, false);
            kinetics.placeDirect(b2.getWorld(), b2.getX(), b2.getY(), b2.getZ(), PartType.BELT, null, facing, 0);
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            connector.setAmount(connector.getAmount() - 1);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 0.8f);
        return null;
    }

    void breakRun(KineticPart broken, boolean dropConnector) {
        List<KineticPart> run = new ArrayList<>();
        run.add(broken);
        for (BlockFace dir : new BlockFace[]{broken.facing, broken.facing.getOppositeFace()}) {
            Block b = broken.block().getRelative(dir);
            KineticPart p;
            while ((p = kinetics.partAt(b)) != null && p.type == PartType.BELT && p.facing == broken.facing) {
                run.add(p);
                b = b.getRelative(dir);
            }
        }
        KineticPart start = broken;
        KineticPart end = broken;
        for (KineticPart p : run) {
            long along = (long) p.x * broken.facing.getModX() + (long) p.z * broken.facing.getModZ();
            if (along < (long) start.x * broken.facing.getModX() + (long) start.z * broken.facing.getModZ()) {
                start = p;
            }
            if (along > (long) end.x * broken.facing.getModX() + (long) end.z * broken.facing.getModZ()) {
                end = p;
            }
        }
        for (KineticPart p : run) {
            for (BeltItem i : segment(p).items) {
                i.shown.remove();
                p.world.dropItemNaturally(itemLocation(p, i.pos), i.stack);
            }
            segment(p).items.clear();
            kinetics.removePart(p);
            if (p != broken) {
                p.block().setType(Material.AIR, false);
            }
        }
        if (dropConnector) {
            broken.world.dropItemNaturally(broken.center(), kinetics.items.part(PartType.BELT, 1));
        }
        KineticPart s = start;
        KineticPart e = end;
        Bukkit.getScheduler().runTask(kinetics.plugin, () -> {
            for (KineticPart end2 : new KineticPart[]{s, e}) {
                if (end2.block().isReplaceable() && kinetics.partAt(end2.block()) == null) {
                    end2.block().setType(Material.AIR, false);
                    kinetics.placeDirect(end2.world, end2.x, end2.y, end2.z, PartType.SHAFT, end2.axis, null, 0);
                }
            }
        });
    }

    void unload(KineticPart belt) {
        save(belt);
        for (BeltItem i : segment(belt).items) {
            i.shown.remove();
        }
    }
}
