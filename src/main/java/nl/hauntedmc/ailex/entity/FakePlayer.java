package nl.hauntedmc.ailex.entity;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.datacomponent.DataComponentType;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;

import nl.hauntedmc.ailex.AIlexPlugin;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents a fake player in the game.
 * It implements the Entity interface and provides methods to interact with the fake player.
 * Wrapper class for the Citizens NPC class
 */
public class FakePlayer implements Entity {

    private static final String AILEX_MANAGED_METADATA_KEY = "ailex.managed";
    private static final String CITIZENS_SHOULD_SAVE_KEY = "should-save";

    private final NPC npc;
    private final NPCRegistry registry;
    private final SkinTrait skinTrait;
    private boolean removed;

    /**
     * Constructor for the FakePlayer class
     * @param name The name of the player
     */
    public FakePlayer(String name) {
        registry = CitizensAPI.getNPCRegistry();
        npc = registry.createNPC(EntityType.PLAYER, name);
        skinTrait = npc.getOrAddTrait(SkinTrait.class);

        // TODO: Currently movement breaks when npc name is longer than 16 characters
        npc.setName(name);

        setProperties();
    }

    /**
     * Method to set the properties of the NPC
     */
    private void setProperties() {
        setDamageable(true);
        npc.setUseMinecraftAI(false);
        npc.setFlyable(false);
        setAlwaysUseNameHologram(false);
        npc.data().setPersistent(CITIZENS_SHOULD_SAVE_KEY, false);
        npc.data().setPersistent(AILEX_MANAGED_METADATA_KEY, true);
    }

    /**
     * Configure whether this fake player can be damaged.
     * @param damageable true when damage should be allowed
     */
    public void setDamageable(boolean damageable) {
        npc.setProtected(!damageable);
    }

    /**
     * Configure Citizens name hologram behavior.
     * @param alwaysUseNameHologram true to always render a name hologram
     */
    public void setAlwaysUseNameHologram(boolean alwaysUseNameHologram) {
        npc.setAlwaysUseNameHologram(alwaysUseNameHologram);
    }

    /**
     * Method to get the location of the NPC
     */
    public void remove() {
        if (removed) {
            return;
        }

        int npcId = npc.getId();

        if (npc.isSpawned()) {
            npc.despawn(DespawnReason.PLUGIN);
        }

        npc.destroy();

        if (registry.getById(npcId) != null) {
            registry.deregister(npc);
        }

        registry.saveToStore();
        removed = true;
    }

    /**
     * Spawn the NPC at the given location
     * Use the citizens API to spawn the NPC
     * @param location The location of the NPC
     * @param spawnReason The reason for spawning the NPC
     */
    public void spawn(Location location, SpawnReason spawnReason) {
        npc.spawn(location, spawnReason);
    }

    /**
     * Method to set the custom name of the NPC
     * @param playerName The name of the player
     */
    public void setSkin(String playerName) {
        if (npc.isSpawned()) {
            skinTrait.setSkinName(playerName);
        }
    }

    /**
     * Method to set the skin of the NPC
     * @param texture The texture of the skin
     * @param signature The signature of the skin
     */
    public void setSkin(String texture, String signature) {
        skinTrait.setSkinPersistent(UUID.randomUUID().toString(), signature, texture);
    }

    /**
     * Respawn the NPC at the spawn location
     * @param spawnLocation The spawn location of the NPC
     */
    public void respawn(Location spawnLocation) {
        final int deathAnimationTicks = 20; // Minecraft death animation duration
        final int delay = 20; // Delay in ticks before respawning the NPC

        Bukkit.getScheduler().scheduleSyncDelayedTask(AIlexPlugin.getPlugin(), () -> {
            if (!npc.isSpawned()) {
                npc.spawn(spawnLocation, SpawnReason.TIMED_RESPAWN);
            }
        }, delay + deathAnimationTicks);

    }

    // ------------------------------------------------------------------------------
    // The following methods are inherited from the Entity interface
    // Do not change the implementation of these methods
    // ------------------------------------------------------------------------------
    @Override
    public boolean isDead() {
        return npc.getEntity().isDead();
    }

    @Override
    public boolean isValid() {
        return npc.getEntity().isValid();
    }

    @Override
    public void sendMessage(@NotNull String s) {
        npc.getEntity().sendMessage(s);
    }

