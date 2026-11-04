package com.starkgroup.vms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Manufacture year must lie between 1886 (the first production automobile)
 * and next calendar year (manufacturers release next-year models early).
 * The upper bound is dynamic, which is why a plain {@code @Max} cannot be used.
 */
@Documented
@Constraint(validatedBy = ManufactureYearValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidManufactureYear {

    String message() default "must be between {min} and next year";

    int min() default 1886;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
