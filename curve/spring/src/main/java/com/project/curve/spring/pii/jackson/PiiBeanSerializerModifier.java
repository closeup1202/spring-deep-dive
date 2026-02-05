package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Modifier that detects fields with @PiiField annotation during bean serialization
 * and replaces them with PiiPropertyWriter.
 */
@RequiredArgsConstructor
public class PiiBeanSerializerModifier extends BeanSerializerModifier {

    private final PiiProcessorRegistry processorRegistry;

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties) {

        List<BeanPropertyWriter> result = new ArrayList<>();

        for (BeanPropertyWriter writer : beanProperties) {
            PiiField piiField = findPiiFieldAnnotation(beanDesc.getBeanClass(), writer.getName());

            if (piiField != null) {
                result.add(new PiiPropertyWriter(writer, piiField, processorRegistry));
            } else {
                result.add(writer);
            }
        }

        return result;
    }

    private PiiField findPiiFieldAnnotation(Class<?> clazz, String fieldName) {
        // Search field in current class and parent classes
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                PiiField annotation = field.getAnnotation(PiiField.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchFieldException e) {
                // Search parent class if not found in current class
            }
            currentClass = currentClass.getSuperclass();
        }

        // Also search in record components
        assert clazz != null;
        if (clazz.isRecord()) {
            try {
                for (var component : clazz.getRecordComponents()) {
                    if (component.getName().equals(fieldName)) {
                        return component.getAnnotation(PiiField.class);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return null;
    }
}
