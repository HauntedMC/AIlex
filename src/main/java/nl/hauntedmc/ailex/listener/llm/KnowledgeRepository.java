package nl.hauntedmc.ailex.listener.llm;

import org.bukkit.plugin.java.JavaPlugin;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.infrastructure.knowledge.KnowledgeRepository}. */
@Deprecated(forRemoval = true)
final class KnowledgeRepository extends nl.hauntedmc.ailex.assistant.infrastructure.knowledge.KnowledgeRepository {
    KnowledgeRepository(JavaPlugin plugin) {
        super(plugin);
    }
}
