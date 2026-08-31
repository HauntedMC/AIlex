package nl.hauntedmc.ailex.assistant.proactive;

/** A vetted reason for the assistant to contribute to chat. */
public record ProactiveChatTrigger(
        String context,
        String instruction,
        String requiredPlayerName,
        CommunityGoal goal,
        boolean privateOnly
) {

    /** Source-compatible constructor for integrations that do not yet provide explicit social-goal metadata. */
    public ProactiveChatTrigger(String context, String instruction, String requiredPlayerName) {
        this(context, instruction, requiredPlayerName, CommunityGoal.SUPPORT_CONVERSATION, false);
    }

    public ProactiveChatTrigger {
        context = context == null ? "" : context.trim();
        instruction = instruction == null ? "" : instruction.trim();
        requiredPlayerName = requiredPlayerName == null ? "" : requiredPlayerName.trim();
        goal = goal == null ? CommunityGoal.SILENCE : goal;
    }

    public static ProactiveChatTrigger join(String playerName, String prompt) {
        return new ProactiveChatTrigger(
                "Welkom-moment: " + playerName + " is net op de server gekomen.",
                prompt.replace("{player_name}", playerName),
                playerName,
                CommunityGoal.WELCOME,
                false
        );
    }

    public static ProactiveChatTrigger newPlayerHelp(String playerName, String message) {
        return new ProactiveChatTrigger(
                message,
                "Deze speler lijkt nieuw of weinig bekend met Haunty en stelt een concrete publieke Minecraft/servervraag. "
                        + "Geef één direct bruikbare volgende stap; maak geen aannames over ervaring en noem de speler alleen "
                        + "wanneer dat natuurlijk is.",
                "",
                CommunityGoal.HELP_NEW_PLAYER,
                false
        );
    }

    public static ProactiveChatTrigger question(String message) {
        return new ProactiveChatTrigger(
                message,
                "Dit is een algemene vraag aan de server, niet aan een specifieke speler. Geef alleen een kort, "
                        + "behulpzaam antwoord als je echt waarde toevoegt; reageer niet alsof je bent genoemd.",
                "",
                CommunityGoal.INFORM,
                false
        );
    }

    public static ProactiveChatTrigger collectiveReaction(int minimumPlayers, String message) {
        return new ProactiveChatTrigger(
                message,
                "Minstens " + minimumPlayers + " verschillende spelers delen vlak na elkaar een positieve reactie. "
                        + "Sluit daar met één korte, passende reactie bij aan; wees niet overdreven en claim geen prestatie "
                        + "die niet in de aanleiding staat.",
                "",
                CommunityGoal.CELEBRATE,
                false
        );
    }

    public static ProactiveChatTrigger celebrate(String playerName, String eventSummary) {
        return new ProactiveChatTrigger(
                eventSummary,
                "Dit is een vertrouwd, positief spelersmoment. Feliciteer " + playerName
                        + " kort en specifiek met alleen wat in de aanleiding is bevestigd.",
                playerName,
                CommunityGoal.CELEBRATE,
                false
        );
    }

    public static ProactiveChatTrigger connect(String message) {
        return new ProactiveChatTrigger(
                message,
                "De speler zoekt publiek medespelers voor een concrete Minecraft/serveractiviteit. Help de oproep kort "
                        + "zichtbaar te maken zonder spelers te matchen op privé- of afgeleide profielen.",
                "",
                CommunityGoal.CONNECT,
                false
        );
    }

    public static ProactiveChatTrigger defuse(String message) {
        return new ProactiveChatTrigger(
                message,
                "Er is lichte publieke frictie. Reageer alleen als één neutrale, korte zin de toon kan kalmeren. Kies geen "
                        + "partij, dreig niet met moderatie en behandel geen reports/sancties; bij echte moderatie is stilte/staff beter.",
                "",
                CommunityGoal.DEFUSE,
                false
        );
    }

    public static ProactiveChatTrigger followUp(String playerName, String rememberedCommitment) {
        return new ProactiveChatTrigger(
                rememberedCommitment,
                "Vraag " + playerName + " in één korte zin of er voortgang is met dit eerder expliciet genoemde doel/project. "
                        + "Presenteer het als optionele follow-up, niet als controle. Noem geen interne geheugenvelden.",
                playerName,
                CommunityGoal.FOLLOW_UP,
                true
        );
    }

    public static ProactiveChatTrigger idleConversation(boolean hasRecentChat) {
        String instruction = hasRecentChat
                ? "Je bent al een tijd stil geweest, maar spelers praten nog. Voeg alleen één korte, relevante "
                + "opmerking toe die duidelijk aansluit bij de recente chatcontext; start geen nieuw onderwerp."
                : "Je bent al een tijd stil geweest en de chat is rustig. Plaats hoogstens één luchtige, nuttige "
                + "Minecraft- of servergerichte gedachte om de chat welkom te houden; vraag geen aandacht en noem "
                + "geen feiten die je niet zeker weet.";
        return new ProactiveChatTrigger(
                "Stille periode in de serverchat.", instruction, "",
                hasRecentChat ? CommunityGoal.SUPPORT_CONVERSATION : CommunityGoal.INFORM,
                false
        );
    }

    public boolean accepts(String response) {
        if (requiredPlayerName.isBlank()) {
            return true;
        }
        if (response == null || response.isBlank()) {
            return false;
        }
        String name = requiredPlayerName.toLowerCase(java.util.Locale.ROOT);
        String message = response.toLowerCase(java.util.Locale.ROOT);
        int index = message.indexOf(name);
        while (index >= 0) {
            int before = index - 1;
            int after = index + name.length();
            boolean validBefore = before < 0 || !isNameCharacter(message.charAt(before));
            boolean validAfter = after >= message.length() || !isNameCharacter(message.charAt(after));
            if (validBefore && validAfter) {
                return true;
            }
            index = message.indexOf(name, index + name.length());
        }
        return false;
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
