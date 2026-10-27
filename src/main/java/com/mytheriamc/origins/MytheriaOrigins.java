package com.mytheriamc.origins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class MytheriaOrigins extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Origin> origins = new ConcurrentHashMap<>();
    private final Map<UUID, Long> originExpires = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> waterDisabled = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shulkerShieldDisabled = new ConcurrentHashMap<>();
    private DiscordWebhook discord;
    private NamespacedKey originKey, menuKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        originKey = new NamespacedKey(this, "origin");
        menuKey = new NamespacedKey(this, "menu");
        discord = new DiscordWebhook(this);
        loadPlayers();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("origins")).setExecutor(this);
        Objects.requireNonNull(getCommand("origin")).setExecutor(this);
        new BukkitRunnable() { @Override public void run() {
            for (Player p : Bukkit.getOnlinePlayers()) { applyEffects(p); ensureBreezeMace(p); updateTraits(p); }
            markWetMobs();
        }}.runTaskTimer(this, 20L, 20L);
        getLogger().info("Mytheria Origins is ready.");
    }
    @Override public void onDisable() { savePlayers(); }

    private void loadPlayers() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        for (String id : getConfig().getStringList("players")) {
            Origin origin = Origin.fromKey(getConfig().getString("player-origins." + id, ""));
            UUID uuid = UUID.fromString(id);
            if (origin != null) origins.put(uuid, origin);
            long expires = getConfig().getLong("origin-expires." + id, 0L);
            if (expires > 0) originExpires.put(uuid, expires);
            else if (origin != null) originExpires.put(uuid, System.currentTimeMillis() + getConfig().getLong("menu.rental-hours", 168) * 3600000L);
        }
    }
    private void savePlayers() {
        for (Map.Entry<UUID, Origin> entry : origins.entrySet())
            getConfig().set("player-origins." + entry.getKey(), entry.getValue().key());
        for (Map.Entry<UUID, Long> entry : originExpires.entrySet())
            getConfig().set("origin-expires." + entry.getKey(), entry.getValue());
        getConfig().set("players", origins.keySet().stream().map(UUID::toString).toList());
        saveConfig();
    }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private Origin origin(Player p) {
        Long expires = originExpires.get(p.getUniqueId());
        if (expires != null && expires <= System.currentTimeMillis()) {
            origins.remove(p.getUniqueId());
            originExpires.remove(p.getUniqueId());
            getConfig().set("player-origins." + p.getUniqueId(), null);
            getConfig().set("origin-expires." + p.getUniqueId(), null);
            removeOriginEffects(p);
            tell(p, "&cYour origin rental has expired. Choose a new origin from the menu.");
            return null;
        }
        return origins.get(p.getUniqueId());
    }
    private boolean enabled(Origin o) { return getConfig().getBoolean("origins." + o.key() + ".enabled", true); }
    private void tell(Player p, String message) {
        String prefix = getConfig().getString("settings.messages.prefix", "&dMytheria &8» &f");
        p.sendMessage(color(prefix + message));
    }
    private boolean cooled(Player p, String id, int seconds) {
        long end = cooldowns.computeIfAbsent(p.getUniqueId(), ignored -> new HashMap<>()).getOrDefault(id, 0L);
        if (end > System.currentTimeMillis()) {
            tell(p, "&cThat ability is cooling down for " + ((end - System.currentTimeMillis()) / 1000 + 1) + "s.");
            return false;
        }
        cooldowns.get(p.getUniqueId()).put(id, System.currentTimeMillis() + seconds * 1000L);
        return true;
    }
    private boolean disabled(Player p) {
        Long until = waterDisabled.get(p.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) return true;
        if (p.isInWater()) {
            waterDisabled.put(p.getUniqueId(), System.currentTimeMillis() + getConfig().getInt("settings.water-disable-seconds", 180) * 1000L);
            tell(p, "&cYour powers are extinguished while you are in water.");
            return true;
        }
        return false;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can open this menu."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("vault")) {
            if (origin(p) == Origin.SHULKER) openVault(p); else tell(p, "&cOnly the Shulker origin can use a private vault.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!p.hasPermission("mytheriaorigins.admin")) { tell(p, "&cNo permission."); return true; }
            openAdmin(p); return true;
        }
        openOrigins(p); return true;
    }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name)); meta.setLore(Arrays.stream(lore).map(this::color).toList());
        stack.setItemMeta(meta); return stack;
    }
    private void openOrigins(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menu.title", "&d&lMytheria Origins")));
        Origin current = origin(p);
        String time = current == null ? "No active rental" : rentalTime(p);
        inv.setItem(4, item(Material.NETHER_STAR, "&d&lYour origin", "&7Current: &f" + (current == null ? "None" : current.displayName()), "&7Time remaining: &f" + time));
        for (Origin o : Origin.values()) if (enabled(o)) {
            int slot = getConfig().getInt("menu.item-slots." + o.key(), 10 + o.ordinal());
            String price = origin(p) == o ? "&aActive" : "&e" + getConfig().getInt("menu.origin-price", 250) + " diamonds";
            inv.setItem(slot++, item(o.icon(), "&d&l" + o.displayName(), "&7Click to buy or select.", price));
        }
        if (getConfig().getBoolean("menu.show-help-button", true))
            inv.setItem(20, item(Material.BOOK, "&d&lHow to play", "&7Right-click: race ability.", "&7Shift + right-click: aimed ability.", "&7Use your origin relic for its ultimate."));
        if (getConfig().getBoolean("menu.show-vault-button", true))
            inv.setItem(22, item(Material.ENDER_CHEST, "&5&lPrivate vault", "&7Available to Shulkers.", "&7Click to open your 27-slot vault."));
        if (p.hasPermission("mytheriaorigins.admin") && getConfig().getBoolean("menu.show-admin-button", true))
            inv.setItem(26, item(Material.COMPARATOR, "&c&lAdmin panel", "&7Click to open controls."));
        p.openInventory(inv);
    }
    private void openAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&5&lOrigins Admin"));
        inv.setItem(11, item(Material.RECOVERY_COMPASS, "&dReload config", "&7Reload configuration from disk."));
        inv.setItem(13, item(Material.BARRIER, "&cClear your origin", "&7Removes your current origin."));
        inv.setItem(15, item(Material.NETHER_STAR, "&dGive yourself a random origin", "&7Useful for testing."));
        inv.setItem(22, item(Material.BOOK, "&dAdmin help", "&7Prices, cooldowns and enabled origins", "&7are controlled in config.yml."));
        p.openInventory(inv);
    }
    private void openVault(Player p) {
        Inventory vault = Bukkit.createInventory(null, 27, color("&5&l" + p.getName() + "'s Private Vault"));
        String path = "vaults." + p.getUniqueId();
        for (int i = 0; i < 27; i++) {
            ItemStack item = getConfig().getItemStack(path + "." + i);
            if (item != null) vault.setItem(i, item);
        }
        p.openInventory(vault);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null) return;
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.contains("Origins") && !title.contains("Private Vault")) return;
        if (title.contains("Private Vault")) return;
        e.setCancelled(true); ItemStack clicked = e.getCurrentItem(); Material type = clicked.getType();
        if (title.contains("Admin")) {
            if (type == Material.RECOVERY_COMPASS) { reloadConfig(); tell(p, "&aConfig reloaded."); openAdmin(p); }
            else if (type == Material.BARRIER) { origins.remove(p.getUniqueId()); originExpires.remove(p.getUniqueId()); removeOriginEffects(p); savePlayers(); discord.send("admin", p.getName() + " cleared their origin."); tell(p, "&aYour origin was cleared."); openAdmin(p); }
            else if (type == Material.NETHER_STAR) { Origin[] values = Origin.values(); setOrigin(p, values[new Random().nextInt(values.length)]); openAdmin(p); }
            return;
        }
        if (type == Material.COMPARATOR && p.hasPermission("mytheriaorigins.admin")) { openAdmin(p); return; }
        if (type == Material.ENDER_CHEST) { if (origin(p) == Origin.SHULKER) openVault(p); else tell(p, "&cOnly Shulkers have a private vault."); return; }
        if (type == Material.BOOK) { tell(p, "&dChoose an origin, then use its relic and ability items. Your choice is saved automatically."); return; }
        Origin chosen = Arrays.stream(Origin.values()).filter(o -> o.icon() == type).findFirst().orElse(null);
        if (chosen == null) return;
        if (origin(p) == chosen) { tell(p, "&eYou already have this origin."); return; }
        int cost = getConfig().getInt("origins." + chosen.key() + ".price", getConfig().getInt("menu.origin-price", 250));
        if (!p.hasPermission("mytheriaorigins.admin") && !takeDiamonds(p, cost)) {
            tell(p, getConfig().getString("settings.messages.not-enough-diamonds", "&cYou need {amount} diamonds.").replace("{amount}", String.valueOf(cost)));
            return;
        }
        setOrigin(p, chosen); p.closeInventory();
    }
    private boolean takeDiamonds(Player p, int amount) {
        int total = 0; for (ItemStack s : p.getInventory().getContents()) if (s != null && s.getType() == Material.DIAMOND) total += s.getAmount();
        if (total < amount) return false; int left = amount;
        for (int i = 0; i < p.getInventory().getSize() && left > 0; i++) {
            ItemStack s = p.getInventory().getItem(i); if (s == null || s.getType() != Material.DIAMOND) continue;
            int take = Math.min(left, s.getAmount()); s.setAmount(s.getAmount() - take); left -= take;
        } return true;
    }
    private void setOrigin(Player p, Origin chosen) {
        origins.put(p.getUniqueId(), chosen);
        originExpires.put(p.getUniqueId(), System.currentTimeMillis() + getConfig().getLong("menu.rental-hours", 168) * 3600000L);
        savePlayers(); applyEffects(p);
        discord.send("origin-selected", p.getName() + " became a " + chosen.displayName() + ".");
        tell(p, getConfig().getString("settings.messages.purchase-success", "&dYou are now a &f{origin}&d for {hours} hours.")
                .replace("{origin}", chosen.displayName())
                .replace("{hours}", String.valueOf(getConfig().getLong("menu.rental-hours", 168))));
    }
    private void applyEffects(Player p) {
        Origin o = origin(p); if (o == null) return;
        removeOriginEffects(p);
        if (o == Origin.BLAZE) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        if (o == Origin.BREEZE) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        if (o == Origin.GUARDIAN) p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false));
    }
    private void removeOriginEffects(Player p) {
        for (PotionEffectType type : List.of(PotionEffectType.FIRE_RESISTANCE, PotionEffectType.SPEED, PotionEffectType.WATER_BREATHING, PotionEffectType.NIGHT_VISION, PotionEffectType.JUMP_BOOST))
            p.removePotionEffect(type);
    }
    private String rentalTime(Player p) {
        long seconds = Math.max(0, (originExpires.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis()) / 1000);
        return (seconds / 86400) + "d " + (seconds / 3600 % 24) + "h " + (seconds / 60 % 60) + "m";
    }
    private void ensureBreezeMace(Player p) {
        if (origin(p) != Origin.BREEZE) return;
        for (ItemStack stack : p.getInventory().getContents()) if (stack != null && stack.getType() == Material.MACE && !stack.containsEnchantment(Enchantment.WIND_BURST))
            stack.addUnsafeEnchantment(Enchantment.WIND_BURST, 1);
    }
    private void updateTraits(Player p) {
        Origin o = origin(p);
        if (o == Origin.BLAZE && p.getLocation().getBlock().isLiquid() && getConfig().getBoolean("settings.enable-blaze-lava-vision", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 40, 0, false, false));
        }
        if (o == Origin.BLAZE && p.getLocation().getBlock().isLiquid() && getConfig().getBoolean("settings.enable-blaze-lava-swim", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, false, false));
        }
        if (o == Origin.BREEZE && getConfig().getBoolean("settings.enable-breeze-jump", true)) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40,
                getConfig().getInt("settings.breeze-jump-amplifier", 1), false, false));
        if (o == Origin.BREEZE && p.getAttribute(Attribute.ATTACK_SPEED) != null)
            p.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(getConfig().getDouble("settings.breeze-attack-speed", 5.0));
        if (o == Origin.GUARDIAN) {
            double health = p.isInWater() ? getConfig().getDouble("settings.guardian-water-health", 24.0)
                    : getConfig().getDouble("settings.guardian-land-health", 16.0);
            if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
                p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                if (p.getHealth() > health) p.setHealth(health);
            }
        }
    }
    private void markWetMobs() {
        NamespacedKey wet = new NamespacedKey(this, "wet-mob");
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
                Location location = entity.getLocation();
                if (location.getBlock().isLiquid() || (world.hasStorm() && world.getHighestBlockYAt(location) <= location.getBlockY()))
                    entity.getPersistentDataContainer().set(wet, PersistentDataType.BYTE, (byte) 1);
        }
    }
    @EventHandler public void join(PlayerJoinEvent e) { applyEffects(e.getPlayer()); }
    @EventHandler public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer(); Origin o = origin(p); if (o == null || !p.hasPermission("mytheriaorigins.use") || disabled(p)) return;
        if (o == Origin.BLAZE && !p.isSneaking() && e.getItem() != null && e.getItem().getType() == Material.BLAZE_ROD) { blazeRelic(p); e.setCancelled(true); }
        else if (o == Origin.BREEZE && (e.getItem() == null || e.getItem().getType() == Material.WIND_CHARGE)) {
            if (p.isSneaking()) breezeRelic(p); else wind(p);
            e.setCancelled(true);
        }
        else if (o == Origin.SHULKER && e.getItem() != null && e.getItem().getType() == Material.SHULKER_SHELL) { shulkerRelic(p); e.setCancelled(true); }
        else if (o == Origin.GUARDIAN && e.getItem() != null && e.getItem().getType() == Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE) { guardianRelic(p); e.setCancelled(true); }
    }
    private void blazeRelic(Player p) {
        int cd = getConfig().getInt("settings.blaze-relic-cooldown-seconds", 120); if (!cooled(p, "blaze-relic", cd)) return;
        int fireTicks = getConfig().getInt("settings.blaze-relic-fire-ticks", 1200);
        for (Player target : Bukkit.getOnlinePlayers()) { target.setFireTicks(fireTicks); }
        int soldiers = Math.max(0, getConfig().getInt("settings.blaze-relic-soldier-count", 3));
        for (int i = 0; i < soldiers; i++) {
            Blaze blaze = (Blaze) p.getWorld().spawnEntity(p.getLocation().add(i - 1, 0, 0), EntityType.BLAZE);
            blaze.setCustomName(color("&6" + p.getName() + "'s Blaze Soldier"));
            blaze.setPersistent(true);
            if (blaze.getAttribute(Attribute.MAX_HEALTH) != null) blaze.getAttribute(Attribute.MAX_HEALTH).setBaseValue(getConfig().getDouble("settings.blaze-soldier-health", 40.0));
            if (blaze.getAttribute(Attribute.ATTACK_DAMAGE) != null) blaze.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(getConfig().getDouble("settings.blaze-soldier-damage", 8.0));
            blaze.setHealth(getConfig().getDouble("settings.blaze-soldier-health", 40.0));
        }
        discord.send("relic-used", p.getName() + " unleashed the Blaze relic.");
        tell(p, "&6The inferno answers your call.");
    }
    private void wind(Player p) {
        if (!cooled(p, "wind-charge", getConfig().getInt("settings.wind-charge-cooldown-seconds", 2))) return;
        WindCharge charge = (WindCharge) p.getWorld().spawnEntity(p.getEyeLocation().add(p.getLocation().getDirection()), EntityType.WIND_CHARGE);
        charge.setVelocity(p.getLocation().getDirection().multiply(getConfig().getDouble("settings.wind-charge-speed", 1.3))); tell(p, "&bWind charge launched.");
    }
    private void breezeRelic(Player p) {
        if (!cooled(p, "breeze-relic", getConfig().getInt("settings.breeze-relic-cooldown-seconds", 60))) return;
        double radius = getConfig().getDouble("settings.breeze-relic-radius", 9.0);
        double horizontal = getConfig().getDouble("settings.breeze-relic-horizontal-force", 1.35);
        double vertical = getConfig().getDouble("settings.breeze-relic-vertical-force", 1.1);
        for (Entity entity : p.getNearbyEntities(radius, radius, radius)) if (entity instanceof LivingEntity living && living != p) {
            Vector push = living.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(horizontal);
            living.setVelocity(push.setY(vertical));
        }
        discord.send("relic-used", p.getName() + " triggered the Breeze relic.");
        tell(p, "&bThe sky throws everyone back.");
    }
    private void shulkerRelic(Player p) {
        if (!cooled(p, "shulker-relic", getConfig().getInt("settings.shulker-relic-cooldown-seconds", 420))) return;
        double radius = getConfig().getDouble("settings.shulker-relic-radius", 7.0);
        int levitationTicks = getConfig().getInt("settings.shulker-relic-levitation-ticks", 100);
        for (Entity entity : p.getNearbyEntities(radius, radius, radius)) if (entity instanceof LivingEntity living && entity != p) living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levitationTicks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0)); tell(p, "&dThe void bends around you.");
        Block nearest = nearestStorage(p);
        if (nearest != null) p.teleport(nearest.getLocation().add(.5, 1, .5));
        discord.send("relic-used", p.getName() + " opened a Shulker relic.");
    }
    private Block nearestStorage(Player p) {
        Block best = null; double distance = Double.MAX_VALUE;
        for (int x = -32; x <= 32; x++) for (int y = -8; y <= 8; y++) for (int z = -32; z <= 32; z++) {
            Block block = p.getLocation().getBlock().getRelative(x, y, z);
            Material type = block.getType();
            if (type != Material.CHEST && type != Material.BARREL && type != Material.SHULKER_BOX && type != Material.ENDER_CHEST) continue;
            double candidate = block.getLocation().distanceSquared(p.getLocation());
            if (candidate < distance) { distance = candidate; best = block; }
        }
        return best;
    }
    private void guardianRelic(Player p) {
        if (!cooled(p, "guardian-relic", getConfig().getInt("settings.guardian-relic-cooldown-seconds", 1800))) return;
        for (Player target : Bukkit.getOnlinePlayers()) target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, getConfig().getInt("settings.guardian-mining-fatigue-seconds", 720) * 20, 1));
        p.getWorld().setStorm(true); p.getWorld().setWeatherDuration(getConfig().getInt("settings.guardian-relic-duration-seconds", 1800) * 20); tell(p, "&3The tide rises.");
        discord.send("relic-used", p.getName() + " summoned the Guardian tide.");
    }
    @EventHandler public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return; Origin o = origin(p);
        if (o == Origin.SHULKER && p.isSneaking() && !environmental(e) && !shieldDisabled(p) && !hasAxeAttacker(e)) e.setCancelled(true);
        if (o == Origin.BREEZE && e.getCause() == EntityDamageEvent.DamageCause.FALL)
            e.setDamage(e.getDamage() * getConfig().getDouble("settings.breeze-fall-damage-multiplier", .35));
        if (o == Origin.GUARDIAN && (p.getWorld().hasStorm() || p.isInWater()))
            e.setDamage(e.getDamage() * getConfig().getDouble("settings.guardian-water-damage-multiplier", .75));
    }
    private boolean environmental(EntityDamageEvent e) {
        return switch (e.getCause()) {
            case DROWNING, LAVA, FIRE, FIRE_TICK, SUFFOCATION, VOID, HOT_FLOOR, CAMPFIRE, CRAMMING -> true;
            default -> false;
        };
    }
    private boolean shieldDisabled(Player p) {
        return shulkerShieldDisabled.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
    }
    private boolean hasAxeAttacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) return false;
        return by.getDamager() instanceof LivingEntity living && living.getEquipment() != null && living.getEquipment().getItemInMainHand().getType().name().endsWith("_AXE");
    }
    @EventHandler public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || origin(p) == null) return; Origin o = origin(p);
        if (o == Origin.BLAZE && e.getEntity() instanceof LivingEntity target)
            target.setFireTicks(getConfig().getInt("settings.blaze-hit-fire-ticks", 100));
        if (o == Origin.SHULKER && getConfig().getBoolean("settings.enable-shulker-shadow-step", true)
                && Math.random() < getConfig().getDouble("settings.shulker-shadow-step-chance", .10)
                && e.getEntity() instanceof LivingEntity target)
            p.teleport(target.getLocation().subtract(target.getLocation().getDirection().multiply(2)));
        if (o == Origin.GUARDIAN && (p.isInWater() || p.getWorld().hasStorm()))
            e.setDamage(e.getDamage() * getConfig().getDouble("settings.guardian-aquatic-damage-multiplier", 1.35));
        if (o == Origin.BREEZE && e.getEntity() instanceof LivingEntity target)
            Bukkit.getScheduler().runTask(this, () -> target.setVelocity(target.getVelocity().multiply(getConfig().getDouble("settings.breeze-extra-knockback", 1.35))));
        if (e.getEntity() instanceof Player target && origin(target) == Origin.BREEZE)
            Bukkit.getScheduler().runTask(this, () -> target.setVelocity(target.getVelocity().multiply(getConfig().getDouble("settings.breeze-extra-knockback", 1.35))));
        if (e.getEntity() instanceof Player target && origin(target) == Origin.SHULKER && hasAxeAttacker(e))
            shulkerShieldDisabled.put(target.getUniqueId(), System.currentTimeMillis() + getConfig().getInt("settings.shulker-shield-seconds", 30) * 1000L);
    }
    @EventHandler public void projectileDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof SmallFireball fireball && fireball.getShooter() instanceof Player p && origin(p) == Origin.BLAZE) {
            e.setDamage(getConfig().getDouble("settings.fireball-damage", 6.0));
            e.getEntity().setFireTicks(getConfig().getInt("settings.fireball-fire-ticks", 100));
        }
    }
    @EventHandler public void peacefulAquatic(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && origin(p) == Origin.GUARDIAN &&
                (e.getEntity() instanceof WaterMob || e.getEntity() instanceof Guardian)) e.setCancelled(true);
        NamespacedKey wet = new NamespacedKey(this, "wet-mob");
        if (getConfig().getBoolean("settings.enable-guardian-wet-mob-hostility", true)
                && e.getEntity().getPersistentDataContainer().has(wet, PersistentDataType.BYTE)
                && e.getTarget() instanceof Player p && origin(p) != Origin.GUARDIAN) e.setCancelled(false);
    }
    @EventHandler public void guardianTrident(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (origin(p) != Origin.GUARDIAN) return;
        ItemStack stack = p.getInventory().getItem(e.getNewSlot());
        if (stack == null || stack.getType() != Material.TRIDENT) return;
        stack.addUnsafeEnchantment(Enchantment.RIPTIDE, getConfig().getInt("settings.guardian-trident-riptide-level", 2));
        stack.addUnsafeEnchantment(Enchantment.LOYALTY, getConfig().getInt("settings.guardian-trident-loyalty-level", 3));
        stack.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
    }
    @EventHandler public void guardianMilk(PlayerItemConsumeEvent e) {
        if (getConfig().getBoolean("settings.enable-guardian-milk-immunity", true)
                && e.getItem().getType() == Material.MILK_BUCKET && origin(e.getPlayer()) == Origin.GUARDIAN) {
            e.setCancelled(true);
            tell(e.getPlayer(), "&3The guardian's blessing rejects the milk.");
        }
    }
    @EventHandler public void shulkerLevitation(EntityPotionEffectEvent e) {
        if (e.getEntity() instanceof Player p && origin(p) == Origin.SHULKER &&
                e.getNewEffect() != null && e.getNewEffect().getType() == PotionEffectType.LEVITATION)
            e.setCancelled(true);
    }
    @EventHandler public void ignite(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (origin(p) != Origin.BLAZE || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Material type = e.getClickedBlock().getType();
        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE || type == Material.FURNACE ||
                type == Material.BLAST_FURNACE || type == Material.SMOKER) {
            Block block = e.getClickedBlock();
            if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
                org.bukkit.block.data.type.Campfire fire = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                fire.setLit(true);
                block.setBlockData(fire);
            }
            else block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(.5, 1, .5), 10);
        }
    }
    @EventHandler public void closeVault(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !ChatColor.stripColor(e.getView().getTitle()).contains("Private Vault")) return;
        for (int i = 0; i < 27; i++) getConfig().set("vaults." + p.getUniqueId() + "." + i, e.getInventory().getItem(i));
        saveConfig();
    }
    @EventHandler public void launch(PlayerInteractEvent e) {
        if (!e.getPlayer().isSneaking() || e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer(); if (origin(p) == Origin.BLAZE && !disabled(p) && cooled(p, "fireball", getConfig().getInt("settings.fireball-cooldown-seconds", 8))) {
            SmallFireball fireball = (SmallFireball) p.getWorld().spawnEntity(p.getEyeLocation().add(p.getLocation().getDirection()), EntityType.SMALL_FIREBALL);
            fireball.setShooter(p); fireball.setVelocity(p.getLocation().getDirection().multiply(1.2));
        } else if (origin(p) == Origin.SHULKER && !disabled(p) && cooled(p, "shulker-bullet", getConfig().getInt("settings.shulker-bullet-cooldown-seconds", 4))) {
            ShulkerBullet bullet = (ShulkerBullet) p.getWorld().spawnEntity(p.getEyeLocation().add(p.getLocation().getDirection()), EntityType.SHULKER_BULLET);
            bullet.setShooter(p);
        } else if (origin(p) == Origin.GUARDIAN && !disabled(p) && cooled(p, "guardian-beam", getConfig().getInt("settings.guardian-beam-cooldown-seconds", 3))) {
            for (Entity entity : p.getNearbyEntities(18, 8, 18)) if (entity instanceof LivingEntity target && target != p) {             target.damage(getConfig().getDouble("settings.guardian-beam-damage", 6.0), p); p.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 15); break; }
        }
    }
}
