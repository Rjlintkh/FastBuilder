package ai.pneuj.FastBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.minecraft.server.v1_8_R1.ChatSerializer;
import net.minecraft.server.v1_8_R1.MinecraftServer;
import net.minecraft.server.v1_8_R1.PacketPlayOutChat;
import net.minecraft.server.v1_8_R1.PacketPlayOutPlayerListHeaderFooter;

public class FastBuilder extends JavaPlugin implements Listener {
	class BridgingModeData {
		double personalBest = -1;
		double sessionBest = -1;
	}
	class BridgingData {
		private HashMap<String, BridgingModeData> data = new HashMap<String, BridgingModeData>();
		public BridgingData() {
			data.put("normal", new BridgingModeData());
			data.put("short", new BridgingModeData());
			data.put("inclined", new BridgingModeData());
			data.put("onestack", new BridgingModeData());
		}
		public BridgingModeData getData(String mode) {
			return data.get(mode);
		}
		public void putData(String mode, BridgingModeData value) {
			data.put(mode, value);
		}
	}
	HashMap<UUID, BridgingData> bridgingData = new HashMap<UUID, BridgingData>();
	HashMap<UUID, ArrayList<ArrayList<Long>>> cps = new HashMap<UUID, ArrayList<ArrayList<Long>>>();
	HashMap<UUID, Integer> time = new HashMap<UUID, Integer>();
	HashMap<UUID, ArrayList<Location>> placedBlocks = new HashMap<UUID, ArrayList<Location>>();
	
