package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerMoveListener implements Listener {

    private final Set<UUID> msgCooldown = new HashSet<>();
    private final Set<Long> retryCooldown = new HashSet<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location to = event.getTo();
        if (to == null) return;

        int cx = to.getBlockX() >> 4;
        int cz = to.getBlockZ() >> 4;
        long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);

        if (ChunkStatusCache.isFailed(cx, cz)) {
            event.setTo(event.getFrom());

            UUID uuid = event.getPlayer().getUniqueId();
            if (!msgCooldown.contains(uuid)) {
                event.getPlayer().sendMessage("§c§l[!] §7The terrain ahead of you didn't load. waiting...");
                msgCooldown.add(uuid);
                org.bukkit.Bukkit.getScheduler().runTaskLater(Terraplusminus.instance, () -> msgCooldown.remove(uuid), 80L);
            }

            if (!retryCooldown.contains(chunkKey)) {
                retryCooldown.add(chunkKey);

                World world = to.getWorld();

                org.bukkit.Bukkit.getScheduler().runTaskLater(Terraplusminus.instance, () -> {
                    ChunkStatusCache.removeFailure(cx, cz);

                    if (world != null) {
                        world.unloadChunk(cx, cz, false);
                        world.getChunkAtAsync(cx, cz, chunk -> {
                        });
                    }

                    org.bukkit.Bukkit.getScheduler().runTaskLater(Terraplusminus.instance, () -> retryCooldown.remove(chunkKey), 100L);
                }, 200L);
            }
        }
    }
}