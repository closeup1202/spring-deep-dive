package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameMasker Test")
class NameMaskerTest {

    private NameMasker masker;

    @BeforeEach
    void setUp() {
        masker = new NameMasker();
    }

    @Test
    @DisplayName("WEAK level - show only first char")
    void maskWeakLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.WEAK);

        // then
        assertEquals("홍**", result);
    }

    @Test
    @DisplayName("NORMAL level - show first and last char")
    void maskNormalLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.NORMAL);

        // then
        assertEquals("홍*동", result);
    }

    @Test
    @DisplayName("STRONG level - mask all")
    void maskStrongLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.STRONG);

        // then
        assertEquals("***", result);
    }

    @Test
    @DisplayName("Single char name - WEAK")
    void maskSingleCharWeak() {
        // when
        String result = masker.mask("김", MaskingLevel.WEAK);

        // then
        assertEquals("*", result);
    }

    @Test
    @DisplayName("Two chars name - NORMAL")
    void maskTwoCharsNormal() {
        // when
        String result = masker.mask("홍길", MaskingLevel.NORMAL);

        // then
        assertEquals("홍*", result);
    }

    @Test
    @DisplayName("Long name - NORMAL")
    void maskLongNameNormal() {
        // when
        String result = masker.mask("홍길동길동", MaskingLevel.NORMAL);

        // then
        assertEquals("홍***동", result);
    }

    @Test
    @DisplayName("English name - WEAK")
    void maskEnglishNameWeak() {
        // when
        String result = masker.mask("John", MaskingLevel.WEAK);

        // then
        assertEquals("J***", result);
    }

    @Test
    @DisplayName("English name - NORMAL")
    void maskEnglishNameNormal() {
        // when
        String result = masker.mask("John", MaskingLevel.NORMAL);

        // then
        assertEquals("J**n", result);
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
    @DisplayName("Handle null level")
    void maskWithNullLevel() {
        // when
        String result = masker.mask("홍길동", null);

        // then
        assertEquals("홍*동", result); // NORMAL level behavior
    }

    @Test
    @DisplayName("supports method - only supports NAME type")
    void supportsNameType() {
        // then
        assertTrue(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }
}
