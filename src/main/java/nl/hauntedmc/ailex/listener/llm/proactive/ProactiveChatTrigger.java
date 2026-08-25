package nl.hauntedmc.ailex.listener.llm.proactive;

/** A vetted reason for the bot to contribute to public chat. */
public record ProactiveChatTrigger(String context, String instruction, String requiredPlayerName) {

    public static ProactiveChatTrigger join(String playerName, String prompt) {
        return new ProactiveChatTrigger(
                "Welkom-moment: " + playerName + " is net op de server gekomen.",
                prompt.replace("{player_name}", playerName),
                playerName
        );
    }

    public static ProactiveChatTrigger question(String message) {
        return new ProactiveChatTrigger(
                message,
                "Dit is een algemene vraag aan de server, niet aan een specifieke speler. Geef alleen een kort, "
                        + "behulpzaam antwoord als je echt waarde toevoegt; reageer niet alsof je bent genoemd.",
                ""
        );
    }

    public static ProactiveChatTrigger collectiveReaction(int minimumPlayers, String message) {
        return new ProactiveChatTrigger(
                message,
                "Minstens " + minimumPlayers + " verschillende spelers delen vlak na elkaar een positieve reactie "
                        + "zoals gg of welkom. Sluit daar met één korte, passende reactie bij aan; wees niet overdreven.",
                ""
        );
    }

    public static ProactiveChatTrigger idleConversation(boolean hasRecentChat) {
        String instruction = hasRecentChat
                ? "Je bent al een tijd stil geweest, maar spelers praten nog. Voeg alleen één korte, relevante "
                + "opmerking toe die duidelijk aansluit bij de recente chatcontext; start geen nieuw onderwerp."
                : "Je bent al een tijd stil geweest en de chat is rustig. Plaats hoogstens één luchtige, nuttige "
                + "Minecraft- of servergerichte gedachte om de chat welkom te houden; vraag geen aandacht en noem "
                + "geen feiten die je niet zeker weet.";
        return new ProactiveChatTrigger("Stille periode in de serverchat.", instruction, "");
    }

    /** Rejects a join reply when it failed to personally address the player. */
    public boolean accepts(String response) {
        if (requiredPlayerName == null || requiredPlayerName.isBlank()) {
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
