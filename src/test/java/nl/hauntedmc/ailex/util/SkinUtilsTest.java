package nl.hauntedmc.ailex.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkinUtilsTest {

    @Test
    void shouldExposeStaticTextureAndSignatureValues() {
        assertNotNull(SkinUtils.AIlex_textureValue);
        assertNotNull(SkinUtils.AIlex_signatureValue);
        assertFalse(SkinUtils.AIlex_textureValue.isBlank());
        assertFalse(SkinUtils.AIlex_signatureValue.isBlank());
    }
}