    @Override
    public void sendMessage(@NotNull String... strings) {
        npc.getEntity().sendMessage(strings);
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String s) {
        npc.getEntity().sendMessage(uuid, s);
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String... strings) {
        npc.getEntity().sendMessage(uuid, strings);
    }

    @Override
    public @NotNull Server getServer() {
        return npc.getEntity().getServer();
    }

    @Override
    public @NotNull String getName() {
        return npc.getEntity().getName();
    }

    @Override
    public boolean isPersistent() {
        return npc.getEntity().isPersistent();
    }

    @Override
    public void setPersistent(boolean b) {
        npc.getEntity().setPersistent(b);
    }

    @Override
    public @Nullable Entity getPassenger() {
        return npc.getEntity().getPassenger();
    }

    @Override
    public boolean setPassenger(@NotNull Entity entity) {
        return npc.getEntity().setPassenger(entity);
    }

    @Override
    public @NotNull List<Entity> getPassengers() {
        return npc.getEntity().getPassengers();
    }

    @Override
    public boolean addPassenger(@NotNull Entity entity) {
        return npc.getEntity().addPassenger(entity);
    }

    @Override
    public boolean removePassenger(@NotNull Entity entity) {
        return npc.getEntity().removePassenger(entity);
    }

    @Override
    public boolean isEmpty() {
        return npc.getEntity().isEmpty();
    }

    @Override
    public boolean eject() {
        return npc.getEntity().eject();
    }

    @Override
    public float getFallDistance() {
        return npc.getEntity().getFallDistance();
    }

    @Override
    public void setFallDistance(float v) {
        npc.getEntity().setFallDistance(v);
    }

    @Override
    public void setLastDamageCause(@Nullable EntityDamageEvent entityDamageEvent) {
        npc.getEntity().setLastDamageCause(entityDamageEvent);
    }

    @Override
    public @Nullable EntityDamageEvent getLastDamageCause() {
        return npc.getEntity().getLastDamageCause();
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return npc.getUniqueId();
    }

    @Override
    public int getTicksLived() {
        return npc.getEntity().getTicksLived();
    }

    @Override
    public void setTicksLived(int i) {
        npc.getEntity().setTicksLived(i);
    }

    @Override
    public void playEffect(@NotNull EntityEffect entityEffect) {
        npc.getEntity().playEffect(entityEffect);
    }

    @Override
    public void broadcastHurtAnimation(@NotNull Collection<Player> players) {
        npc.getEntity().broadcastHurtAnimation(players);
    }

    @Override
    public @NotNull EntityType getType() {
        return npc.getEntity().getType();
    }

    @Override
    public @NotNull ItemStack getPickItemStack() {
        return npc.getEntity().getPickItemStack();
    }

    @Override
    public @NotNull Sound getSwimSound() {
        return npc.getEntity().getSwimSound();
    }

    @Override
    public @NotNull Sound getSwimSplashSound() {
        return npc.getEntity().getSwimSplashSound();
    }

    @Override
    public @NotNull Sound getSwimHighSpeedSplashSound() {
        return npc.getEntity().getSwimHighSpeedSplashSound();
    }

    @Override
    public boolean isInsideVehicle() {
        return npc.getEntity().isInsideVehicle();
    }

    @Override
    public boolean leaveVehicle() {
        return npc.getEntity().leaveVehicle();
    }

    @Override
    public @Nullable Entity getVehicle() {
        return npc.getEntity().getVehicle();
    }

    @Override
    public void setCustomNameVisible(boolean b) {
        npc.getEntity().setCustomNameVisible(b);
    }

    @Override
    public boolean isCustomNameVisible() {
        return npc.getEntity().isCustomNameVisible();
    }

    @Override
    public void setVisibleByDefault(boolean b) {
        npc.getEntity().setVisibleByDefault(b);
    }

    @Override
    public boolean isVisibleByDefault() {
        return npc.getEntity().isVisibleByDefault();
    }

    @Override
    public @NotNull Set<Player> getTrackedBy() {
        return npc.getEntity().getTrackedBy();
    }

    @Override
    public boolean isTrackedBy(@NotNull Player player) {
        return npc.getEntity().isTrackedBy(player);
    }

    @Override
    public void setGlowing(boolean b) {
        npc.getEntity().setGlowing(b);
    }

