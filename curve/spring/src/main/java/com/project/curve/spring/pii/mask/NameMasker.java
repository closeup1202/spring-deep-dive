package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class NameMasker implements PiiMasker {

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        int length = value.length();

        return switch (level) {
            case WEAK -> {
                // Show first char only: "John Doe" → "J*******"
                if (length == 1) yield "*";
                yield value.charAt(0) + "*".repeat(length - 1);
            }
            case NORMAL -> {
                // Show first and last char: "John Doe" → "J******e"
                if (length <= 2) yield value.charAt(0) + "*";
                yield value.charAt(0) + "*".repeat(length - 2) + value.charAt(length - 1);
            }
            case STRONG -> "*".repeat(length); // Mask entire name: "John Doe" → "********"
        };
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.NAME;
    }
}
