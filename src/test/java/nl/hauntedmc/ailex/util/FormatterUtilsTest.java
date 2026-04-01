package nl.hauntedmc.ailex.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FormatterUtilsTest {

    @Test
    void shouldExposeConfiguredMiniMessageSerializer() {
        MiniMessage serializer = FormatterUtils.serializer;
        assertNotNull(serializer);
        Component parsed = serializer.deserialize("<green>Hello</green>");
        assertNotNull(parsed);
    }

    @Test
    void shouldExposeNonEmptyDebugPrefix() {
        assertNotNull(FormatterUtils.DEBUG_PREFIX);
        assertFalse(FormatterUtils.DEBUG_PREFIX.isBlank());
    }
}
