package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WhereCommand implements BasicCommand {

    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (!(stack.getSender() instanceof Player)) {
            stack.getSender().sendMessage("This command can only be used by players!");
            return;
        }
        Player player = (Player) stack.getSender();
        if (!player.hasPermission("t+-.where")) {
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /where");
            return;
        }
        int xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        int zOffset = Terraplusminus.config.getInt("terrain_offset.z");

        TextComponent message = new TextComponent(Terraplusminus.config.getString("prefix"));

        double playerX = player.getLocation().getX() - xOffset;
        double playerZ = player.getLocation().getZ() - zOffset;
        try {
            double[] coordinates = this.bteGeneratorSettings.projection().toGeo(playerX, playerZ);
            message.addExtra("§7Your coordinates are:");
            message.addExtra("\n§8" + coordinates[1] + ", " + coordinates[0] + "§7.");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://maps.google.com/maps?t=k&q=loc:" + coordinates[1] + "+" + coordinates[0]));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7Click here to view in Google Maps.").create()));
        } catch (OutOfProjectionBoundsException e) {
            message.addExtra(ChatColor.RED + "You are currently outside of the world's projection and your location in the Minecraft world has no equivalent on Earth.");
        }
        player.spigot().sendMessage(message);
    }
}
