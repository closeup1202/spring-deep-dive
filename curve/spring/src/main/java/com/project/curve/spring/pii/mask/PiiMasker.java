package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;

public interface PiiMasker {
    String mask(String value, MaskingLevel strength);
    boolean supports(PiiType type);
}