    @Override
    public boolean isGlowing() {
        return npc.getEntity().isGlowing();
    }

    @Override
    public void setInvulnerable(boolean b) {
        npc.getEntity().setInvulnerable(b);
    }

    @Override
    public boolean isInvulnerable() {
        return npc.getEntity().isInvulnerable();
    }

    @Override
    public boolean isSilent() {
        return npc.getEntity().isSilent();
    }

    @Override
    public void setSilent(boolean b) {
        npc.getEntity().setSilent(b);
    }

    @Override
    public boolean hasGravity() {
        return npc.getEntity().hasGravity();
    }

    @Override
    public void setGravity(boolean b) {
        npc.getEntity().setGravity(b);
    }

    @Override
    public int getPortalCooldown() {
        return npc.getEntity().getPortalCooldown();
    }

    @Override
    public void setPortalCooldown(int i) {
        npc.getEntity().setPortalCooldown(i);
    }

    @Override
    public @NotNull Set<String> getScoreboardTags() {
        return npc.getEntity().getScoreboardTags();
    }

    @Override
    public boolean addScoreboardTag(@NotNull String s) {
        return npc.getEntity().addScoreboardTag(s);
    }

    @Override
    public boolean removeScoreboardTag(@NotNull String s) {
        return npc.getEntity().removeScoreboardTag(s);
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        return npc.getEntity().getPistonMoveReaction();
    }

    @Override
    public @NotNull BlockFace getFacing() {
        return npc.getEntity().getFacing();
    }

    @Override
    public @NotNull Pose getPose() {
        return npc.getEntity().getPose();
    }

    @Override
    public boolean isSneaking() {
        return npc.getEntity().isSneaking();
    }

    @Override
    public void setSneaking(boolean b) {
        npc.getEntity().setSneaking(b);
    }

    @Override
    public void setPose(@NotNull Pose pose, boolean b) {
        npc.getEntity().setPose(pose, b);
    }

    @Override
    public boolean hasFixedPose() {
        return npc.getEntity().hasFixedPose();
    }

    @Override
    public @NotNull SpawnCategory getSpawnCategory() {
        return npc.getEntity().getSpawnCategory();
    }

    @Override
    public boolean isInWorld() {
        return npc.getEntity().isInWorld();
    }

    @Override
    public @Nullable String getAsString() {
        return npc.getEntity().getAsString();
    }

    @Override
    public @Nullable EntitySnapshot createSnapshot() {
        return npc.getEntity().createSnapshot();
    }

    @Override
    public @NotNull Entity copy() {
        return npc.getEntity().copy();
    }

    @Override
    public @NotNull Entity copy(@NotNull Location location) {
        return npc.getEntity().copy(location);
    }

    @NotNull
    @Override
    public Spigot spigot() {
        return npc.getEntity().spigot();
    }

    @Override
    public @NotNull Component name() {
        return npc.getEntity().name();
    }

    @Override
    public @NotNull Component teamDisplayName() {
        return npc.getEntity().teamDisplayName();
    }

    @Override
    public @Nullable Location getOrigin() {
        return npc.getEntity().getOrigin();
    }

    @Override
    public boolean fromMobSpawner() {
        return npc.getEntity().fromMobSpawner();
    }

    @NotNull
    @Override
    public CreatureSpawnEvent.SpawnReason getEntitySpawnReason() {
        return npc.getEntity().getEntitySpawnReason();
    }

    @Override
    public boolean isUnderWater() {
        return npc.getEntity().isUnderWater();
    }

    @Override
    public boolean isInRain() {
        return npc.getEntity().isInRain();
    }

    @Override
    public boolean isInBubbleColumn() {
        return npc.getEntity().isInBubbleColumn();
    }

    @Override
    public boolean isInWaterOrRain() {
        return npc.getEntity().isInWaterOrRain();
    }

    @Override
    public boolean isInWaterOrBubbleColumn() {
        return npc.getEntity().isInWaterOrBubbleColumn();
    }

    @Override
    public boolean isInWaterOrRainOrBubbleColumn() {
        return npc.getEntity().isInWaterOrRainOrBubbleColumn();
    }

    @Override
    public boolean isInLava() {
        return npc.getEntity().isInLava();
    }

