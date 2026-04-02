package nl.hauntedmc.ailex.npc;

import net.citizensnpcs.api.event.SpawnReason;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.entity.FakePlayer;
import nl.hauntedmc.ailex.ai.movement.behaviour.SeekBehaviour;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.ai.movement.Kinematic;
import nl.hauntedmc.ailex.ai.movement.MovementRequest;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.util.math.Geometry;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import org.joml.Vector3d;

import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents a Non-Player Character (NPC) in the game. It extends the Kinematic interface
 * and implements the methods to update the kinematics of the NPC.
 */
public abstract class NPC implements Kinematic {

    protected String name;
    protected String displayTabName;
    protected String displayName;
    protected int id;
    protected UUID uuid;
    protected FakePlayer fakePlayer;
    protected NPCProperties properties;
    protected MovementBehaviour movementBehaviour;
    protected Vector3d position;
    protected float orientation;
    protected Vector3d velocity;
    protected float rotation;
    protected PriorityQueue<Actionable> actionQueue;
    protected Actionable currentAction;
    protected NPCState state;
    protected Location spawnLocation;
    private String skinTexture;
    private String skinSignature;

    public abstract void updateKinematics(MovementRequest movementRequest, float time);
    public abstract String getFriendlyName();

    /**
     * Constructor for the NPC class
     * @param npcData The data of the NPC
     */
    public NPC(NPCData npcData) {

        if (!npcData.isValid()) {
            return;
        }

        // Initialize the NPC identification properties
        id = npcData.getId();
        uuid = UUID.randomUUID();
        name = npcData.getName();
        properties = npcData.getProperties() == null ? NPCProperties.defaultValues() : npcData.getProperties().copy();
        displayName = buildDisplayName(properties.getPrefix(), name);
        displayTabName = buildDisplayTabName(properties.getTabPrefix(), properties.getPrefix(), name);

        // Set default movement behaviour to seek
        movementBehaviour = new SeekBehaviour();

        // Initialize the action queue
        actionQueue = new PriorityQueue<>((a1, a2) -> Integer.compare(a2.getPriority(), a1.getPriority()));

        // Set the original spawn location of the NPC
        spawnLocation = npcData.getSpawnLocation();

        // Initialize the kinematics of the NPC
        initializeKinematics(spawnLocation);

        // Spawn the fake player entity
        fakePlayer = new FakePlayer(displayName);
        fakePlayer.setDamageable(properties.isDamageable());
        fakePlayer.setAlwaysUseNameHologram(properties.isAlwaysUseNameHologram());

        // Set the state of the NPC to idle
        state = NPCState.IDLE;
    }
    
    /**
     * Initialize the entity of the NPC
     */
    public void postInitializeNPC() {
    }

    /**
     * Spawn the NPC in the game
     */
    public void spawn() {
        fakePlayer.spawn(spawnLocation, SpawnReason.PLUGIN);
    }

    /**
     * Initialize the kinematics of the NPC
     * @param location The spawn location of the NPC
     */
    private void initializeKinematics(Location location) {
        position = Geometry.locationToVector3d(location);
        orientation = location.getYaw();
        velocity = new Vector3d(0, 0, 0);
        rotation = 0;
    }

    /**
     * Execute the next action in the action queue
     * If the NPC is idle and the action queue is not empty, the next action will be executed
     * If the action is completed, the next action will be executed
     */
    private void executeNextAction() {
        if (state == NPCState.IDLE) {
            if (!actionQueue.isEmpty()) {
                Actionable nextAction = actionQueue.poll();
                if (nextAction != null) {
                    currentAction = nextAction;
                    state = NPCState.BUSY;
                    CompletableFuture<Void> actionFuture = nextAction.execute(this);

                    actionFuture.whenComplete((result, exception) -> {
                        state = NPCState.IDLE;
                        currentAction = null;

                        if (exception != null) {
                           LoggerUtils.logWarning("Action not completed: " + exception.getMessage());
                        }
                        executeNextAction(); // Check for the next action after completing the current one
                    });
                }
            }
        }
    }

    /**
     * Queue an action for the NPC
     * @param action The action to be queued
     */
    public void queueAction(Actionable action) {
        postInitializeNPC();
        actionQueue.add(action);
        if (state == NPCState.IDLE) {
            executeNextAction();
        }
    }

    /**
     * Remove the NPC from the game by removing the fake player entity
     */
    public void remove() {
        if (fakePlayer != null) {
            actionQueue.clear();
            cancelCurrentAction();
            fakePlayer.remove();
        }
    }

