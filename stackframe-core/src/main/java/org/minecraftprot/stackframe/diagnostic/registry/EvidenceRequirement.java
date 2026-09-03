package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Stable description of evidence a classifier must supply for this meaning. */
public record EvidenceRequirement(
        String description,
        Set<EvidenceCapability> capabilities) {

    public EvidenceRequirement {
        description = RegistryValidation.text(description, "evidence.description");
        RegistryValidation.noNullElements(capabilities, "evidence.capabilities");
        if (capabilities.isEmpty()) {
            throw new RegistryValidationException(
                    "evidence.capabilities must declare at least one capability");
        }
        capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public boolean supports(EvidenceCapability capability) {
        return capabilities.contains(RegistryValidation.required(capability, "capability"));
    }
}
