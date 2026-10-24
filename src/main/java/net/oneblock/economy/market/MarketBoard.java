package net.oneblock.economy.market;

import net.oneblock.economy.account.Currency;
import net.oneblock.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Supplier;

public final class MarketBoard {
    private static final int SHOWN = 6;
    private final MarketManager market;
    private final Supplier<Location> where;
    private TextDisplay display;

    public MarketBoard(MarketManager market, Supplier<Location> where) {
        this.market = market;
        this.where = where;
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 40L, 20L * 10);
    }

    public void stop() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }

    private void update() {
        Location at = where.get();
        if (at == null || !at.isChunkLoaded()) {
            return;
        }
        if (display == null || !display.isValid()) {
            display = at.getWorld().spawn(at, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setLineWidth(260);
            });
        }
        List<MarketManager.ItemMarket> ranked = market.ranked();
        StringBuilder sb = new StringBuilder("<gradient:#5dff8a:#1fbf5a><bold>MARKET</bold></gradient>\n<gray>Best to sell right now\n");
        for (int i = 0; i < Math.min(SHOWN, ranked.size()); i++) {
            sb.append('\n').append(line(ranked.get(i)));
        }
        if (ranked.size() > SHOWN) {
            sb.append("\n\n<reset><gray>Flooded right now");
            for (int i = ranked.size() - 1; i >= Math.max(SHOWN, ranked.size() - 3); i--) {
                sb.append('\n').append(line(ranked.get(i)));
            }
        }
        sb.append("\n\n<reset><dark_gray>/market for every price");
        display.text(Text.mm(sb.toString()));
    }

    private String line(MarketManager.ItemMarket m) {
        return "<reset><white>" + Text.pretty(m.material()) + " " + Currency.COINS.format(market.price(m.material())) + " "
                + market.demandTag(m.material()).replace(" above normal", "").replace(" below normal", "").replace("around normal", "normal");
    }
}
