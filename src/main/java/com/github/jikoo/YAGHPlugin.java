package com.github.jikoo;

import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.event.entity.EntityDismountEvent;

/**
 * Yet Another Grappling Hook Plugin.
 *
 * @author Jikoo
 */
public class YAGHPlugin extends JavaPlugin implements Listener {

	private final NamespacedKey keyGrapple = new NamespacedKey(this, "grappling_hook");
	private final NamespacedKey keyGlide = new NamespacedKey(this, "grappling_hook_glide");
	private ItemStack grapplingHook;

	@Override
	public void onEnable() {
		grapplingHook = new ItemStack(Material.TIPPED_ARROW);
		ItemMeta itemMeta = grapplingHook.getItemMeta();
		if (itemMeta instanceof PotionMeta) {
			itemMeta.setDisplayName(ChatColor.AQUA + "Grappling Hook");
			setGrappleTag(itemMeta);
			((PotionMeta) itemMeta).setBasePotionData(new PotionData(PotionType.SLOW_FALLING));
		}
		grapplingHook.setItemMeta(itemMeta);

		ShapelessRecipe recipe = new ShapelessRecipe(keyGrapple, grapplingHook);
		recipe.addIngredient(Material.ARROW).addIngredient(Material.LEAD).addIngredient(Material.TRIPWIRE_HOOK);
		getServer().addRecipe(recipe);

		getServer().getPluginManager().registerEvents(this, this);
	}

	private boolean isGrapplingHook(@Nullable ItemStack itemStack) {
		if (itemStack == null || itemStack.getType() != Material.TIPPED_ARROW || !itemStack.hasItemMeta()) {
			return false;
		}
		ItemMeta itemMeta = itemStack.getItemMeta();
		return itemMeta instanceof PotionMeta && hasGrappleTag(itemMeta)
				&& ((PotionMeta) itemMeta).getBasePotionData().getType() == PotionType.SLOW_FALLING;
	}

	private boolean hasGrappleTag(PersistentDataHolder dataHolder) {
		return dataHolder.getPersistentDataContainer().has(keyGrapple, PersistentDataType.BYTE);
	}

	private void setGrappleTag(PersistentDataHolder dataHolder) {
		dataHolder.getPersistentDataContainer().set(keyGrapple, PersistentDataType.BYTE, (byte) 0);
	}

	private void setGrappleGlide(PersistentDataHolder dataHolder, boolean glide) {
		dataHolder.getPersistentDataContainer().set(keyGlide, PersistentDataType.BYTE, (byte) (glide ? 1 : 0));
	}

	private boolean hasGrappleGlide(PersistentDataHolder dataHolder) {
		PersistentDataContainer dataContainer = dataHolder.getPersistentDataContainer();
		Byte stored = dataContainer.get(keyGlide, PersistentDataType.BYTE);
		return stored != null && stored != 0;
	}

