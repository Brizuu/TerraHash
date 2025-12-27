package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        int cx = to.getBlockX() >> 4;
        int cz = to.getBlockZ() >> 4;

        if (ChunkStatusCache.isFailed(cx, cz)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c§l[!] §7Teren jeszcze się nie pobrał. Poczekaj chwilę...");

            Bukkit.getScheduler().runTaskLater(Terraplusminus.instance, () -> {
                ChunkStatusCache.remove(cx, cz);
            }, 60L);
        }
    }
}