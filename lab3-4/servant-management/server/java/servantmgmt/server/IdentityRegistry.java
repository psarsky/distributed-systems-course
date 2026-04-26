package servantmgmt.server;

import com.zeroc.Ice.Identity;

import java.util.Set;

public final class IdentityRegistry {
    private final String dedicatedCategory;
    private final String sharedCategory;
    private final Set<String> dedicatedNames;
    private final Set<String> sharedNames;

    IdentityRegistry(
            String dedicatedCategory,
            String sharedCategory,
            Set<String> dedicatedNames,
            Set<String> sharedNames
    ) {
        this.dedicatedCategory = dedicatedCategory;
        this.sharedCategory = sharedCategory;
        this.dedicatedNames = dedicatedNames;
        this.sharedNames = sharedNames;
    }

    String id(Identity identity) {
        return identity.category + "/" + identity.name;
    }

    boolean isUnknown(Identity identity) {
        if (identity == null) {
            return true;
        }
        if (dedicatedCategory.equals(identity.category)) {
            return !dedicatedNames.contains(identity.name);
        }
        if (sharedCategory.equals(identity.category)) {
            return !sharedNames.contains(identity.name);
        }
        return true;
    }
}
