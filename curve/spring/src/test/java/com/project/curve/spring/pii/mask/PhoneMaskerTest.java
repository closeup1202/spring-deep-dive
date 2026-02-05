package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneMasker Test")
class PhoneMaskerTest {

    private PhoneMasker masker;

    @BeforeEach
    void setUp() {
        masker = new PhoneMasker();
    }

    @Test
    @DisplayName("WEAK level - mask last 4 digits")
    void maskWeakLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.WEAK);

        // then
        assertEquals("010-1234-****", result);
    }

    @Test
    @DisplayName("NORMAL level - mask middle 4 digits")
    void maskNormalLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.NORMAL);

        // then
        assertEquals("010-****-5678", result);
    }

    @Test
    @DisplayName("STRONG level - mask last 8 digits")
    void maskStrongLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.STRONG);

        // then
        assertEquals("010-****-****", result);
    }

    @Test
    @DisplayName("Phone without hyphen - WEAK")
    void maskWithoutHyphenWeak() {
        // when
        String result = masker.mask("01012345678", MaskingLevel.WEAK);

        // then
        assertEquals("0101234****", result);
    }

    @Test
    @DisplayName("Phone without hyphen - NORMAL")
    void maskWithoutHyphenNormal() {
        // when
        String result = masker.mask("01012345678", MaskingLevel.NORMAL);

        // then
        assertEquals("010-****-5678", result);
    }

    @Test
    @DisplayName("Short phone number (less than 4 digits)")
    void maskShortPhone() {
        // when
        String result = masker.mask("123", MaskingLevel.NORMAL);

        // then
        assertEquals("***", result);
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
        String result = masker.mask("010-1234-5678", null);

        // then
        assertEquals("010-****-5678", result); // NORMAL level behavior
    }

    @Test
    @DisplayName("supports method - only supports PHONE type")
    void supportsPhoneType() {
        // then
        assertTrue(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }

    @Test
    @DisplayName("Phone with parenthesis - WEAK")
    void maskWithParenthesisWeak() {
        // when
        String result = masker.mask("(010)1234-5678", MaskingLevel.WEAK);

        // then
        assertTrue(result.contains("****"));
        assertTrue(result.contains("010"));
    }

    @Test
    @DisplayName("Phone with space - WEAK")
    void maskWithSpaceWeak() {
        // when
        String result = masker.mask("010 1234 5678", MaskingLevel.WEAK);

        // then
        assertTrue(result.contains("****"));
    }

}
