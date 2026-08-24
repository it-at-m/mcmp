package de.muenchen.mcmp.errorlog;

import java.math.BigInteger;

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
        long scrambled = (id * MULTIPLIER) % MODULUS;
        String base36 = Long.toString(scrambled, 36).toUpperCase();
        return PREFIX + "0".repeat(Math.max(0, ENCODED_WIDTH - base36.length())) + base36;
    }

    /**
     * Reverses {@link #format(Long)}, so a user-reported reference can be looked up.
     *
     * @param reference a reference previously produced by {@link #format(Long)}
     * @return the original {@link ErrorLog} id, or {@code null} if {@code reference} is not a valid reference
     */
    public static Long parse(final String reference) {
        if (reference == null || !reference.toUpperCase().startsWith(PREFIX)) {
            return null;
        }
        try {
            long scrambled = Long.parseLong(reference.substring(PREFIX.length()), 36);
            return (scrambled * INVERSE) % MODULUS;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