    /**
     * Respawn the NPC by teleporting the fake player to the last known location
     */
    public void respawn() {
        LoggerUtils.sendDebugMessage("Respawning NPC " + getName() + " (id " + getId() + ")");
        fakePlayer.respawn(getSpawnLocation());
    }

    /**
     * Reset the kinematics of the NPC
     */
    public void resetKinematics() {
        position = Geometry.locationToVector3d(fakePlayer.getLocation());
        orientation = fakePlayer.getLocation().getYaw();
        velocity = new Vector3d(0, 0, 0);
        rotation = 0;
    }

    /**
     * Cancel the current action being executed by the NPC.
     */
    public void cancelCurrentAction() {
        if (currentAction != null) {
            currentAction.cancel();
        }
    }

    /**
     * Set the skin of the NPC
     * @param texture The texture of the skin
     * @param signature The signature of the skin
     */
    public void setSkin(String texture, String signature) {
        skinTexture = texture;
        skinSignature = signature;
        applySkin();
    }

    /**
     * Re-apply the skin of the NPC
     */
    private void applySkin() {
        if (fakePlayer != null && skinTexture != null && skinSignature != null) {
            fakePlayer.setSkin(skinTexture, skinSignature);
        }
    }

    /**
     * Clear the action queue of the NPC.
     */
    public void clearActionQueue() {
        actionQueue.clear();
    }


    /**
     * Get the maximum velocity of the NPC
     * The data is read from the configuration file
     * @return
     */
    public double getMaxVelocity() {
        return ConfigHandler.getInstance().getConfig().getDouble("npc.general.maxVelocity", 4.317);
    }

    /**
     * Get the maximum rotation of the NPC
     * The data is read from the configuration file
     * @return The maximum rotation of the NPC
     */
    public float getMaxRotation() {
        return (float) ConfigHandler.getInstance().getConfig().getDouble("npc.general.maxRotation", 20.0);
    }

    /**
     * Get the data of the NPC
     * This method returns the data of the NPC in the form of an NPCData object
     * @return The data of the NPC
     */
    public NPCData getNPCData() {
        return new NPCData(id, name, fakePlayer.getLocation(), getClass().getName(), properties.copy());
    }

    // --------------------------------------------------------------------------
    // Getters and Setters
    // --------------------------------------------------------------------------

    public int getId() {
        return id;
    }

    public Actionable getCurrentAction() {
        return currentAction;
    }

    public Entity getEntity() {
        return fakePlayer;
    }

    public NPC getNPC() {
        return this;
    }

    public String getName() {
        return name;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public UUID getCitizensEntityID() {
        return fakePlayer.getUniqueId();
    }

    public String getSkinTexture() {
        return skinTexture;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public void setMovementBehaviour(MovementBehaviour movementBehaviour) {
        this.movementBehaviour = movementBehaviour;
    }

    public MovementBehaviour getMovementBehaviour() {
        return movementBehaviour;
    }

    public Vector3d getPosition() {
        return new Vector3d(position);
    }

    public float getOrientation() {
        return orientation;
    }

    public Vector3d getVelocity() {
        return new Vector3d(velocity);
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public void setPosition(Vector3d position) {
        this.position = new Vector3d(position);
    }

    public void setOrientation(float orientation) {
        this.orientation = orientation;
    }

    public void setVelocity(Vector3d velocity) {
        this.velocity = new Vector3d(velocity);
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getDisplayTabName() {
        return displayTabName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTabListOrder() {
        return properties.getTabListOrder();
    }

    public boolean isRespawnOnDeath() {
        return properties.isRespawnOnDeath();
    }

    public boolean isChatEnabled() {
        return properties.isChatEnabled();
    }

    public boolean isListedInTab() {
        return properties.isListedInTab();
    }

    public String getSystemPrompt() {
        return properties.getSystemPrompt();
    }

    public String getUserPromptTemplate() {
        return properties.getUserPromptTemplate();
    }

    private static String buildDisplayName(String prefix, String name) {
        return joinParts(prefix, name);
    }

    private static String buildDisplayTabName(String tabPrefix, String prefix, String name) {
        return joinParts(tabPrefix, prefix, name);
    }

    private static String joinParts(String... parts) {
        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (output.length() > 0) {
                    output.append(' ');
                }
                output.append(part);
            }
        }
        return output.toString();
    }

}