	@EventHandler
	public void onPlayerReadyArrow(PlayerReadyArrowEvent event) {
		if (isGrapplingHook(event.getArrow()) && event.getBow().getType() != Material.CROSSBOW) {
			event.setCancelled(true);
			event.getPlayer().updateInventory();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityShootBow(EntityShootBowEvent event) {
		LivingEntity shooter = event.getEntity();
		if (event.getBow() == null || event.getBow().getType() != Material.CROSSBOW || !event.getBow().hasItemMeta()
				|| !shooter.getWorld().equals(event.getProjectile().getWorld())
				|| !(event.getProjectile() instanceof Arrow)) {
			return;
		}

		ItemMeta itemMeta = event.getBow().getItemMeta();
		if (!(itemMeta instanceof CrossbowMeta)) {
			return;
		}

		Arrow arrow = (Arrow) event.getProjectile();
		if (arrow.getBasePotionData().getType() != PotionType.SLOW_FALLING) {
			return;
		}

		boolean foundHook = false;
		CrossbowMeta crossbowMeta = (CrossbowMeta) itemMeta;
		List<ItemStack> chargedProjectiles = new ArrayList<>(crossbowMeta.getChargedProjectiles());
		Iterator<ItemStack> projectileIterator = chargedProjectiles.iterator();
		while (projectileIterator.hasNext()) {
			if (!isGrapplingHook(projectileIterator.next())) {
				continue;
			}
			if (foundHook) {
				// Prevent multishot yielding duplicates
				projectileIterator.remove();
				event.getProjectile().remove();
				crossbowMeta.setChargedProjectiles(chargedProjectiles);
				event.getBow().setItemMeta(crossbowMeta);
				return;
			}
			foundHook = true;
		}

		arrow.setBasePotionData(new PotionData(PotionType.UNCRAFTABLE));
		arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
		arrow.setBounce(false);
		setGrappleTag(arrow);

		LivingEntity mount = event.getProjectile().getWorld().spawn(shooter.getLocation(), Cat.class, entity -> {
			entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1000000, 0, false, false, false));
			entity.setLootTable(LootTables.EMPTY.getLootTable());
			entity.setInvulnerable(true);
			entity.setSilent(true);
			entity.setGliding(true);
			entity.setCollidable(false);
			setGrappleTag(entity);
			setGrappleGlide(entity, true);
		});

		mount.addPassenger(shooter);
		mount.setLeashHolder(arrow);

		// Slow down arrow to prevent leash breakage
		arrow.setVelocity(arrow.getVelocity().normalize());

		// Stop glide to prevent grapple being far too effective
		getServer().getScheduler().runTaskLater(this, () -> {
			if (mount.isDead() || arrow.isDead()) {
				return;
			}
			setGrappleGlide(mount, false);
			mount.setGliding(false);
		}, 10L);

	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDismount(EntityDismountEvent event) {
		Entity mount = event.getDismounted();
		if (!hasGrappleTag(mount)) {
			// Not a mount
			return;
		}

		Entity shooter = event.getEntity();

		// Project entity along grapple path
		Location to = mount.getLocation();
		to.setPitch(shooter.getLocation().getPitch());
		to.setYaw(shooter.getLocation().getYaw());
		shooter.teleport(to);

		Vector velocity = mount.getVelocity();
		velocity.setX(velocity.getX() * 2);
		velocity.setY(velocity.getY() * (velocity.getY() > 0 ? 2 : 0.5));
		velocity.setZ(velocity.getZ() * 2);
		shooter.setVelocity(velocity);

		if (mount.isDead()) {
			// Already removed
			return;
		}

		// Remove mount
		mount.remove();

		if (!(mount instanceof LivingEntity)) {
			return;
		}

		LivingEntity livingMount = (LivingEntity) mount;

		if (!livingMount.isLeashed()) {
			return;
		}

		// Remove arrow
		livingMount.getLeashHolder().remove();

		// Return grapple
		Item item = shooter.getWorld().dropItem(shooter.getLocation(), grapplingHook);
		item.setPickupDelay(0);
		item.setVelocity(shooter.getVelocity());

	}

	@EventHandler
	public void onUnleash(EntityUnleashEvent event) {
		Entity mount = event.getEntity();
		if (mount.isDead() || !hasGrappleTag(mount)) {
			// Not a mount or already removed
			return;
		}

		// Remove mount
		mount.remove();

		if (!(mount instanceof LivingEntity)) {
			return;
		}

		LivingEntity livingMount = (LivingEntity) mount;

		if (!livingMount.isLeashed() || !(livingMount.getLeashHolder() instanceof Arrow)) {
			return;
		}

		Arrow leashHolder = (Arrow) livingMount.getLeashHolder();
		// Remove arrow
		leashHolder.remove();

		if (event.getReason() != EntityUnleashEvent.UnleashReason.DISTANCE && leashHolder.getShooter() instanceof LivingEntity) {
			LivingEntity shooter = (LivingEntity) leashHolder.getShooter();
			// Return grapple
			Item item = shooter.getWorld().dropItem(shooter.getLocation(), grapplingHook);
			item.setPickupDelay(0);
			item.setVelocity(livingMount.getVelocity().multiply(2));
		}
	}

	@EventHandler
	public void onEntityDropItem(EntityDeathEvent event) {
		if (hasGrappleTag(event.getEntity())) {
			event.getDrops().clear();
		}
	}

	@EventHandler
	public void onProjectileHit(EntityDamageByEntityEvent event) {
		if (hasGrappleTag(event.getEntity())) {
			event.setCancelled(true);
			return;
		}

		if (!hasGrappleTag(event.getDamager())) {
			return;
		}

		event.setCancelled(true);

		if (!(event.getEntity() instanceof LivingEntity)) {
			return;
		}

		LivingEntity livingEntity = (LivingEntity) event.getEntity();
		if (livingEntity.isCollidable()) {
			livingEntity.getCollidableExemptions().add(event.getDamager().getUniqueId());
		}
	}

	@EventHandler
	public void onEntityToggleGlide(EntityToggleGlideEvent event) {
		if (hasGrappleTag(event.getEntity()) && event.isGliding() != hasGrappleGlide(event.getEntity())) {
			event.setCancelled(true);
		}
	}

}
