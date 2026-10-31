package me.amets.clanradar;

import me.amets.vidas.ClanManager;
import me.amets.vidas.VidasPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClanRadarPlugin extends JavaPlugin {

    /** Mitad del campo de visión (en grados) donde se muestra la etiqueta sobre la cabeza. */
    private static final double FRONT_HALF_ANGLE = 50.0;
    /** Cada cuántos ticks se refresca el radar. */
    private static final long UPDATE_PERIOD = 2L;

    private final Map<UUID, Map<UUID, Marker>> markers = new HashMap<>();
    private ClanManager clans;
    private BukkitTask task;

    private static final class Marker {
        final TextDisplay display;
        final BossBar bar;

        Marker(TextDisplay display, BossBar bar) {
            this.display = display;
            this.bar = bar;
        }
    }

    @Override
    public void onEnable() {
        VidasPlugin vidas = (VidasPlugin) Bukkit.getPluginManager().getPlugin("Vidas5");
        if (vidas == null) {
            getLogger().severe("No se encontró Vidas5, desactivando ClanRadar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        clans = vidas.getClanManager();
        task = Bukkit.getScheduler().runTaskTimer(this, this::update, 1L, UPDATE_PERIOD);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        for (UUID viewer : new HashSet<>(markers.keySet())) {
            clearViewer(viewer);
        }
    }

    private void update() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (clans.getClan(viewer) == null) {
                clearViewer(viewer.getUniqueId());
                continue;
            }

            Map<UUID, Marker> mine = markers.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
            Set<UUID> activos = new HashSet<>();

            for (Player mate : Bukkit.getOnlinePlayers()) {
                if (mate.equals(viewer)) continue;
                if (!clans.mismoClan(viewer, mate)) continue;
                if (mate.getWorld() != viewer.getWorld()) continue;

                activos.add(mate.getUniqueId());
                Marker m = mine.computeIfAbsent(mate.getUniqueId(), k -> createMarker(viewer, mate));
                refresh(viewer, mate, m);
            }

            Iterator<Map.Entry<UUID, Marker>> it = mine.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Marker> e = it.next();
                if (!activos.contains(e.getKey())) {
                    destroy(e.getValue());
                    it.remove();
                }
            }
        }
    }

    private Marker createMarker(Player viewer, Player mate) {
        TextDisplay display = mate.getWorld().spawn(mate.getLocation(), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setTeleportDuration(2);
            td.setPersistent(false);
            td.setVisibleByDefault(false); // solo lo ve el dueño del marcador
        });
        viewer.showEntity(this, display);

        BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.setVisible(false);

        return new Marker(display, bar);
    }

    private void refresh(Player viewer, Player mate, Marker m) {
        Location eye = viewer.getEyeLocation();
        Location target = mate.getLocation();

        double dx = target.getX() - eye.getX();
        double dz = target.getZ() - eye.getZ();
        double dist = eye.distance(target);

        // Minecraft: yaw 0 = sur (+Z), y el giro positivo es hacia la derecha del jugador.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = normalize(targetYaw - viewer.getLocation().getYaw());
        boolean enFrente = Math.abs(diff) <= FRONT_HALF_ANGLE;

        String colorClan = colorDe(mate);
        String nombre = colorClan + mate.getName();
        String distancia = String.format("%.0f m", dist);

        if (enFrente) {
            // Etiqueta sobre la cabeza
            m.bar.removePlayer(viewer);
            if (!viewer.canSee(m.display)) viewer.showEntity(this, m.display);

            m.display.teleport(target.clone().add(0, mate.getHeight() + 0.4, 0));
            m.display.text(LegacyComponentSerializer.legacySection()
                    .deserialize(nombre + " §7" + distancia));
        } else {
            // Fuera de pantalla: barra arriba con flecha de dirección
            viewer.hideEntity(this, m.display);

            String flecha;
            if (Math.abs(diff) > 130) flecha = "▼";
            else flecha = diff > 0 ? "▶" : "◀";

            m.bar.setTitle(ChatColor.YELLOW + flecha + " " + nombre + ChatColor.GRAY + " · " + distancia);
            m.bar.setVisible(true);
            if (!m.bar.getPlayers().contains(viewer)) m.bar.addPlayer(viewer);
        }
    }

    private String colorDe(Player mate) {
        ClanManager.Clan clan = clans.getClan(mate);
        if (clan == null || clan.getColor() == null) return "§f";
        return clan.getColor().toString();
    }

    private void destroy(Marker m) {
        m.bar.removeAll();
        m.bar.setVisible(false);
        m.display.remove();
    }

    private void clearViewer(UUID viewerId) {
        Map<UUID, Marker> mine = markers.remove(viewerId);
        if (mine != null) mine.values().forEach(this::destroy);
    }

    private static double normalize(double angle) {
        return ((angle + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }
}
