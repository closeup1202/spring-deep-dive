package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PiiProcessor registry.
 * Finds the processor matching the strategy and delegates processing.
 */
@Component
public class PiiProcessorRegistry {

    private final Map<PiiStrategy, PiiProcessor> processorMap;

    public PiiProcessorRegistry(List<PiiProcessor> processors) {
        this.processorMap = processors.stream()
                .collect(Collectors.toMap(
                        PiiProcessor::supportedStrategy,
                        Function.identity(),
                        (existing, replacement) -> replacement
                ));
    }

    /**
     * Processes the value according to the PiiField annotation.
     *
     * @param value Original value
     * @param piiField PiiField annotation
     * @return Processed value, null if EXCLUDE strategy
     */
    public String process(String value, PiiField piiField) {
        if (piiField.strategy() == PiiStrategy.EXCLUDE) {
            return null; // Field exclusion handled by Jackson
        }

        PiiProcessor processor = processorMap.get(piiField.strategy());
        if (processor == null) {
            // Return original if processor not found (safe fallback)
            return value;
        }

        return processor.process(value, piiField);
    }

    /**
     * Checks if a processor for the specified strategy is registered.
     */
    public boolean hasProcessor(PiiStrategy strategy) {
        return processorMap.containsKey(strategy);
    }
}
