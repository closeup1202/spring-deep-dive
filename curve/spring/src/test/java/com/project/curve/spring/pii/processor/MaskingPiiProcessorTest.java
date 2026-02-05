package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.mask.DefaultMasker;
import com.project.curve.spring.pii.mask.EmailMasker;
import com.project.curve.spring.pii.mask.NameMasker;
import com.project.curve.spring.pii.mask.PhoneMasker;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaskingPiiProcessorTest {

    private MaskingPiiProcessor processor;

    @BeforeEach
    void setUp() {
        List<com.project.curve.spring.pii.mask.PiiMasker> maskers = List.of(
                new EmailMasker(),
                new PhoneMasker(),
                new NameMasker(),
                new DefaultMasker()
        );
        processor = new MaskingPiiProcessor(maskers);
    }

    @Test
    @DisplayName("Supported strategy should be MASK")
    void supportedStrategy_shouldBeMask() {
        // When
        PiiStrategy strategy = processor.supportedStrategy();

        // Then
        assertThat(strategy).isEqualTo(PiiStrategy.MASK);
    }

    @Test
    @DisplayName("Email should be masked")
    void process_email_shouldMask() {
        // Given
        String email = "test@example.com";
        PiiField piiField = createPiiField(PiiType.EMAIL, MaskingLevel.NORMAL);

        // When
        String masked = processor.process(email, piiField);

        // Then
        assertThat(masked).isNotEqualTo(email);
        assertThat(masked).contains("*");
    }

    @Test
    @DisplayName("Phone number should be masked")
    void process_phone_shouldMask() {
        // Given
        String phone = "010-1234-5678";
        PiiField piiField = createPiiField(PiiType.PHONE, MaskingLevel.NORMAL);

        // When
        String masked = processor.process(phone, piiField);

        // Then
        assertThat(masked).isNotEqualTo(phone);
        assertThat(masked).contains("*");
    }

    @Test
    @DisplayName("Masking null should return null")
    void process_null_shouldReturnNull() {
        // Given
        PiiField piiField = createPiiField(PiiType.EMAIL, MaskingLevel.NORMAL);

        // When
        String masked = processor.process(null, piiField);

        // Then
        assertThat(masked).isNull();
    }

    @Test
    @DisplayName("Masking empty string should return empty string")
    void process_emptyString_shouldReturnEmpty() {
        // Given
        PiiField piiField = createPiiField(PiiType.EMAIL, MaskingLevel.NORMAL);

        // When
        String masked = processor.process("", piiField);

        // Then
        assertThat(masked).isEmpty();
    }

    private PiiField createPiiField(PiiType type, MaskingLevel level) {
        PiiField piiField = mock(PiiField.class);
        when(piiField.type()).thenReturn(type);
        when(piiField.level()).thenReturn(level);
        when(piiField.strategy()).thenReturn(PiiStrategy.MASK);
        when(piiField.encryptKey()).thenReturn("");
        return piiField;
    }
}
