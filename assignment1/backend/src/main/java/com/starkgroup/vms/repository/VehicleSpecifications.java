package com.starkgroup.vms.repository;

import com.starkgroup.vms.entity.Vehicle;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import com.starkgroup.vms.util.VehicleNumberNormalizer;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class VehicleSpecifications {

    /** '!' rather than '\\', because backslash is itself an escape character in MySQL/TiDB string literals. */
    private static final char LIKE_ESCAPE = '!';

    private VehicleSpecifications() {
    }

    /**
     * Case-insensitive "contains" search across plate, make, model and owner.
     * The plate is also matched on its normalized form so "dha1234" finds "DHA-1234".
     */
    public static Specification<Vehicle> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(search.strip().toLowerCase(Locale.ROOT)) + "%";
        String normalized = VehicleNumberNormalizer.toKey(search);

        return (root, query, cb) -> {
            var plate = cb.like(cb.lower(root.get("vehicleNumber")), pattern, LIKE_ESCAPE);
            var make = cb.like(cb.lower(root.get("make")), pattern, LIKE_ESCAPE);
            var model = cb.like(cb.lower(root.get("model")), pattern, LIKE_ESCAPE);
            var owner = cb.like(cb.lower(root.get("ownerName")), pattern, LIKE_ESCAPE);
            if (normalized.isEmpty()) {
                return cb.or(plate, make, model, owner);
            }
            var normalizedPlate = cb.like(root.get("normalizedNumber"),
                    "%" + escapeLike(normalized) + "%", LIKE_ESCAPE);
            return cb.or(plate, normalizedPlate, make, model, owner);
        };
    }

    public static Specification<Vehicle> hasType(VehicleType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Vehicle> hasStatus(VehicleStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Escapes LIKE wildcards so a user searching for "%" or "_" gets literal matches. */
    static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
