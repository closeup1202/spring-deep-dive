package com.project.curve.spring.context.tag;

import com.project.curve.core.context.TagsContextProvider;
import org.slf4j.MDC;

import java.util.*;

/**
 * MDC (Mapped Diagnostic Context) based Tags Context Provider.
 *
 * <p>Reads specific keys from SLF4J's MDC and passes them as event metadata tags.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // Set MDC at request processing start
 * MDC.put("region", "ap-northeast-2");
 * MDC.put("tenant", "company-001");
 *
 * // Automatically included in tags when publishing events
 * eventProducer.publish(payload);
 *
 * // Clear MDC at request processing end
 * MDC.clear();
 * </pre>
 *
 * <h3>Customization</h3>
 * <pre>
 * // To use different keys:
 * {@literal @}Bean
 * public TagsContextProvider tagsContextProvider() {
 *     return MdcTagsContextProvider.withKeys("region", "tenant", "customKey");
 * }
 * </pre>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>MDC is ThreadLocal-based, so propagation is needed for async processing</li>
 *   <li>Keys with null values are automatically excluded (prevents NPE)</li>
 *   <li>Safely handles empty map cases</li>
 * </ul>
 */
public record MdcTagsContextProvider(List<String> tagKeys) implements TagsContextProvider {

    /**
     * List of keys to extract from MDC (default: region, tenant)
     */
    private static final List<String> DEFAULT_TAG_KEYS = List.of("region", "tenant");

    /**
     * Default constructor (uses region, tenant)
     */
    public MdcTagsContextProvider() {
        this(DEFAULT_TAG_KEYS);
    }

    /**
     * Constructor using custom keys
     *
     * @param tagKeys List of keys to extract from MDC
     */
    public MdcTagsContextProvider(List<String> tagKeys) {
        this.tagKeys = List.copyOf(tagKeys);
    }

    @Override
    public Map<String, String> getTags() {
        Map<String, String> tags = new HashMap<>();

        // Prevent NPE through null checks
        for (String key : tagKeys) {
            Optional.ofNullable(MDC.get(key))
                    .ifPresent(value -> tags.put(key, value));
        }

        // Return immutable empty map if empty, otherwise convert to immutable map
        return tags.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(tags);
    }

    /**
     * Creates a Provider using custom tag keys (static factory method)
     *
     * @param tagKeys List of keys to extract from MDC
     * @return Custom MdcTagsContextProvider
     */
    public static MdcTagsContextProvider withKeys(String... tagKeys) {
        return new MdcTagsContextProvider(Arrays.asList(tagKeys));
    }
}
