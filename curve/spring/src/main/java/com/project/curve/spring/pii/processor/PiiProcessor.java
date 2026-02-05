package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;

/**
 * PII data processing interface.
 * Different implementations handle processing for each strategy.
 */
public interface PiiProcessor {

    /**
     * Processes the PII value.
     *
     * @param value Original value
     * @param piiField PiiField annotation information
     * @return Processed value
     */
    String process(String value, PiiField piiField);

    /**
     * Returns the strategy supported by this processor.
     */
    PiiStrategy supportedStrategy();
}
