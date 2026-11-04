package com.starkgroup.vms.dto;

import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import com.starkgroup.vms.validation.ValidManufactureYear;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a vehicle.
 * All string fields are trimmed (and blank values turned into null) during JSON deserialization.
 */
public record VehicleRequest(

        @Schema(example = "DHA-METRO-GA-12-3456")
        @NotBlank
        @Size(min = 2, max = 32)
        @Pattern(
                regexp = VehicleRequest.VEHICLE_NUMBER_REGEX,
                message = "may contain only letters, digits, spaces and hyphens, must start and end "
                        + "with a letter or digit, and must include at least one digit")
        String vehicleNumber,

        @Schema(example = "Toyota")
        @NotBlank
        @Size(max = 50)
        String make,

        @Schema(example = "Corolla")
        @NotBlank
        @Size(max = 50)
        String model,

        @Schema(example = "CAR")
        @NotNull
        VehicleType type,

        @Schema(example = "2022")
        @NotNull
        @ValidManufactureYear
        Integer manufactureYear,

        @Schema(example = "White")
        @Size(max = 30)
        String color,

        @Schema(example = "Tony Stark")
        @Size(max = 100)
        String ownerName,

        @Schema(description = "Defaults to ACTIVE on create; left unchanged on update when omitted")
        VehicleStatus status,

        @Schema(description = "Optimistic-locking version from the last read. "
                + "When supplied on update, the request is rejected with 409 if the vehicle changed meanwhile.")
        @PositiveOrZero
        Long version
) {
    public static final String VEHICLE_NUMBER_REGEX =
            "^(?=.*\\p{N})[\\p{L}\\p{M}\\p{N}]+(?:[\\s-]+[\\p{L}\\p{M}\\p{N}]+)*$";
}
