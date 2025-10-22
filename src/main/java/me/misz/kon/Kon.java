package me.misz.kon;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Kon extends JavaPlugin implements Listener {
    private final Map<UUID, LivingEntity> summonedEntities = new HashMap<>();
    private final Map<UUID, Long> deathCooldowns = new HashMap<>();
    private final Set<UUID> needsSave = new HashSet<>();
    private File horseFile;
    private FileConfiguration horseData;
    private NamespacedKey saddleKey;
    private NamespacedKey entityTypeKey;

    private Map<EntityType, Boolean> animalEnabled = new HashMap<>();
    private Map<EntityType, Double> animalDefaultHealth = new HashMap<>();
    private Map<EntityType, Double> animalDefaultSpeed = new HashMap<>();
    private Map<EntityType, Double> animalDefaultJump = new HashMap<>();
    private Map<EntityType, Long> animalDeathCooldown = new HashMap<>();
    private Map<EntityType, Double> animalSpeedIncrement = new HashMap<>();
    private Map<EntityType, Double> animalSpeedMax = new HashMap<>();
    private Map<EntityType, Integer> animalSpeedCost = new HashMap<>();
    private Map<EntityType, Double> animalHealthIncrement = new HashMap<>();
    private Map<EntityType, Double> animalHealthMax = new HashMap<>();
    private Map<EntityType, Integer> animalHealthCost = new HashMap<>();
    private Map<EntityType, Double> animalJumpIncrement = new HashMap<>();
    private Map<EntityType, Double> animalJumpMax = new HashMap<>();
    private Map<EntityType, Integer> animalJumpCost = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        loadHorseData();
        loadConfig();
        saddleKey = new NamespacedKey(this, "saddle_uuid");
        entityTypeKey = new NamespacedKey(this, "entity_type");
        Objects.requireNonNull(getCommand("kon")).setExecutor((sender, command, label, args) -> {
            // Handle console-accessible commands first
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("give") && args.length == 3) {
                    if (!sender.hasPermission("kon.admin")) {
                        sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do dawania przedmiotów innym graczom!");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Gracz " + args[1] + " nie jest online!");
                        return true;
                    }
                    EntityType entityType;
                    String entityName;
                    switch (args[2].toLowerCase()) {
                        case "horse":
                            entityType = EntityType.HORSE;
                            entityName = "Koń";
                            break;
                        case "donkey":
                            entityType = EntityType.DONKEY;
                            entityName = "Osioł";
                            break;
                        case "camel":
                            entityType = EntityType.CAMEL;
                            entityName = "Wielbłąd";
                            break;
                        default:
                            sender.sendMessage(ChatColor.RED + "Nieprawidłowy typ: horse, donkey, camel");
                            return true;
                    }
                    if (!animalEnabled.getOrDefault(entityType, false)) {
                        sender.sendMessage(ChatColor.RED + "Ten typ zwierzęcia jest wyłączony w konfiguracji!");
                        return true;
                    }
                    ItemStack item = createAnimalItem(entityType, entityName);
                    target.getInventory().addItem(item);
                    sender.sendMessage(ChatColor.GREEN + "Dałeś graczowi " + target.getName() + " przedmiot: " + entityName);
                    target.sendMessage(ChatColor.GREEN + "Otrzymałeś przedmiot do przywołania " + getEntityName(entityType) + "!");
                    return true;
                }
                
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("kon.admin")) {
                        sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do przeładowania konfiguracji!");
                        return true;
                    }
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Konfiguracja została przeładowana!");
                    return true;
                }
            }
            
            // Player-only commands
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Tylko gracze mogą używać tej komendy!");
                return true;
            }
            
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                player.sendMessage(ChatColor.GOLD + "=== Pomoc Kon ===");
                player.sendMessage(ChatColor.YELLOW + "/kon help - Wyświetla tę pomoc");
                player.sendMessage(ChatColor.YELLOW + "/kon get <typ> - Daje przedmiot do przywołania zwierzęcia");
                player.sendMessage(ChatColor.YELLOW + "/kon give <gracz> <typ> - Daje przedmiot innemu graczowi");
                player.sendMessage(ChatColor.YELLOW + "/kon reload - Przeładowuje konfigurację");
                player.sendMessage(ChatColor.GRAY + "Dostępne typy: horse, donkey, camel");
                return true;
            }
            if (!player.hasPermission("kon.use")) {
                player.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
                return true;
            }
            if (args[0].equalsIgnoreCase("get") && args.length == 2) {
                EntityType entityType;
                String entityName;
                switch (args[1].toLowerCase()) {
                    case "horse":
                        entityType = EntityType.HORSE;
                        entityName = "Koń";
                        break;
                    case "donkey":
                        entityType = EntityType.DONKEY;
                        entityName = "Osioł";
                        break;
                    case "camel":
                        entityType = EntityType.CAMEL;
                        entityName = "Wielbłąd";
                        break;
                    default:
                        player.sendMessage(ChatColor.RED + "Nieprawidłowy typ: horse, donkey, camel");
                        return true;
                }
                if (!animalEnabled.getOrDefault(entityType, false)) {
                    player.sendMessage(ChatColor.RED + "Ten typ zwierzęcia jest wyłączony w konfiguracji!");
                    return true;
                }
                ItemStack item = createAnimalItem(entityType, entityName);
                player.getInventory().addItem(item);
                player.sendMessage(ChatColor.GREEN + "Otrzymałeś przedmiot do przywołania " + getEntityName(entityType) + "!");
                return true;
            }

            player.sendMessage(ChatColor.RED + "Nieznana komenda. Użyj /kon help");
            return true;
        });
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllHorseData();
            }
        }.runTaskTimer(this, 20 * 300, 20 * 300);
    }

    @Override
    public void onDisable() {
        for (LivingEntity entity : summonedEntities.values()) {
            if (entity != null && !entity.isDead()) entity.remove();
        }
        saveAllHorseData();
    }

    private void loadConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        for (EntityType type : new EntityType[]{
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.CAMEL
        }) {
            String section = "animals." + type.name().toLowerCase();
            animalEnabled.put(type, config.getBoolean(section + ".enabled", true));
            animalDefaultHealth.put(type, config.getDouble(section + ".default_health",
                type == EntityType.CAMEL ? 32.0 : 26.0));
            animalDefaultSpeed.put(type, config.getDouble(section + ".default_speed",
                type == EntityType.DONKEY ? 0.175 :
                type == EntityType.CAMEL ? 0.2 : 0.25));
            animalDefaultJump.put(type, config.getDouble(section + ".default_jump",
                type == EntityType.DONKEY ? 0.5 : 0.7));
            animalDeathCooldown.put(type, config.getLong(section + ".death_cooldown", 60 * 1000));
            animalSpeedIncrement.put(type, config.getDouble(section + ".speed_increment", 0.01));
            animalSpeedMax.put(type, config.getDouble(section + ".speed_max", 0.35));
            animalSpeedCost.put(type, config.getInt(section + ".speed_cost", 5));
            animalHealthIncrement.put(type, config.getDouble(section + ".health_increment", 2.0));
            animalHealthMax.put(type, config.getDouble(section + ".health_max",
                type == EntityType.CAMEL ? 48.0 : 40.0));
            animalHealthCost.put(type, config.getInt(section + ".health_cost", 5));
            animalJumpIncrement.put(type, config.getDouble(section + ".jump_increment", type == EntityType.HORSE ? 0.05 : 0.0));
            animalJumpMax.put(type, config.getDouble(section + ".jump_max", type == EntityType.HORSE ? 1.0 : 0.5));
            animalJumpCost.put(type, config.getInt(section + ".jump_cost", type == EntityType.HORSE ? 5 : 0));
        }

        for (EntityType type : animalEnabled.keySet()) {
            String section = "animals." + type.name().toLowerCase();
            config.set(section + ".enabled", animalEnabled.get(type));
            config.set(section + ".default_health", animalDefaultHealth.get(type));
            config.set(section + ".default_speed", animalDefaultSpeed.get(type));
            config.set(section + ".default_jump", animalDefaultJump.get(type));
            config.set(section + ".death_cooldown", animalDeathCooldown.get(type));
            config.set(section + ".speed_increment", animalSpeedIncrement.get(type));
            config.set(section + ".speed_max", animalSpeedMax.get(type));
            config.set(section + ".speed_cost", animalSpeedCost.get(type));
            config.set(section + ".health_increment", animalHealthIncrement.get(type));
            config.set(section + ".health_max", animalHealthMax.get(type));
            config.set(section + ".health_cost", animalHealthCost.get(type));
            config.set(section + ".jump_increment", animalJumpIncrement.get(type));
            config.set(section + ".jump_max", animalJumpMax.get(type));
            config.set(section + ".jump_cost", animalJumpCost.get(type));
        }
        try {
            config.save(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            getLogger().severe("Błąd podczas zapisywania config.yml: " + e.getMessage());
        }
    }

    private void loadHorseData() {
        horseFile = new File(getDataFolder(), "horses.yml");
        if (!horseFile.exists()) {
            horseFile.getParentFile().mkdirs();
            try {
                horseFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Nie można utworzyć horses.yml: " + e.getMessage());
            }
        }
        horseData = YamlConfiguration.loadConfiguration(horseFile);
        try {
            horseData.load(horseFile);
        } catch (Exception e) {
            getLogger().warning("Błąd podczas ładowania horses.yml: " + e.getMessage());
        }
    }

    private void saveHorseData(UUID saddleUUID, LivingEntity entity) {
        if (entity == null) return;
        String path = "horses." + saddleUUID.toString() + ".";
        horseData.set(path + "entity_type", entity.getType().name());
        horseData.set(path + "max_health", entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        horseData.set(path + "current_health", entity.getHealth());
        horseData.set(path + "speed", entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue());
        horseData.set(path + "name", entity.getCustomName());
        horseData.set(path + "last_hidden_time", System.currentTimeMillis());

        if (entity instanceof AbstractHorse horse) {
            ItemStack saddle = horse.getInventory().getSaddle();
            horseData.set(path + "saddle.present", saddle != null && !saddle.getType().isAir());
        }

        if (entity instanceof Horse horse) {
            horseData.set(path + "color", horse.getColor().name());
            horseData.set(path + "style", horse.getStyle().name());
            ItemStack armor = horse.getInventory().getArmor();
            if (armor != null && !armor.getType().isAir()) {
                horseData.set(path + "armor.type", armor.getType().name());
                horseData.set(path + "armor.durability", (int) armor.getDurability());
                if (armor.getType() == Material.LEATHER_HORSE_ARMOR) {
                    LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
                    if (meta != null && meta.getColor() != null) {
                        Color color = meta.getColor();
                        horseData.set(path + "armor.color.red", color.getRed());
                        horseData.set(path + "armor.color.green", color.getGreen());
                        horseData.set(path + "armor.color.blue", color.getBlue());
                    }
                }
            } else {
                horseData.set(path + "armor", null);
            }
        }

        if (entity instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
            horseData.set(path + "chest.present", true);
            ItemStack[] contents = chestedHorse.getInventory().getStorageContents();
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (ItemStack item : contents) {
                if (item != null && !item.getType().isAir()) {
                    serialized.add(item.serialize());
                } else {
                    serialized.add(null);
                }
            }
            horseData.set(path + "chest.contents", serialized);
        } else {
            horseData.set(path + "chest.present", false);
            horseData.set(path + "chest.contents", null);
        }

        if (entity instanceof Horse horse) {
            horseData.set(path + "jump", horse.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).getBaseValue());
        }

        needsSave.remove(saddleUUID);
    }

    private void saveAllHorseData() {
        for (UUID saddleUUID : new ArrayList<>(needsSave)) {
            LivingEntity entity = summonedEntities.get(saddleUUID);
            saveHorseData(saddleUUID, entity);
        }
        try {
            horseData.save(horseFile);
        } catch (IOException e) {
            getLogger().severe("Błąd podczas zapisywania horses.yml: " + e.getMessage());
        }
    }

    private LivingEntity loadHorseAttributes(Player player, Location loc, UUID saddleUUID) {
        String path = "horses." + saddleUUID.toString() + ".";
        if (!horseData.contains(path)) return null;
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(horseData.getString(path + "entity_type", "HORSE"));
        } catch (Exception e) {
            entityType = EntityType.HORSE;
        }
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, entityType);

        String defaultName = switch (entityType) {
            case HORSE -> ChatColor.GOLD + "Koń";
            case DONKEY -> ChatColor.GOLD + "Osioł";
            case CAMEL -> ChatColor.GOLD + "Wielbłąd";
            default -> ChatColor.GOLD + "Zwierzę";
        };
        entity.setCustomName(horseData.getString(path + "name", defaultName));

        entity.getPersistentDataContainer().set(saddleKey, PersistentDataType.STRING, saddleUUID.toString());
        if (entity instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(player);
        }
        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
        double maxHealth = horseData.getDouble(path + "max_health", animalDefaultHealth.getOrDefault(entityType, 26.0));
        double speed = horseData.getDouble(path + "speed", animalDefaultSpeed.getOrDefault(entityType, 0.25));
        double currentHealth = horseData.getDouble(path + "current_health", maxHealth);
        long lastHidden = horseData.getLong(path + "last_hidden_time", 0);
        long timePassedMs = System.currentTimeMillis() - lastHidden;
        double regenAmount = timePassedMs / 10000.0;
        double newHealth = currentHealth + regenAmount;
        if (newHealth > maxHealth) newHealth = maxHealth;
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        entity.setHealth(newHealth);
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

        if (entity instanceof Horse horse) {
            double jump = horseData.getDouble(path + "jump", animalDefaultJump.getOrDefault(entityType, 0.7));
            horse.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).setBaseValue(jump);
            try {
                horse.setColor(Horse.Color.valueOf(horseData.getString(path + "color")));
                horse.setStyle(Horse.Style.valueOf(horseData.getString(path + "style")));
            } catch (Exception e) {
                horse.setColor(Horse.Color.BROWN);
                horse.setStyle(Horse.Style.BLACK_DOTS);
            }
        }

        if (horseData.contains(path + "armor.type") && entity instanceof Horse horse) {
            Material armorType = Material.getMaterial(horseData.getString(path + "armor.type"));
            if (armorType != null) {
                ItemStack armor = new ItemStack(armorType);
                armor.setDurability((short) horseData.getInt(path + "armor.durability", 0));
                if (armorType == Material.LEATHER_HORSE_ARMOR && horseData.contains(path + "armor.color.red")) {
                    int red = horseData.getInt(path + "armor.color.red");
                    int green = horseData.getInt(path + "armor.color.green");
                    int blue = horseData.getInt(path + "armor.color.blue");
                    Color color = Color.fromRGB(red, green, blue);
                    LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
                    meta.setColor(color);
                    armor.setItemMeta(meta);
                }
                horse.getInventory().setArmor(armor);
            }
        }

        if (horseData.getBoolean(path + "saddle.present", false)) {
            if (entity instanceof AbstractHorse horse) {
                horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            }
        }

        if (horseData.getBoolean(path + "chest.present", false) && entity instanceof ChestedHorse chestedHorse) {
            chestedHorse.setCarryingChest(true);
            List<?> rawList = horseData.getList(path + "chest.contents");
            if (rawList != null && !rawList.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> serializedContents = (List<Map<String, Object>>) (List<?>) rawList;
                ItemStack[] contents = new ItemStack[serializedContents.size()];
                for (int i = 0; i < serializedContents.size(); i++) {
                    Map<String, Object> itemMap = serializedContents.get(i);
                    if (itemMap != null) {
                        try {
                            contents[i] = ItemStack.deserialize(itemMap);
                        } catch (Exception e) {
                            contents[i] = null;
                        }
                    } else {
                        contents[i] = null;
                    }
                }
                chestedHorse.getInventory().setStorageContents(contents);
            }
        }

        return entity;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) return;
        Material material = item.getType();
        if (!(material == Material.CYAN_DYE || material == Material.ORANGE_DYE || material == Material.CLOCK)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return;
        List<String> validLore = Arrays.asList(
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego konia",
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego osła",
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego wielbłąda"
        );
        if (meta.getLore().stream().noneMatch(validLore::contains)) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(saddleKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID saddleUUID;
        try {
            saddleUUID = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        String path = "horses." + saddleUUID.toString() + ".";
        double speed = horseData.getDouble(path + "speed", animalDefaultSpeed.getOrDefault(EntityType.HORSE, 0.25));
        double maxHealth = horseData.getDouble(path + "max_health", animalDefaultHealth.getOrDefault(EntityType.HORSE, 26.0));
        double jump = horseData.getDouble(path + "jump", animalDefaultJump.getOrDefault(EntityType.HORSE, 0.7));
        boolean hasChest = horseData.getBoolean(path + "chest.present", false);
        EntityType entityType = EntityType.valueOf(horseData.getString(path + "entity_type", "HORSE"));
        String entityName = getEntityName(entityType);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego " + entityName);
        lore.add(ChatColor.YELLOW + "Szybkość: " + String.format("%.2f", speed));
        lore.add(ChatColor.YELLOW + "Zdrowie: " + String.format("%.1f", maxHealth));
        if (entityType == EntityType.HORSE) {
            lore.add(ChatColor.YELLOW + "Skok: " + String.format("%.2f", jump));
        }
        if (hasChest) {
            lore.add(ChatColor.YELLOW + "Skrzynia: Tak");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (deathCooldowns.containsKey(saddleUUID)) {
            long remaining = (deathCooldowns.get(saddleUUID) - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                long minutes = remaining / 60;
                long seconds = remaining % 60;
                player.sendMessage(ChatColor.RED + "Twój " + entityName + " odpoczywa... spróbuj ponownie za " +
                        ChatColor.YELLOW + minutes + "m " + seconds + "s" + ChatColor.RED + ".");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            } else {
                deathCooldowns.remove(saddleUUID);
            }
        }

        if (summonedEntities.containsKey(saddleUUID)) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ANVIL) {
                openUpgradeGUI(player, saddleUUID, entityType);
            } else {
                LivingEntity entity = summonedEntities.remove(saddleUUID);
                if (entity != null && !entity.isDead()) {
                    needsSave.add(saddleUUID);
                    saveHorseData(saddleUUID, entity);
                    entity.remove();
                    player.sendMessage(ChatColor.YELLOW + "Odesłałeś swojego " + entityName);
                    player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
                }
            }
            return;
        }

        LivingEntity entity = loadHorseAttributes(player, player.getLocation(), saddleUUID);
        if (entity == null) {
            EntityType saddleEntityType = EntityType.valueOf(pdc.get(entityTypeKey, PersistentDataType.STRING));
            double defaultHealth = animalDefaultHealth.getOrDefault(saddleEntityType, saddleEntityType == EntityType.CAMEL ? 32.0 : 26.0);
            double defaultSpeed = animalDefaultSpeed.getOrDefault(saddleEntityType, saddleEntityType == EntityType.DONKEY ? 0.175 : saddleEntityType == EntityType.CAMEL ? 0.2 : 0.25);
            double defaultJump = animalDefaultJump.getOrDefault(saddleEntityType, saddleEntityType == EntityType.DONKEY ? 0.5 : 0.7);
            entity = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), saddleEntityType);
            if (entity instanceof Tameable tameable) {
                tameable.setTamed(true);
                tameable.setOwner(player);
            }
            if (entity instanceof Ageable ageable) {
                ageable.setAdult();
            }
            entity.setCustomName(meta.getDisplayName());
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(defaultHealth);
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(defaultSpeed);
            entity.setHealth(defaultHealth);

            if (entity instanceof Horse horse) {
                Horse.Color[] colors = Horse.Color.values();
                Horse.Style[] styles = Horse.Style.values();
                Random rand = new Random();
                Horse.Color randomColor = colors[rand.nextInt(colors.length)];
                Horse.Style randomStyle = styles[rand.nextInt(styles.length)];
                horse.setColor(randomColor);
                horse.setStyle(randomStyle);
                horseData.set("horses." + saddleUUID.toString() + ".color", randomColor.name());
                horseData.set("horses." + saddleUUID.toString() + ".style", randomStyle.name());
                horse.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).setBaseValue(defaultJump);
            }

            if (saddleEntityType == EntityType.DONKEY && entity instanceof ChestedHorse chestedHorse) {
                chestedHorse.setCarryingChest(true);
            }

            entity.getPersistentDataContainer().set(saddleKey, PersistentDataType.STRING, saddleUUID.toString());
            needsSave.add(saddleUUID);
            saveHorseData(saddleUUID, entity);
        } else {
            entity.setCustomName(meta.getDisplayName());
        }
        summonedEntities.put(saddleUUID, entity);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation(), 10, 0.5, 0.5, 0.5);
        player.sendMessage(ChatColor.GREEN + "Przywołałeś swojego " + entityName);
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_AMBIENT, 1.0f, 1.0f);
    }

    private String getEntityName(EntityType type) {
        return switch (type) {
            case HORSE -> "konia";
            case DONKEY -> "osła";
            case CAMEL -> "wielbłąda";
            default -> "zwierzęcia";
        };
    }

    private ItemStack createAnimalItem(EntityType entityType, String entityName) {
        double defaultHealth = animalDefaultHealth.getOrDefault(entityType, 26.0);
        double defaultSpeed = animalDefaultSpeed.getOrDefault(entityType, 0.25);
        double defaultJump = animalDefaultJump.getOrDefault(entityType, 0.7);
        boolean hasChest = entityType == EntityType.DONKEY;

        Material summonItem = switch (entityType) {
            case HORSE -> Material.CYAN_DYE;
            case DONKEY -> Material.ORANGE_DYE;
            case CAMEL -> Material.CLOCK;
            default -> Material.CLOCK;
        };
        ItemStack item = new ItemStack(summonItem);
        ItemMeta meta = item.getItemMeta();
        UUID saddleUUID = UUID.randomUUID();
        meta.getPersistentDataContainer().set(saddleKey, PersistentDataType.STRING, saddleUUID.toString());
        meta.getPersistentDataContainer().set(entityTypeKey, PersistentDataType.STRING, entityType.name());

        meta.setDisplayName(ChatColor.GOLD + entityName);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        List<String> lore = new ArrayList<>();
        // use genitive form from getEntityName to match the validLore checks (e.g. "konia", "osła", "wielbłąda")
        lore.add(ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego " + getEntityName(entityType));
        lore.add(ChatColor.YELLOW + "Szybkość: " + String.format("%.2f", defaultSpeed));
        lore.add(ChatColor.YELLOW + "Zdrowie: " + String.format("%.1f", defaultHealth));
        if (entityType == EntityType.HORSE) {
            lore.add(ChatColor.YELLOW + "Skok: " + String.format("%.2f", defaultJump));
        }
        if (hasChest) {
            lore.add(ChatColor.YELLOW + "Skrzynia: Tak");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void openUpgradeGUI(Player player, UUID saddleUUID, EntityType entityType) {
        Inventory inv = Bukkit.createInventory(null, 9, "Ulepszanie Zwierzęcia");
        LivingEntity entity = summonedEntities.get(saddleUUID);
        if (entity == null) return;

        double currentSpeed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double currentMaxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        double currentJump = (entity instanceof Horse horse) ? horse.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).getBaseValue() : 0.5;

        ItemStack speedItem = new ItemStack(Material.FEATHER);
        ItemMeta speedMeta = speedItem.getItemMeta();
        speedMeta.setDisplayName(ChatColor.YELLOW + "Ulepsz Szybkość (+ " + animalSpeedIncrement.getOrDefault(entityType, 0.01) + ")");
        speedMeta.setLore(List.of(
                ChatColor.GRAY + "Koszt: " + animalSpeedCost.getOrDefault(entityType, 5) + " poziomów XP",
                ChatColor.GRAY + "Obecna: " + String.format("%.2f", currentSpeed)
        ));
        speedItem.setItemMeta(speedMeta);
        inv.setItem(2, speedItem);

        if (entityType == EntityType.HORSE) {
            ItemStack jumpItem = new ItemStack(Material.RABBIT_FOOT);
            ItemMeta jumpMeta = jumpItem.getItemMeta();
            jumpMeta.setDisplayName(ChatColor.YELLOW + "Ulepsz Skok (+ " + animalJumpIncrement.getOrDefault(entityType, 0.05) + ")");
            jumpMeta.setLore(List.of(
                    ChatColor.GRAY + "Koszt: " + animalJumpCost.getOrDefault(entityType, 5) + " poziomów XP",
                    ChatColor.GRAY + "Obecny: " + String.format("%.2f", currentJump)
            ));
            jumpItem.setItemMeta(jumpMeta);
            inv.setItem(4, jumpItem);
        }

        ItemStack healthItem = new ItemStack(Material.APPLE);
        ItemMeta healthMeta = healthItem.getItemMeta();
        healthMeta.setDisplayName(ChatColor.YELLOW + "Ulepsz Zdrowie (+ " + animalHealthIncrement.getOrDefault(entityType, 2.0) + ")");
        healthMeta.setLore(List.of(
                ChatColor.GRAY + "Koszt: " + animalHealthCost.getOrDefault(entityType, 5) + " poziomów XP",
                ChatColor.GRAY + "Obecne: " + String.format("%.1f", currentMaxHealth)
        ));
        healthItem.setItemMeta(healthMeta);
        inv.setItem(6, healthItem);

        ItemStack dismissItem = new ItemStack(Material.BARRIER);
        ItemMeta dismissMeta = dismissItem.getItemMeta();
        dismissMeta.setDisplayName(ChatColor.RED + "Odeślij Zwierzę");
        dismissItem.setItemMeta(dismissMeta);
        inv.setItem(8, dismissItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Ulepszanie Zwierzęcia")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null) return;
        Material heldType = held.getType();
        if (!(heldType == Material.CYAN_DYE || heldType == Material.ORANGE_DYE || heldType == Material.CLOCK)) {
            player.closeInventory();
            return;
        }
        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(saddleKey, PersistentDataType.STRING);
        if (uuidStr == null) {
            player.closeInventory();
            return;
        }
        UUID saddleUUID;
        try {
            saddleUUID = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            player.closeInventory();
            return;
        }
        LivingEntity entity = summonedEntities.get(saddleUUID);
        if (entity == null) {
            player.closeInventory();
            return;
        }
        String path = "horses." + saddleUUID.toString() + ".";
        EntityType entityType = EntityType.valueOf(horseData.getString(path + "entity_type", "HORSE"));
        String entityName = getEntityName(entityType);
        Material type = event.getCurrentItem().getType();

        if (type == Material.FEATHER) {
            handleUpgrade(player, entity, saddleUUID, entityType, entityName, "speed",
                    animalSpeedCost, animalSpeedIncrement, animalSpeedMax,
                    Attribute.GENERIC_MOVEMENT_SPEED);
        } else if (type == Material.RABBIT_FOOT && entityType == EntityType.HORSE) {
            handleUpgrade(player, entity, saddleUUID, entityType, entityName, "jump",
                    animalJumpCost, animalJumpIncrement, animalJumpMax,
                    Attribute.GENERIC_JUMP_STRENGTH);
        } else if (type == Material.APPLE) {
            handleUpgrade(player, entity, saddleUUID, entityType, entityName, "health",
                    animalHealthCost, animalHealthIncrement, animalHealthMax,
                    Attribute.GENERIC_MAX_HEALTH);
        } else if (type == Material.BARRIER) {
            summonedEntities.remove(saddleUUID);
            needsSave.add(saddleUUID);
            saveHorseData(saddleUUID, entity);
            entity.remove();
            player.sendMessage(ChatColor.YELLOW + "Odesłałeś swojego " + entityName);
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
            player.closeInventory();
        }
    }

    private void handleUpgrade(Player player, LivingEntity entity, UUID saddleUUID, EntityType entityType, String entityName,
                               String stat, Map<EntityType, Integer> costMap, Map<EntityType, Double> incrementMap,
                               Map<EntityType, Double> maxMap, Attribute attribute) {
        int cost = costMap.getOrDefault(entityType, 5);
        double increment = incrementMap.getOrDefault(entityType, 0.01);
        double max = maxMap.getOrDefault(entityType, 0.35);
        if (player.getLevel() < cost) {
            player.sendMessage(ChatColor.RED + "Potrzebujesz co najmniej " + cost + " poziomów XP!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        double currentValue = entity.getAttribute(attribute).getBaseValue();
        if (currentValue >= max) {
            player.sendMessage(ChatColor.RED + "Osiągnięto maksymalną wartość!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        entity.getAttribute(attribute).setBaseValue(currentValue + increment);
        String statName = switch (stat) {
            case "speed" -> "Szybkość";
            case "jump" -> "Skok";
            case "health" -> "Zdrowie";
            default -> stat;
        };
        player.sendMessage(ChatColor.GREEN + statName + " twojego " + entityName + " została ulepszona!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.setLevel(player.getLevel() - cost);
        needsSave.add(saddleUUID);
        saveHorseData(saddleUUID, entity);
        updateItemLore(player.getInventory().getItemInMainHand(), entity, entityType, saddleUUID);
        player.closeInventory();
    }

    private void updateItemLore(ItemStack item, LivingEntity entity, EntityType entityType, UUID saddleUUID) {
        ItemMeta meta = item.getItemMeta();
        String path = "horses." + saddleUUID.toString() + ".";
        double speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        double jump = (entity instanceof Horse horse) ? horse.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).getBaseValue() : 0.5;
        boolean hasChest = horseData.getBoolean(path + "chest.present", false);
        String entityName = getEntityName(entityType);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego " + entityName);
        lore.add(ChatColor.YELLOW + "Szybkość: " + String.format("%.2f", speed));
        lore.add(ChatColor.YELLOW + "Zdrowie: " + String.format("%.1f", maxHealth));
        if (entityType == EntityType.HORSE) {
            lore.add(ChatColor.YELLOW + "Skok: " + String.format("%.2f", jump));
        }
        if (hasChest) {
            lore.add(ChatColor.YELLOW + "Skrzynia: Tak");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack first = anvil.getItem(0);
        if (first == null) return;
        Material firstType = first.getType();
        if (!(firstType == Material.CYAN_DYE || firstType == Material.ORANGE_DYE || firstType == Material.CLOCK)) {
            return;
        }
        ItemMeta meta = first.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        List<String> validLore = Arrays.asList(
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego konia",
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego osła",
            ChatColor.GRAY + "Kliknij prawym przyciskiem, aby przywołać lub odesłać swojego wielbłąda"
        );
        if (meta.getLore().stream().noneMatch(validLore::contains)) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(saddleKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID saddleUUID = UUID.fromString(uuidStr);
        ItemStack result = anvil.getItem(2);
        if (result != null && result.hasItemMeta()) {
            ItemMeta resultMeta = result.getItemMeta();
            if (resultMeta.hasDisplayName()) {
                String newName = resultMeta.getDisplayName();
                Material resultType = switch (firstType) {
                    case CYAN_DYE -> Material.CYAN_DYE;
                    case ORANGE_DYE -> Material.ORANGE_DYE;
                    case CLOCK -> Material.CLOCK;
                    default -> Material.CLOCK;
                };
                ItemStack newResult = new ItemStack(resultType);
                ItemMeta newMeta = newResult.getItemMeta();
                newMeta.setDisplayName(newName);
                newMeta.setLore(meta.getLore());
                newMeta.getPersistentDataContainer().set(saddleKey, PersistentDataType.STRING, uuidStr);
                newMeta.getPersistentDataContainer().set(entityTypeKey, PersistentDataType.STRING, pdc.get(entityTypeKey, PersistentDataType.STRING));
                newMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                newResult.setItemMeta(newMeta);
                event.setResult(newResult);
                if (summonedEntities.containsKey(saddleUUID)) {
                    LivingEntity entity = summonedEntities.get(saddleUUID);
                    entity.setCustomName(newName);
                    needsSave.add(saddleUUID);
                    saveHorseData(saddleUUID, entity);
                }
            }
        }
    }
    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String uuidStr = entity.getPersistentDataContainer().get(saddleKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID saddleUUID = UUID.fromString(uuidStr);
        if (summonedEntities.containsKey(saddleUUID) && summonedEntities.get(saddleUUID).equals(entity)) {
            EntityType entityType = entity.getType();
            String entityName = getEntityName(entityType);
            Player owner = null;
            if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player p) {
                owner = p;
            }
            summonedEntities.remove(saddleUUID);
            deathCooldowns.put(saddleUUID, System.currentTimeMillis() + animalDeathCooldown.getOrDefault(entityType, 60L * 1000));
            if (owner != null) {
                owner.sendMessage(ChatColor.RED + "Twój " + entityName + " zginął! Możesz go przywołać ponownie za "
                        + ChatColor.YELLOW + (animalDeathCooldown.getOrDefault(entityType, 60L) / 60000) + " minut" + ChatColor.RED + ".");
            }

            if (entity instanceof AbstractHorse horse) {
                ItemStack saddle = horse.getInventory().getSaddle();
                if (saddle != null && !saddle.getType().isAir()) {
                    event.getDrops().add(saddle);
                }
                if (horse instanceof Horse h) {
                    ItemStack armor = h.getInventory().getArmor();
                    if (armor != null && !armor.getType().isAir()) {
                        event.getDrops().add(armor);
                    }
                }
            }

            if (entity instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
                ItemStack[] contents = chestedHorse.getInventory().getStorageContents();
                chestedHorse.getInventory().clear(); 
                for (ItemStack item : contents) {
                    if (item != null && !item.getType().isAir()) {
                        event.getDrops().add(item); 
                    }
                }
            }

            String path = "horses." + saddleUUID.toString() + ".";
            horseData.set(path + "saddle.present", null);
            horseData.set(path + "armor", null);
            horseData.set(path + "chest.present", null);
            horseData.set(path + "chest.contents", null);

            try {
                horseData.save(horseFile);
            } catch (IOException e) {
                getLogger().warning("nie można zapisać horse.yml po śmierci: " + e.getMessage());
            }
            needsSave.remove(saddleUUID);
        }
    }
}