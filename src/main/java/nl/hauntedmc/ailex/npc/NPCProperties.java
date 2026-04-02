package nl.hauntedmc.ailex.npc;

/**
 * Mutable properties that control per-NPC runtime behavior and presentation.
 */
public class NPCProperties {

    public static final String DEFAULT_PREFIX = "<grey>[Speler]";
    public static final String DEFAULT_TAB_PREFIX = "<green>●";
    public static final int DEFAULT_TAB_LIST_ORDER = -10_000;
    public static final boolean DEFAULT_DAMAGEABLE = true;
    public static final boolean DEFAULT_RESPAWN_ON_DEATH = true;
    public static final boolean DEFAULT_CHAT_ENABLED = true;
    public static final boolean DEFAULT_LISTED_IN_TAB = true;
    public static final boolean DEFAULT_ALWAYS_USE_NAME_HOLOGRAM = false;

    private String prefix;
    private String tabPrefix;
    private int tabListOrder;
    private boolean damageable;
    private boolean respawnOnDeath;
    private boolean chatEnabled;
    private boolean listedInTab;
    private boolean alwaysUseNameHologram;

    public NPCProperties() {
        this(
                DEFAULT_PREFIX,
                DEFAULT_TAB_PREFIX,
                DEFAULT_TAB_LIST_ORDER,
                DEFAULT_DAMAGEABLE,
                DEFAULT_RESPAWN_ON_DEATH,
                DEFAULT_CHAT_ENABLED,
                DEFAULT_LISTED_IN_TAB,
                DEFAULT_ALWAYS_USE_NAME_HOLOGRAM
        );
    }

    /**
     * Constructor for mutable entity properties.
     * @param prefix The display prefix shown before the NPC name in chat/nameplate.
     * @param tabPrefix The prefix shown in tab before prefix/name.
     * @param tabListOrder Specific tab sort order for this NPC.
     * @param damageable Whether this NPC should be damageable by players/world.
     * @param respawnOnDeath Whether the NPC should automatically respawn after death.
     * @param chatEnabled Whether mention-based AI replies are enabled for this NPC.
     * @param listedInTab Whether this NPC should be listed in the tab list.
     * @param alwaysUseNameHologram Whether Citizens should force name holograms.
     */
    public NPCProperties(String prefix, String tabPrefix, int tabListOrder, boolean damageable,
                         boolean respawnOnDeath, boolean chatEnabled, boolean listedInTab,
                         boolean alwaysUseNameHologram) {
        this.prefix = prefix == null ? "" : prefix;
        this.tabPrefix = tabPrefix == null ? "" : tabPrefix;
        this.tabListOrder = tabListOrder;
        this.damageable = damageable;
        this.respawnOnDeath = respawnOnDeath;
        this.chatEnabled = chatEnabled;
        this.listedInTab = listedInTab;
        this.alwaysUseNameHologram = alwaysUseNameHologram;
    }

    public static NPCProperties defaultValues() {
        return new NPCProperties();
    }

    public NPCProperties copy() {
        return new NPCProperties(
                prefix,
                tabPrefix,
                tabListOrder,
                damageable,
                respawnOnDeath,
                chatEnabled,
                listedInTab,
                alwaysUseNameHologram
        );
    }

    public boolean isValid() {
        return prefix != null && tabPrefix != null;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String getTabPrefix() {
        return tabPrefix;
    }

    public void setTabPrefix(String tabPrefix) {
        this.tabPrefix = tabPrefix == null ? "" : tabPrefix;
    }

    public int getTabListOrder() {
        return tabListOrder;
    }

    public void setTabListOrder(int tabListOrder) {
        this.tabListOrder = tabListOrder;
    }

    public boolean isDamageable() {
        return damageable;
    }

    public void setDamageable(boolean damageable) {
        this.damageable = damageable;
    }

    public boolean isRespawnOnDeath() {
        return respawnOnDeath;
    }

    public void setRespawnOnDeath(boolean respawnOnDeath) {
        this.respawnOnDeath = respawnOnDeath;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }

    public boolean isListedInTab() {
        return listedInTab;
    }

    public void setListedInTab(boolean listedInTab) {
        this.listedInTab = listedInTab;
    }

    public boolean isAlwaysUseNameHologram() {
        return alwaysUseNameHologram;
    }

    public void setAlwaysUseNameHologram(boolean alwaysUseNameHologram) {
        this.alwaysUseNameHologram = alwaysUseNameHologram;
    }
}
