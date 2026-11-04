package com.starkgroup.vms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.Year;

public class ManufactureYearValidator implements ConstraintValidator<ValidManufactureYear, Integer> {

    private final Clock clock;
    private int min;

    public ManufactureYearValidator() {
        this(Clock.systemUTC());
    }

    ManufactureYearValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(ValidManufactureYear annotation) {
        this.min = annotation.min();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull is responsible for presence
        }
        int max = Year.now(clock).getValue() + 1;
        if (value >= min && value <= max) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be between " + min + " and " + max)
                .addConstraintViolation();
        return false;
    }
}
