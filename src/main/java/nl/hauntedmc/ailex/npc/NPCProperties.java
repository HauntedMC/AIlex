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
    public static final String DEFAULT_SYSTEM_PROMPT = "Je bent een gemiddelde Nederlandse Minecraft speler op een survival server. Antwoord kort, casual en gematigd positief.";
    public static final String DEFAULT_USER_PROMPT_TEMPLATE = "Een online speler genaamd {player_name} zei: \"{chat_message}\". Reageer als {npc_name} in maximaal 1 korte chatregel.";

    private String prefix;
    private String tabPrefix;
    private int tabListOrder;
    private boolean damageable;
    private boolean respawnOnDeath;
    private boolean chatEnabled;
    private boolean listedInTab;
    private boolean alwaysUseNameHologram;
    private String systemPrompt;
    private String userPromptTemplate;

    public NPCProperties() {
        this(
                DEFAULT_PREFIX,
                DEFAULT_TAB_PREFIX,
                DEFAULT_TAB_LIST_ORDER,
                DEFAULT_DAMAGEABLE,
                DEFAULT_RESPAWN_ON_DEATH,
                DEFAULT_CHAT_ENABLED,
                DEFAULT_LISTED_IN_TAB,
                DEFAULT_ALWAYS_USE_NAME_HOLOGRAM,
                DEFAULT_SYSTEM_PROMPT,
                DEFAULT_USER_PROMPT_TEMPLATE
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
        this(prefix, tabPrefix, tabListOrder, damageable, respawnOnDeath, chatEnabled, listedInTab,
                alwaysUseNameHologram, DEFAULT_SYSTEM_PROMPT, DEFAULT_USER_PROMPT_TEMPLATE);
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
     * @param systemPrompt System prompt used for LLM responses for this NPC.
     * @param userPromptTemplate User prompt template with placeholders for this NPC.
     */
    public NPCProperties(String prefix, String tabPrefix, int tabListOrder, boolean damageable,
                         boolean respawnOnDeath, boolean chatEnabled, boolean listedInTab,
                         boolean alwaysUseNameHologram, String systemPrompt, String userPromptTemplate) {
        this.prefix = prefix == null ? "" : prefix;
        this.tabPrefix = tabPrefix == null ? "" : tabPrefix;
        this.tabListOrder = tabListOrder;
        this.damageable = damageable;
        this.respawnOnDeath = respawnOnDeath;
        this.chatEnabled = chatEnabled;
        this.listedInTab = listedInTab;
        this.alwaysUseNameHologram = alwaysUseNameHologram;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPromptTemplate = userPromptTemplate == null ? "" : userPromptTemplate;
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
                alwaysUseNameHologram,
                systemPrompt,
                userPromptTemplate
        );
    }

    public boolean isValid() {
        return prefix != null
                && tabPrefix != null
                && systemPrompt != null
                && userPromptTemplate != null;
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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public String getUserPromptTemplate() {
        return userPromptTemplate;
    }

    public void setUserPromptTemplate(String userPromptTemplate) {
        this.userPromptTemplate = userPromptTemplate == null ? "" : userPromptTemplate;
    }
}
