package com.starkgroup.vms.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Single source of truth for how vehicle numbers are cleaned up and compared.
 */
public final class VehicleNumberNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SPACED_HYPHEN = Pattern.compile("\\s*-\\s*");
    private static final Pattern REPEATED_HYPHEN = Pattern.compile("-{2,}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]");

    private VehicleNumberNormalizer() {
    }

    /**
     * Display form: trimmed, upper-cased, runs of whitespace collapsed to one space,
     * spaces around hyphens removed and repeated hyphens collapsed.
     * Example: {@code "  dha  -- metro 12 "} becomes {@code "DHA-METRO 12"}.
     */
    public static String toDisplay(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        value = WHITESPACE.matcher(value).replaceAll(" ");
        value = SPACED_HYPHEN.matcher(value).replaceAll("-");
        value = REPEATED_HYPHEN.matcher(value).replaceAll("-");
        return value;
    }

    /**
     * Comparison key: only letters (incl. combining marks, e.g. Bengali vowel signs) and digits, upper-cased.
     * {@code "DHA-1234"}, {@code "dha 1234"} and {@code "DHA1234"} all map to {@code "DHA1234"}.
     */
    public static String toKey(String raw) {
        if (raw == null) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
