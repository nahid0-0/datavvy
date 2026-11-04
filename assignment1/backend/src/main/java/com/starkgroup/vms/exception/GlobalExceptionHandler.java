package com.starkgroup.vms.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates every error into an RFC 9457 {@link ProblemDetail} body with two extra properties:
 * <ul>
 *     <li>{@code code}   – a stable {@link ErrorCode} clients can branch on</li>
 *     <li>{@code errors} – field name → message, for validation-style failures</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------- domain exceptions

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(DuplicateVehicleNumberException.class)
    public ProblemDetail handleDuplicate(DuplicateVehicleNumberException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Duplicate vehicle number", ex.getMessage(),
                ErrorCode.DUPLICATE_VEHICLE_NUMBER);
        pd.setProperty("errors", Map.of("vehicleNumber", "is already registered to another vehicle"));
        return pd;
    }

    @ExceptionHandler(VersionConflictException.class)
    public ProblemDetail handleVersionConflict(VersionConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(), ErrorCode.VERSION_CONFLICT);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "This vehicle was modified or deleted by someone else. Reload and try again.",
                ErrorCode.VERSION_CONFLICT);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(),
                ErrorCode.VALIDATION_FAILED);
        pd.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data integrity violation",
                "The request conflicts with existing data.", ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = describeType(ex.getRequiredType());
        String message = "must be " + expected;
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' " + message, ErrorCode.VALIDATION_FAILED);
        pd.setProperty("errors", Map.of(ex.getName(), message));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Please try again later.", ErrorCode.INTERNAL_ERROR);
    }

    // ---------------------------------------------------------------- Spring MVC exceptions

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), messageOf(fieldError), (a, b) -> a + "; " + b);
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(err -> errors.merge(err.getObjectName(), messageOf(err), (a, b) -> a + "; " + b));

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", ErrorCode.VALIDATION_FAILED);
        pd.setProperty("errors", errors);
        log.warn("Validation errors: {}", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                // @Valid @RequestBody validated together with constrained path/query params:
                // report the body's field errors by field name, exactly like MethodArgumentNotValid.
                parameterErrors.getFieldErrors().forEach(fe ->
                        errors.merge(fe.getField(), messageOf(fe), (a, b) -> a + "; " + b));
                parameterErrors.getGlobalErrors().forEach(ge ->
                        errors.merge(ge.getObjectName(), messageOf(ge), (a, b) -> a + "; " + b));
                return;
            }
            String name = result.getMethodParameter().getParameterName();
            String key = name != null ? name : "parameter";
            result.getResolvableErrors()
                    .forEach(err -> errors.merge(key, messageOf(err), (a, b) -> a + "; " + b));
        });
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid.", ErrorCode.VALIDATION_FAILED);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(@NonNull MissingServletRequestParameterException ex,
                                                                          @NonNull HttpHeaders headers,
                                                                          @NonNull HttpStatusCode status,
                                                                          @NonNull WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Missing parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing.", ErrorCode.VALIDATION_FAILED);
        pd.setProperty("errors", Map.of(ex.getParameterName(), "is required"));
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail pd;
        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null) {
            // e.g. "type": "SPACESHIP" or "manufactureYear": "abc"
            String field = fieldPath(ife);
            String message = "must be " + describeType(ife.getTargetType());
            pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.",
                    ErrorCode.VALIDATION_FAILED);
            pd.setProperty("errors", Map.of(field, message));
        } else if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            // e.g. "make": {"nested": "object"}
            String field = fieldPath(mie);
            pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.",
                    ErrorCode.VALIDATION_FAILED);
            pd.setProperty("errors", Map.of(field, "has an invalid value type"));
        } else {
            pd = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                    "The request body is missing or is not valid JSON.", ErrorCode.MALFORMED_REQUEST);
        }
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(@NonNull HttpRequestMethodNotSupportedException ex,
                                                                         @NonNull HttpHeaders headers,
                                                                         @NonNull HttpStatusCode status,
                                                                         @NonNull WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint.",
                ErrorCode.METHOD_NOT_ALLOWED);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(@NonNull HttpMediaTypeNotSupportedException ex,
                                                                     @NonNull HttpHeaders headers,
                                                                     @NonNull HttpStatusCode status,
                                                                     @NonNull WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "Content-Type must be application/json.", ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(headers).body(pd);
    }

    /** Adds {@code code} and {@code timestamp} to problem details produced by the base class (404 etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        // The base class builds the ProblemDetail lazily when body is null, so enrich the result.
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            if (pd.getProperties() == null || !pd.getProperties().containsKey("code")) {
                pd.setProperty("code", codeFor(statusCode));
            }
            if (!pd.getProperties().containsKey("timestamp")) {
                pd.setProperty("timestamp", Instant.now());
            }
        }
        return response;
    }

    // ---------------------------------------------------------------- helpers

    private static ProblemDetail problem(HttpStatus status, String title, String detail, ErrorCode code) {
        if (status.is4xxClientError()) {
            // Client errors are expected, but logging them makes "it silently failed" reports debuggable.
            log.warn("Rejected request: {} {} - {}", status.value(), code, detail);
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST.name();
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND.name();
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED.name();
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE.name();
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR.name() : "HTTP_" + status.value();
        };
    }

    private static String messageOf(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }

    private static String fieldPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String describeType(Class<?> type) {
        if (type == null) {
            return "a valid value";
        }
        if (type.isEnum()) {
            return "one of " + Arrays.toString(type.getEnumConstants());
        }
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class)) {
            return "a number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        return "a valid " + type.getSimpleName();
    }
}
