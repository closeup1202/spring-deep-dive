package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultMasker Test")
class DefaultMaskerTest {

    private DefaultMasker masker;

    @BeforeEach
    void setUp() {
        masker = new DefaultMasker();
    }

    @Test
    @DisplayName("WEAK level masking - show first half")
    void maskWeakLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.WEAK);

        // then
        assertEquals("abc***", result);
    }

    @Test
    @DisplayName("NORMAL level masking - show first 2 chars")
    void maskNormalLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.NORMAL);

        // then
        assertEquals("ab****", result);
    }

    @Test
    @DisplayName("STRONG level masking - mask all")
    void maskStrongLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.STRONG);

        // then
        assertEquals("******", result);
    }

    @Test
    @DisplayName("Handle null value")
    void maskNullValue() {
        // when
        String result = masker.mask(null, MaskingLevel.NORMAL);

        // then
        assertNull(result);
    }

    @Test
    @DisplayName("Handle empty string")
    void maskEmptyString() {
        // when
        String result = masker.mask("", MaskingLevel.NORMAL);

        // then
        assertEquals("", result);
    }

    @Test
    @DisplayName("Handle null level - default to NORMAL")
    void maskWithNullLevel() {
        // when
        String result = masker.mask("abcdef", null);

        // then
        assertEquals("ab****", result); // NORMAL level behavior
    }

    @Test
    @DisplayName("Mask single char - WEAK")
    void maskSingleCharWeak() {
        // when
        String result = masker.mask("a", MaskingLevel.WEAK);

        // then
        assertEquals("a", result); // Show at least 1 char
    }

    @Test
    @DisplayName("Mask single char - NORMAL")
    void maskSingleCharNormal() {
        // when
        String result = masker.mask("a", MaskingLevel.NORMAL);

        // then
        assertEquals("a", result);
    }

    @Test
    @DisplayName("Mask single char - STRONG")
    void maskSingleCharStrong() {
        // when
        String result = masker.mask("a", MaskingLevel.STRONG);

        // then
        assertEquals("*", result);
    }

    @Test
    @DisplayName("Mask long string - WEAK")
    void maskLongStringWeak() {
        // when
        String result = masker.mask("abcdefghijklmnop", MaskingLevel.WEAK);

        // then
        assertEquals("abcdefgh********", result);
    }

    @Test
    @DisplayName("Mask Korean characters")
    void maskKorean() {
        // when
        String weakResult = masker.mask("홍길동", MaskingLevel.WEAK);
        String normalResult = masker.mask("홍길동", MaskingLevel.NORMAL);
        String strongResult = masker.mask("홍길동", MaskingLevel.STRONG);

        // then
        assertEquals("홍**", weakResult); // Math.max(1, 3/2) = 1 -> "홍" + "**"
        assertEquals("홍길*", normalResult); // Math.min(2, 3) = 2 -> "홍길" + "*"
        assertEquals("***", strongResult);
    }

    @Test
    @DisplayName("supports method - only supports CUSTOM type")
    void supportsCustomType() {
        // then
        assertTrue(masker.supports(PiiType.CUSTOM));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.ADDRESS));
    }

    @Test
    @DisplayName("Implement PiiMasker interface")
    void implementsPiiMasker() {
        // then
        assertTrue(masker instanceof PiiMasker);
    }
}
