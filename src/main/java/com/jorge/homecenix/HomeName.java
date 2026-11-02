package com.jorge.homecenix;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates home names, which are also used as Firestore document ids. */
final class HomeName {

    private static final Pattern VALID = Pattern.compile("[a-zA-Z0-9_-]{1,32}");

    private HomeName() {
    }

    static Optional<String> normalize(String raw) {
        if (raw == null || !VALID.matcher(raw).matches()) {
            return Optional.empty();
        }
        return Optional.of(raw.toLowerCase(Locale.ROOT));
    }
}
