package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.mask.PiiMasker;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MASK strategy processor.
 * Finds the masker matching the PiiType and performs masking.
 */
@Component
public class MaskingPiiProcessor implements PiiProcessor {

    private final Map<PiiType, PiiMasker> maskerMap;
    private final PiiMasker defaultMasker;

    public MaskingPiiProcessor(List<PiiMasker> maskers) {
        this.maskerMap = maskers.stream()
                .collect(Collectors.toMap(
                        this::findSupportedType,
                        Function.identity(),
                        (existing, replacement) -> replacement
                ));
        this.defaultMasker = maskerMap.getOrDefault(PiiType.CUSTOM, new FallbackMasker());
    }

    private PiiType findSupportedType(PiiMasker masker) {
        for (PiiType type : PiiType.values()) {
            if (masker.supports(type)) {
                return type;
            }
        }
        return PiiType.CUSTOM;
    }

    @Override
    public String process(String value, PiiField piiField) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        PiiMasker masker = maskerMap.getOrDefault(piiField.type(), defaultMasker);
        return masker.mask(value, piiField.level());
    }

    @Override
    public PiiStrategy supportedStrategy() {
        return PiiStrategy.MASK;
    }

    private static class FallbackMasker implements PiiMasker {
        @Override
        public String mask(String value, com.project.curve.spring.pii.type.MaskingLevel level) {
            if (value == null) return null;
            return "*".repeat(value.length());
        }

        @Override
        public boolean supports(PiiType type) {
            return true;
        }
    }
}
