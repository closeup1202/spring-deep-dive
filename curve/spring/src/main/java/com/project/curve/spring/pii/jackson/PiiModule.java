package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;

/**
 * Jackson module for PII processing.
 * When registered with ObjectMapper, @PiiField annotations are automatically processed.
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new PiiModule(processorRegistry));
 *
 * // Automatic masking during serialization
 * String json = mapper.writeValueAsString(userPayload);
 * // {"email":"j***@gm***.com","phone":"010-****-5678",...}
 * }</pre>
 */
public class PiiModule extends SimpleModule {

    private static final String MODULE_NAME = "PiiModule";

    public PiiModule(PiiProcessorRegistry processorRegistry) {
        super(MODULE_NAME);
        setSerializerModifier(new PiiBeanSerializerModifier(processorRegistry));
    }
}
