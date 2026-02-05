package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailMasker Test")
class EmailMaskerTest {

    private EmailMasker masker;

    @BeforeEach
    void setUp() {
        masker = new EmailMasker();
    }

    @Test
    @DisplayName("WEAK level - show first 3 chars of local part")
    void maskWeakLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.WEAK);

        // then
        assertEquals("joh*****@gmail.com", result);
    }

    @Test
    @DisplayName("NORMAL level - show first 2 chars of local part + first 2 chars of domain")
    void maskNormalLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.NORMAL);

        // then
        assertEquals("jo******@gm***.com", result);
    }

    @Test
    @DisplayName("STRONG level - mask all local part + all domain")
    void maskStrongLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.STRONG);

        // then
        assertEquals("********@*****.com", result);
    }

    @Test
    @DisplayName("Short local part - WEAK")
    void maskShortLocalWeak() {
        // when
        String result = masker.mask("ab@test.com", MaskingLevel.WEAK);

        // then
        assertEquals("ab@test.com", result); // Do not mask if less than 3 chars
    }

    @Test
    @DisplayName("Handle missing @ symbol")
    void maskWithoutAt() {
        // when
        String result = masker.mask("notanemail", MaskingLevel.NORMAL);

        // then
        assertEquals("**********", result);
    }

    @Test
    @DisplayName("Handle @ symbol at start")
    void maskWithAtAtStart() {
        // when
        String result = masker.mask("@gmail.com", MaskingLevel.NORMAL);

        // then
        assertEquals("**********", result);
    }

    @Test
    @DisplayName("Handle domain without dot")
    void maskWithoutDotInDomain() {
        // when
        String result = masker.mask("john@localhost", MaskingLevel.NORMAL);

        // then
        assertEquals("jo**@*********", result);
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
        String result = masker.mask("test@test.com", null);

        // then
        assertEquals("te**@te**.com", result); // NORMAL level behavior
    }

    @Test
    @DisplayName("supports method - only supports EMAIL type")
    void supportsEmailType() {
        // then
        assertTrue(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }

    @Test
    @DisplayName("Complex email - WEAK")
    void maskComplexEmailWeak() {
        // when
        String result = masker.mask("user.name+tag@example.co.uk", MaskingLevel.WEAK);

        // then
        assertTrue(result.startsWith("use"));
        assertTrue(result.contains("@"));
        assertTrue(result.contains("example.co.uk"));
    }

    @Test
    @DisplayName("Complex email - STRONG")
    void maskComplexEmailStrong() {
        // when
        String result = masker.mask("user.name@example.com", MaskingLevel.STRONG);

        // then
        assertTrue(result.contains("@"));
        assertTrue(result.endsWith(".com"));
        assertTrue(result.contains("*"));
    }
}