    @Override
    public boolean isTicking() {
        return npc.getEntity().isTicking();
    }

    @Override
    public @NotNull Set<Player> getTrackedPlayers() {
        return npc.getEntity().getTrackedPlayers();
    }

    @Override
    public boolean spawnAt(@NotNull Location location, @NotNull CreatureSpawnEvent.SpawnReason spawnReason) {
        return npc.getEntity().spawnAt(location, spawnReason);
    }

    @Override
    public boolean isInPowderedSnow() {
        return npc.getEntity().isInPowderedSnow();
    }

    @Override
    public double getX() {
        return npc.getEntity().getX();
    }

    @Override
    public double getY() {
        return npc.getEntity().getY();
    }

    @Override
    public double getZ() {
        return npc.getEntity().getZ();
    }

    @Override
    public float getPitch() {
        return npc.getEntity().getPitch();
    }

    @Override
    public float getYaw() {
        return npc.getEntity().getYaw();
    }

    @Override
    public boolean collidesAt(@NotNull Location location) {
        return npc.getEntity().collidesAt(location);
    }

    @Override
    public boolean wouldCollideUsing(@NotNull BoundingBox boundingBox) {
        return npc.getEntity().wouldCollideUsing(boundingBox);
    }

    @Override
    public @NotNull EntityScheduler getScheduler() {
        return npc.getEntity().getScheduler();
    }

    @Override
    public @NotNull String getScoreboardEntryName() {
        return npc.getEntity().getScoreboardEntryName();
    }

    @Override
    public @NotNull Location getLocation() {
        return npc.getEntity().getLocation();
    }

    @Override
    public @Nullable Location getLocation(@Nullable Location location) {
        return npc.getEntity().getLocation(location);
    }

    @Override
    public void setVelocity(@NotNull Vector vector) {
        npc.getEntity().setVelocity(vector);
    }

    @Override
    public @NotNull Vector getVelocity() {
        return npc.getEntity().getVelocity();
    }

    @Override
    public double getHeight() {
        return npc.getEntity().getHeight();
    }

    @Override
    public double getWidth() {
        return npc.getEntity().getWidth();
    }

    @Override
    public @NotNull BoundingBox getBoundingBox() {
        return npc.getEntity().getBoundingBox();
    }

    @Override
    public boolean isOnGround() {
        return npc.getEntity().isOnGround();
    }

    @Override
    public boolean isInWater() {
        return npc.getEntity().isInWater();
    }

    @Override
    public @NotNull World getWorld() {
        return npc.getEntity().getWorld();
    }

    @Override
    public void setRotation(float v, float v1) {
        npc.getEntity().setRotation(v, v1);
    }

    @Override
    public void lookAt(double x, double y, double z, @NotNull LookAnchor lookAnchor) {
        npc.getEntity().lookAt(x, y, z, lookAnchor);
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull PlayerTeleportEvent.TeleportCause teleportCause, @NotNull TeleportFlag @NotNull ... teleportFlags) {
        return npc.getEntity().teleport(location, teleportCause, teleportFlags);
    }

    @Override
    public boolean teleport(@NotNull Location location) {
        return npc.getEntity().teleport(location);
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull PlayerTeleportEvent.TeleportCause teleportCause) {
        return npc.getEntity().teleport(location, teleportCause);
    }

    @Override
    public boolean teleport(@NotNull Entity entity) {
        return npc.getEntity().teleport(entity);
    }

