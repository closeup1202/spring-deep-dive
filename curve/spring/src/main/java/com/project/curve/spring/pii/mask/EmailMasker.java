package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class EmailMasker implements PiiMasker {

    private static final String AT = "@";

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        int atIndex = value.indexOf(AT);
        if (atIndex <= 0) return "*".repeat(value.length());

        String local = value.substring(0, atIndex);
        String domain = value.substring(atIndex + 1);

        return switch (level) {
            case WEAK -> {
                // Show first 3 chars of local: "john.doe@gmail.com" → "joh****@gmail.com"
                String maskedLocal = maskPart(local, 3);
                yield maskedLocal + AT + domain;
            }
            case NORMAL -> {
                // Show first 2 chars of local + first 2 chars of domain: "john.doe@gmail.com" → "jo****@gm***.com"
                String maskedLocal = maskPart(local, 2);
                String maskedDomain = maskDomain(domain);
                yield maskedLocal + AT + maskedDomain;
            }
            case STRONG -> {
                // Mask entire local + entire domain: "john.doe@gmail.com" → "********@*****.com"
                String maskedLocal = "*".repeat(local.length());
                String maskedDomain = maskDomainStrong(domain);
                yield maskedLocal + AT + maskedDomain;
            }
        };
    }

    private String maskPart(String value, int showCount) {
        if (value.length() <= showCount) return value;
        return value.substring(0, showCount) + "*".repeat(value.length() - showCount);
    }

    private String maskDomain(String domain) {
        int dotIndex = domain.lastIndexOf(".");
        if (dotIndex <= 0) return "*".repeat(domain.length());

        String name = domain.substring(0, dotIndex);
        String tld = domain.substring(dotIndex);

        String maskedName = maskPart(name, 2);
        return maskedName + tld;
    }

    private String maskDomainStrong(String domain) {
        int dotIndex = domain.lastIndexOf(".");
        if (dotIndex <= 0) return "*".repeat(domain.length());

        String name = domain.substring(0, dotIndex);
        String tld = domain.substring(dotIndex);

        return "*".repeat(name.length()) + tld;
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.EMAIL;
    }
}
