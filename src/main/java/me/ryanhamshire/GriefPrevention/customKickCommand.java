package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.DataStore.NoTransferException;
import me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent;
import me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import me.ryanhamshire.GriefPrevention.metrics.MetricsHandler;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.BanList;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class customKickCommand extends JavaPlugin

	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {

        Player player = null;
        if (sender instanceof Player)
        {
            player = (Player) sender;
        }

        //customkick
        else if (cmd.getName().equalsIgnoreCase("siegemodeisnowenabled") && player != null)
        {
            //FEATURE: empower players who get "stuck" in an area where they don't have permission to build to save themselves

            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);

            //if the player isn't in a claim or has permission to build, tell him to man up
            if (claim == null || claim.checkPermission(player, ClaimPermission.Build, null) == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotTrappedHere);
                return true;
            }

            //rescue destination may be set by GPFlags or other plugin, ask to find out
            SaveTrappedPlayerEvent event = new SaveTrappedPlayerEvent(claim);
            Bukkit.getPluginManager().callEvent(event);

            //if the player is in the nether or end, he's screwed (there's no way to programmatically find a safe place for him)
            if (player.getWorld().getEnvironment() != Environment.NORMAL && event.getDestination() == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return true;
            }

            //if the player is in an administrative claim and AllowTrappedInAdminClaims is false, he should contact an admin
            if (!GriefPrevention.instance.config_claims_allowTrappedInAdminClaims && claim.isAdminClaim() && event.getDestination() == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.TrappedWontWorkHere);
                return true;
            }
            //send instructions
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.RescuePending);

            //create a task to rescue this player in a little while
            PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation(), event.getDestination());
            this.getServer().getScheduler().scheduleSyncDelayedTask(this, task, 1L);  //20L ~ 1 second

            return true;
        }
    }