	public void startGame(Player player) {
		UUID uuid = player.getUniqueId();
		String mode = player.getWorld().getName();
		if (mode.equals("inclined")) {
			player.teleport(new Location(player.getWorld(), 2.5, 65, -2.5, 45, 0));
		} else {
			player.teleport(new Location(player.getWorld(), 0.5, 65, -2.5, 0, 0));
		}
		Inventory inventory = player.getInventory();
		inventory.clear();
		{
			ItemStack item = new ItemStack(Material.SANDSTONE, -1);
			inventory.setItem(0, item);
		}
		{
			ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE, 1);
			ItemMeta meta = item.getItemMeta();
			meta.spigot().setUnbreakable(true);
			meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
			item.setItemMeta(meta);
			inventory.setItem(1, item);
		}
		{
			ItemStack item = new ItemStack(Material.EMERALD, 1);
			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName("§rCosmetics");
			item.setItemMeta(meta);
			inventory.setItem(6, item);
		}
		{
			ItemStack item = new ItemStack(Material.COMPASS, 1);
			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName("§rModes");
			item.setItemMeta(meta);
			inventory.setItem(7, item);
		}
		{
			ItemStack item = new ItemStack(Material.REDSTONE_COMPARATOR, 1);
			ItemMeta meta = item.getItemMeta();
			meta.setDisplayName("§rSettings");
			item.setItemMeta(meta);
	        inventory.setItem(8, item);
		}
		BridgingData dataFull = bridgingData.get(uuid);
		BridgingModeData data = dataFull.getData(mode);
		Scoreboard scoreboard = player.getScoreboard();
	    Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
	    for (String row : scoreboard.getEntries()) {
            if (objective.getScore(row).getScore() == 14) {
                if (data.personalBest != -1) {
                	scoreboard.resetScores(row);
		    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(data.personalBest)).setScore(14);
	    	    } else {
                	scoreboard.resetScores(row);
		    	    objective.getScore("§8\u2502  §7-.---").setScore(14);
	    	    }
            }
            if (objective.getScore(row).getScore() == 11) {
                if (data.sessionBest != -1) {
                	scoreboard.resetScores(row);
		    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(data.sessionBest) + " ").setScore(11);
	    	    } else {
                	scoreboard.resetScores(row);
		    	    objective.getScore("§8\u2502  §7-.--- ").setScore(11);
	    	    }
            }
	    }
	}
	public void endGame(Player player, Boolean calculateScore) {
		UUID uuid = player.getUniqueId();
		if (time.get(uuid) != -1) {
			double currentTime = (double) (MinecraftServer.currentTick - time.get(uuid))/20;
    		Bukkit.getScheduler().runTaskLater(this, () -> {
    			if (calculateScore && time.get(uuid) != -1) {
    				String mode = player.getWorld().getName();
            		BridgingData dataFull = bridgingData.get(uuid);
            		BridgingModeData data = dataFull.getData(mode);
        			if (data.personalBest == -1 || data.personalBest > currentTime) {
	        			data.personalBest = currentTime;
	        			FileConfiguration fbData = getGameData();
	        			fbData.set(uuid.toString() + ".personal-best." + mode, data.personalBest);
	        			saveGameData(fbData);
	        		}
	        		if (data.sessionBest == -1 || data.sessionBest > currentTime) {
	        			data.sessionBest = currentTime;
	        		}
	        		dataFull.putData(mode, data);
	        		bridgingData.put(uuid, dataFull);
	        		Scoreboard scoreboard = player.getScoreboard();
		    	    Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
		    	    for (String row : scoreboard.getEntries()) {
		                if (objective.getScore(row).getScore() == 14) {
		                    if (data.personalBest != -1) {
		                    	scoreboard.resetScores(row);
					    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(data.personalBest)).setScore(14);
				    	    }
		                }
		                if (objective.getScore(row).getScore() == 11) {
		                    if (data.sessionBest != -1) {
		                    	scoreboard.resetScores(row);
					    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(data.sessionBest) + " ").setScore(11);
				    	    }
		                }
		    	    }
		    	    player.playSound(player.getLocation(), Sound.LEVEL_UP, 2.0F, 1.0F);
		    	    player.sendMessage("§7" + new String(new char[71]).replace("\0", "\u25AC"));
		    	    player.sendMessage(" ");
		    	    player.sendMessage("          §lFastBuilder");
		    	    player.sendMessage(" ");
		    	    player.sendMessage("          §aYou made it to the §l§3finish line§r§a.");
		    	    player.sendMessage(" ");
		    	    player.sendMessage("          §ePrevious §bbest§8: §a" + new DecimalFormat("0.000").format(data.personalBest));
		    	    player.sendMessage("          §eTime §btaken§8: §c" + new DecimalFormat("0.000").format(currentTime) + " §7|| §c" + (data.personalBest > currentTime ? "" : "+") + new DecimalFormat("0.000").format((currentTime - data.personalBest)));
		    	    player.sendMessage(" ");
		    	    player.sendMessage("§7" + new String(new char[71]).replace("\0", "\u25AC"));
        		}
        		time.put(uuid, -1);
        		ArrayList<Location> blocks = placedBlocks.get(uuid);
        		for (Location block : blocks) {
        		    block.getBlock().setType(Material.AIR);
        		}
        		placedBlocks.put(uuid, new ArrayList<Location>());
    		}, 1L);
    	}
	}
	
	public FileConfiguration getGameData() {
		File file = new File(getDataFolder() + File.separator + "fb_data.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration fbData = YamlConfiguration.loadConfiguration(file);
		return fbData;
	}
	
	public void saveGameData(FileConfiguration data) {
		File file = new File(getDataFolder() + File.separator + "fb_data.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			data.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onEnable() {
		getServer().createWorld(new WorldCreator("short"));
		getServer().createWorld(new WorldCreator("inclined"));
		getServer().createWorld(new WorldCreator("onestack"));
		getServer().getPluginManager().registerEvents(this, this);
		new BukkitRunnable() {
		    @Override
		    public void run() {
		    	for (Entry<UUID, BridgingData> entry : bridgingData.entrySet()) {
		    	    UUID uuid = entry.getKey();
		    	    Player player = Bukkit.getPlayer(uuid);
		    	    Scoreboard scoreboard = player.getScoreboard();
		    	    Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
		    	    double currentTime = (double) (MinecraftServer.currentTick - time.get(uuid))/20;
		    	    for (String row : scoreboard.getEntries()) {
		                if (objective.getScore(row).getScore() == 8) {
		                	double distance = Math.sqrt((Math.pow(player.getLocation().getX(), 2)) + (Math.pow(player.getLocation().getZ(), 2)));
		                    if (time.get(uuid) != -1 && (int) player.getLocation().getZ() >= 0) {
		                    	scoreboard.resetScores(row);
					    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.00").format(distance)).setScore(8);
				    	    }
		                }
		                if (objective.getScore(row).getScore() == 5) {
		                    if (time.get(uuid) != -1 && placedBlocks.get(uuid).size() >= 0) {
			                    scoreboard.resetScores(row);
					    	    objective.getScore("§8\u2502  §e" + placedBlocks.get(uuid).size()).setScore(5);
				    	    }
		                }
		                if (objective.getScore(row).getScore() == 2) {
		                    if (time.get(uuid) != -1) {
			                    scoreboard.resetScores(row);
					    	    objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(currentTime) + "  ").setScore(2);
				    	    }
		                }
		            }
		    	}
		    	for (Entry<UUID, ArrayList<ArrayList<Long>>> entry : cps.entrySet()) {
		    	    UUID uuid = entry.getKey();
		    	    ArrayList<ArrayList<Long>> clicks = entry.getValue();
		    	    for (int i = 0; i < clicks.get(0).size(); i++){
		    	    	if ((System.currentTimeMillis() - clicks.get(0).get(i)) > 1000) {
		    	        	   clicks.get(0).remove(i);
		    	    	}
		    	    }
		    	    for (int i = 0; i < clicks.get(1).size(); i++){
		    	    	if ((System.currentTimeMillis() - clicks.get(1).get(i)) > 1000) {
		    	        	   clicks.get(1).remove(i);
		    	    	}
		    	    }
		    	    cps.put(uuid, clicks);
		            PacketPlayOutChat packet = new PacketPlayOutChat(ChatSerializer.a("{\"text\": \"§3CPS: §a" + clicks.get(0).size() + "§3|§a" + clicks.get(1).size() + "\"}"), (byte) 2);
		            ((CraftPlayer) Bukkit.getPlayer(uuid)).getHandle().playerConnection.sendPacket(packet);
		    	}

		    }
		}.runTaskTimer(this, 0, 1L);
	}
	
	@Override
	public void onDisable() {
		
	}

	@EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		event.setJoinMessage(null);
		FileConfiguration fbData = getGameData();
		BridgingData dataFull = new BridgingData();
		double personalBestNormal = fbData.getDouble((uuid.toString() + ".personal-best.normal"));
		if (personalBestNormal != 0) {
			BridgingModeData data = dataFull.getData("normal");
			data.personalBest = personalBestNormal;
			dataFull.putData("normal", data);
		}
		double personalBestShort = fbData.getDouble((uuid.toString() + ".personal-best.short"));
		if (personalBestShort != 0) {
			BridgingModeData data = dataFull.getData("short");
			data.personalBest = personalBestShort;
			dataFull.putData("short", data);
		}
		double personalBestInclined = fbData.getDouble((uuid.toString() + ".personal-best.inclined"));
		if (personalBestInclined != 0) {
			BridgingModeData data = dataFull.getData("inclined");
			data.personalBest = personalBestInclined;
			dataFull.putData("inclined", data);
		}
		double personalBestOnestack = fbData.getDouble((uuid.toString() + ".personal-best.onestack"));
		if (personalBestOnestack != 0) {
			BridgingModeData data = dataFull.getData("onestack");
			data.personalBest = personalBestOnestack;
			dataFull.putData("onestack", data);
		}
		bridgingData.put(uuid, dataFull);
		cps.put(uuid, new ArrayList<ArrayList<Long>>(Arrays.asList(new ArrayList<Long>(), new ArrayList<Long>())));
		time.put(uuid, -1);
		placedBlocks.put(uuid, new ArrayList<Location>());
		player.teleport(new Location(Bukkit.getWorld("normal"), 0.5, 65, -2.5, 0, 0));
		{
			PacketPlayOutPlayerListHeaderFooter packet = new PacketPlayOutPlayerListHeaderFooter(ChatSerializer.a("{\"text\":\"§eWelcome\"}"));
			try {
				Field field = packet.getClass().getDeclaredField("b");
				field.setAccessible(true);
				field.set(packet,  ChatSerializer.a("{\"text\":\"§7-= §eFastBuilder §7=-\"}"));
			}	catch (Exception e) {
				e.printStackTrace();
			} finally {
				((CraftPlayer)player).getHandle().playerConnection.sendPacket(packet);
			}
		}
		{
    	    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    	    Objective objective = scoreboard.registerNewObjective("dummy", "fb-" + player.getUniqueId().toString());
    	    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    	    objective.setDisplayName("§6Fast Builder");
    	    objective.getScore("§8\u251C §l§bPersonal Best§r§7:").setScore(15);
    	    if (personalBestNormal == 0) {
    	    	objective.getScore("§8\u2502  §7-.---").setScore(14);
    	    } else {
    	    	objective.getScore("§8\u2502  §e" + new DecimalFormat("0.000").format(personalBestNormal)).setScore(14);
    	    }
    	    objective.getScore("§8\u2502").setScore(13);
    	    objective.getScore("§8\u251C §l§eSession Best§r§7:").setScore(12);
	    	objective.getScore("§8\u2502  §7-.--- ").setScore(11);
    	    objective.getScore("§8\u2502 ").setScore(10);
    	    objective.getScore("§8\u251C §l§9Distance§r§7:").setScore(9);
	    	objective.getScore("§8\u2502  §7-.--").setScore(8);
    	    objective.getScore("§8\u2502  ").setScore(7);
    	    objective.getScore("§8\u251C §l§3Blocks§r§7:").setScore(6);
	    	objective.getScore("§8\u2502  §7-").setScore(5);
    	    objective.getScore("§8\u2502   ").setScore(4);
    	    objective.getScore("§8\u251C §l§aTime§r§7:").setScore(3);
    	    objective.getScore("§8\u2502  §7-.---  ").setScore(2);
    	    objective.getScore("§8\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500").setScore(1);
    	    player.setScoreboard(scoreboard);
		}
		startGame(player);
	}
	
	@EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		ArrayList<Location> blocks = placedBlocks.get(uuid);
		for (Location block : blocks) {
		    block.getBlock().setType(Material.AIR);
		}
		bridgingData.remove(uuid);
		cps.remove(uuid);
		time.remove(uuid);
		placedBlocks.remove(uuid);
    }
	
	@EventHandler
	public void onPlayerInteractEvent(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
			ArrayList<ArrayList<Long>> clicks = cps.get(uuid);
			clicks.get(0).add(System.currentTimeMillis());
			cps.put(uuid, clicks);
		}
		if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			ArrayList<ArrayList<Long>> clicks = cps.get(uuid);
			clicks.get(1).add(System.currentTimeMillis());
			cps.put(uuid, clicks);
		}
		if (event.getAction() == Action.PHYSICAL) {
			if (event.getClickedBlock().getType() == Material.GOLD_PLATE){
				if (time.get(uuid) != -1) {
			    	endGame(player, true);
			    	Bukkit.getScheduler().runTaskLater(this, () -> {
		    			startGame(player);
		    		}, 40L);
				}
			}
		}
		if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (event.getItem() != null && event.getItem().getType() == Material.EMERALD) {
				Inventory inventory = Bukkit.createInventory(null, 27, "§aCosmetics");
				{
					ItemStack item = new ItemStack(Material.SANDSTONE, 51);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§eBlocks");
					item.setItemMeta(meta);
					inventory.setItem(12, item);
				}
				player.openInventory(inventory);
			}
			if (event.getItem() != null && event.getItem().getType() == Material.COMPASS) {
				Inventory inventory = Bukkit.createInventory(null, 27, "§eChoose Mode");
				{
					ItemStack item = new ItemStack(Material.SANDSTONE, 51);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3Normal");
					if (player.getWorld().getName().equals("normal")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(10, item);
				}
				{
					ItemStack item = new ItemStack(Material.SANDSTONE, 28);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3Short");
					if (player.getWorld().getName().equals("short")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(11, item);
				}
				{
					ItemStack item = new ItemStack(Material.STEP, 35, (byte) 1);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3Inclined");
					if (player.getWorld().getName().equals("inclined")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(13, item);
				}
				{
					ItemStack item = new ItemStack(Material.SANDSTONE_STAIRS, 28);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3OneStack");
					if (player.getWorld().getName().equals("onestack")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(14, item);
				}
				{
					ItemStack item = new ItemStack(Material.ENDER_PORTAL_FRAME, 1);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3Infinite");
					if (player.getWorld().getName().equals("infinite")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(16, item);
				}
				player.openInventory(inventory);
			}
			if (event.getItem() != null && event.getItem().getType() == Material.REDSTONE_COMPARATOR) {
				Inventory inventory = Bukkit.createInventory(null, 27, "§eSettings");
				{
					ItemStack item = new ItemStack(Material.ENDER_PORTAL_FRAME, 1);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§3Infinite");
					if (player.getWorld().getName().equals("infinite")) {
						meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, false);
						meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
					}
					item.setItemMeta(meta);
					inventory.setItem(13, item);
				}
				player.openInventory(inventory);
			}
		}
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		InventoryView view = event.getView();
		if (view.getTitle() == "§aCosmetics") {
			if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() == "§eBlocks") {
				Player player = Bukkit.getPlayer(event.getWhoClicked().getUniqueId());
				Inventory inventory = Bukkit.createInventory(null, 54, "§eBlocks");
				{
					ItemStack item = new ItemStack(Material.WOOL, 1);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§l§fWool Blocks");
					item.setItemMeta(meta);
					inventory.setItem(0, item);
				}
				{
					ItemStack item = new ItemStack(Material.WOOL, 1);
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName("§l§fWool Blocks");
					item.setItemMeta(meta);
					inventory.setItem(0, item);
				}
				view.close();
				player.openInventory(inventory);
			}
		}
		if (view.getTitle() == "§eChoose Mode") {
			if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() == "§3Normal") {
				Player player = Bukkit.getPlayer(event.getWhoClicked().getUniqueId());
				endGame(player, false);
				player.teleport(new Location(Bukkit.getWorld("normal"), 0.5, 65, -2.5, 0, 0));
				startGame(player);
				view.close();
			}
			if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() == "§3Short") {
				Player player = Bukkit.getPlayer(event.getWhoClicked().getUniqueId());
				endGame(player, false);
				player.teleport(new Location(Bukkit.getWorld("short"), 0.5, 65, -2.5, 0, 0));
				startGame(player);
				view.close();
			}
			if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() == "§3Inclined") {
				Player player = Bukkit.getPlayer(event.getWhoClicked().getUniqueId());
				endGame(player, false);
				player.teleport(new Location(Bukkit.getWorld("inclined"), 2.5, 65, -2.5, 45, 0));
				startGame(player);
				view.close();
			}
			if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() == "§3OneStack") {
				Player player = Bukkit.getPlayer(event.getWhoClicked().getUniqueId());
				endGame(player, false);
				player.teleport(new Location(Bukkit.getWorld("onestack"), 0.5, 65, -2.5, 0, 0));
				startGame(player);
				view.close();
			}
		}
		event.setCancelled(true);
	}

	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event){
        if (event.getEntity() instanceof Player){
            event.setCancelled(true);
        }
    }
	
	@EventHandler
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onWeatherChange(WeatherChangeEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onThunderChange(ThunderChangeEvent event) {
        event.setCancelled(true);
    }
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		Block block = event.getBlock();
		Location blockLocation = block.getLocation();
		ArrayList<Location> blocks = placedBlocks.get(uuid);
    	blocks.add(blockLocation);
    	placedBlocks.put(uuid, blocks);
    	if (time.get(uuid) == -1) {
    		time.put(uuid, MinecraftServer.currentTick);
    	}
	}
	
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		Block block = event.getBlock();
		Location blockLocation = block.getLocation();
		ArrayList<Location> blocks = placedBlocks.get(uuid);
		if (blocks.contains(blockLocation)) {
	    	blocks.remove(blocks.indexOf(blockLocation));
	    	placedBlocks.put(uuid, blocks);
		} else {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (player.getLocation().getY() <= 55) {
			endGame(player, false);
			startGame(player);
		}
	}
}
