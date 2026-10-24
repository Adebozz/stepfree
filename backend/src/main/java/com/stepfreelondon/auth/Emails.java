package com.stepfreelondon.auth;

import java.util.Locale;

final class Emails {

    private Emails() {
    }

    /** Emails are compared and stored trimmed and lower-cased (Locale.ROOT avoids the Turkish dotless-i problem). */
    static String normalise(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
