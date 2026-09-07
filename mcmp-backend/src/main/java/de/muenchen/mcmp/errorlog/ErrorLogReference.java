package de.muenchen.mcmp.errorlog;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formats {@link ErrorLog} ids into a fixed-width, user-facing reference (e.g. {@code ERR-3F8K2QZ}).
 * <p>
 * A raw auto-increment id would visibly change length as it grows and would let consecutive
 * errors produce obviously consecutive references (e.g. {@code ERR-000123}, {@code ERR-000124}),
 * which looks odd in the UI and reveals roughly how many errors have occurred. Instead, the id is
 * run through a bijective multiplicative hash over the 32-bit integer space before being base-36
 * encoded, so consecutive ids map to unrelated-looking codes while the mapping stays reversible
 * and always produces a fixed-length result.
 */
public final class ErrorLogReference {

    private static final String PREFIX = "ERR-";

    /** Odd 32-bit constant (Knuth's multiplicative hash constant); odd => coprime to 2^32 => bijective mod 2^32. */
    private static final long MULTIPLIER = 2654435761L;
    private static final long MODULUS = 1L << 32;
    private static final long INVERSE = BigInteger.valueOf(MULTIPLIER).modInverse(BigInteger.valueOf(MODULUS)).longValue();

    /** base-36 digits needed to represent any value below {@link #MODULUS} (36^7 > 2^32 > 36^6). */
    private static final int ENCODED_WIDTH = 7;

    /** {@link #format(Long)} always emits exactly {@link #ENCODED_WIDTH} upper-case base-36 digits. */
    private static final Pattern BASE36_UPPER = Pattern.compile("^[0-9A-Z]{" + ENCODED_WIDTH + "}$");

    private ErrorLogReference() {
    }

    /**
     * @param id the {@link ErrorLog} id, or {@code null}
     * @return the formatted reference (e.g. {@code ERR-3F8K2QZ}), or {@code null} if {@code id} is {@code null}
     */
    public static String format(final Long id) {
        if (id == null) {
            return null;
        }
        long scrambled = Math.floorMod(id * MULTIPLIER, MODULUS);
        String base36 = Long.toString(scrambled, 36).toUpperCase(Locale.ROOT);
        return PREFIX + "0".repeat(Math.max(0, ENCODED_WIDTH - base36.length())) + base36;
    }

    /**
     * Reverses {@link #format(Long)}, so a user-reported reference can be looked up.
     *
     * @param reference a reference previously produced by {@link #format(Long)}
     * @return the original {@link ErrorLog} id, or {@code null} if {@code reference} is not a valid reference
     */
    public static Long parse(final String reference) {
        if (reference == null) {
            return null;
        }
        String normalized = reference.toUpperCase(Locale.ROOT);
        if (!normalized.startsWith(PREFIX)) {
            return null;
        }
        String encoded = normalized.substring(PREFIX.length());
        if (!BASE36_UPPER.matcher(encoded).matches()) {
            return null;
        }
        try {
            long scrambled = Long.parseLong(encoded, 36);
            if (scrambled < 0 || scrambled >= MODULUS) {
                return null;
            }
            return Math.floorMod(scrambled * INVERSE, MODULUS);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
