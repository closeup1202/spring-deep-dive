package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;
import com.project.curve.spring.pii.strategy.PiiStrategy;

/**
 * Custom PropertyWriter for processing PII fields.
 * Processes and serializes values of fields annotated with @PiiField.
 */
public class PiiPropertyWriter extends BeanPropertyWriter {

    private final BeanPropertyWriter delegate;
    private final PiiField piiField;
    private final PiiProcessorRegistry processorRegistry;

    public PiiPropertyWriter(BeanPropertyWriter delegate, PiiField piiField, PiiProcessorRegistry processorRegistry) {
        super(delegate);
        this.delegate = delegate;
        this.piiField = piiField;
        this.processorRegistry = processorRegistry;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        // Completely exclude field if EXCLUDE strategy
        if (piiField.strategy() == PiiStrategy.EXCLUDE) {
            return;
        }

        Object value = delegate.get(bean);

        if (value == null) {
            if (_nullSerializer != null) {
                gen.writeFieldName(_name);
                _nullSerializer.serialize(null, gen, prov);
            } else if (!_suppressNulls) {
                gen.writeFieldName(_name);
                prov.defaultSerializeNull(gen);
            }
            return;
        }

        // Process PII only for string values
        if (value instanceof String stringValue) {
            String processedValue = processorRegistry.process(stringValue, piiField);
            gen.writeFieldName(_name);
            gen.writeString(processedValue);
        } else {
            // Serialize non-string values as-is
            delegate.serializeAsField(bean, gen, prov);
        }
    }
}
