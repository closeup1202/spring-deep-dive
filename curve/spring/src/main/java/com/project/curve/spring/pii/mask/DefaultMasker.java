package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class DefaultMasker implements PiiMasker {

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        int length = value.length();

        return switch (level) {
            case WEAK -> {
                // Show first half: "abcdef" → "abc***"
                int showCount = Math.max(1, length / 2);
                yield value.substring(0, showCount) + "*".repeat(length - showCount);
            }
            case NORMAL -> {
                // Show first 2 chars: "abcdef" → "ab****"
                int showCount = Math.min(2, length);
                yield value.substring(0, showCount) + "*".repeat(length - showCount);
            }
            case STRONG -> "*".repeat(length); // Mask entire value: "abcdef" → "******"
        };
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.CUSTOM;
    }
}