    @Override
    public boolean teleport(@NotNull Entity entity, @NotNull PlayerTeleportEvent.TeleportCause teleportCause) {
        return npc.getEntity().teleport(entity, teleportCause);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> teleportAsync(@NotNull Location location, @NotNull PlayerTeleportEvent.TeleportCause teleportCause, @NotNull TeleportFlag @NotNull ... teleportFlags) {
        return npc.getEntity().teleportAsync(location, teleportCause, teleportFlags);
    }

    @Override
    public @NotNull List<Entity> getNearbyEntities(double v, double v1, double v2) {
        return npc.getEntity().getNearbyEntities(v, v1, v2);
    }

    @Override
    public int getEntityId() {
        return npc.getEntity().getEntityId();
    }

    @Override
    public int getFireTicks() {
        return npc.getEntity().getFireTicks();
    }

    @Override
    public int getMaxFireTicks() {
        return npc.getEntity().getMaxFireTicks();
    }

    @Override
    public void setFireTicks(int i) {
        npc.getEntity().setFireTicks(i);
    }

    @Override
    public void setVisualFire(boolean b) {
        npc.getEntity().setVisualFire(b);
    }

    @Override
    public void setVisualFire(@NotNull TriState state) {
        npc.getEntity().setVisualFire(state);
    }

    @Override
    public @NotNull TriState getVisualFire() {
        return npc.getEntity().getVisualFire();
    }

    @Override
    public boolean isVisualFire() {
        return npc.getEntity().isVisualFire();
    }

    @Override
    public int getFreezeTicks() {
        return npc.getEntity().getFreezeTicks();
    }

    @Override
    public int getMaxFreezeTicks() {
        return npc.getEntity().getMaxFreezeTicks();
    }

    @Override
    public void setFreezeTicks(int i) {
        npc.getEntity().setFreezeTicks(i);
    }

    @Override
    public boolean isFrozen() {
        return npc.getEntity().isFrozen();
    }

    @Override
    public void setInvisible(boolean b) {
        npc.getEntity().setInvisible(b);
    }

    @Override
    public boolean isInvisible() {
        return npc.getEntity().isInvisible();
    }

    @Override
    public void setNoPhysics(boolean b) {
        npc.getEntity().setNoPhysics(b);
    }

    @Override
    public boolean hasNoPhysics() {
        return npc.getEntity().hasNoPhysics();
    }

    @Override
    public boolean isFreezeTickingLocked() {
        return npc.getEntity().isFreezeTickingLocked();
    }

    @Override
    public void lockFreezeTicks(boolean b) {
        npc.getEntity().lockFreezeTicks(b);
    }

    @Override
    public @Nullable Component customName() {
        return npc.getEntity().customName();
    }

    @Override
    public void customName(@Nullable Component component) {
        npc.getEntity().customName(component);
    }

    @Override
    public @Nullable String getCustomName() {
        return npc.getEntity().getCustomName();
    }

    @Override
    public void setCustomName(@Nullable String s) {
        npc.getEntity().setCustomName(s);
    }

    @Override
    public void setMetadata(@NotNull String s, @NotNull MetadataValue metadataValue) {
        npc.getEntity().setMetadata(s, metadataValue);
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(@NotNull String s) {
        return npc.getEntity().getMetadata(s);
    }

    @Override
    public boolean hasMetadata(@NotNull String s) {
        return npc.getEntity().hasMetadata(s);
    }

    @Override
    public void removeMetadata(@NotNull String s, @NotNull Plugin plugin) {
        npc.getEntity().removeMetadata(s, plugin);
    }

    @Override
    public boolean isPermissionSet(@NotNull String s) {
        return npc.getEntity().isPermissionSet(s);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission permission) {
        return npc.getEntity().isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(@NotNull String s) {
        return npc.getEntity().hasPermission(s);
    }

    @Override
    public boolean hasPermission(@NotNull Permission permission) {
        return npc.getEntity().hasPermission(permission);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String s, boolean b) {
        return npc.getEntity().addAttachment(plugin, s, b);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return npc.getEntity().addAttachment(plugin);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String s, boolean b, int i) {
        return npc.getEntity().addAttachment(plugin, s, b, i);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int i) {
        return npc.getEntity().addAttachment(plugin, i);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment permissionAttachment) {
        npc.getEntity().removeAttachment(permissionAttachment);
    }

    @Override
    public void recalculatePermissions() {
        npc.getEntity().recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return npc.getEntity().getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return npc.getEntity().isOp();
    }

    @Override
    public void setOp(boolean b) {
        npc.getEntity().setOp(b);
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        return npc.getEntity().getPersistentDataContainer();
    }

    @Override
    public <T> @Nullable T getData(@NotNull DataComponentType.Valued<T> valued) {
        return npc.getEntity().getData(valued);
    }

    @Override
    public <T> @NotNull T getDataOrDefault(@NotNull DataComponentType.Valued<? extends T> valued, @NotNull T t) {
        return npc.getEntity().getDataOrDefault(valued, t);
    }

    @Override
    public boolean hasData(@NotNull DataComponentType dataComponentType) {
        return npc.getEntity().hasData(dataComponentType);
    }
}
