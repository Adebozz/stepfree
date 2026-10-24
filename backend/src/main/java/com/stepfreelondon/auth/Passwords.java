package com.stepfreelondon.auth;

import java.nio.charset.StandardCharsets;

import com.stepfreelondon.common.error.RequestValidationException;

final class Passwords {

    /** bcrypt only uses the first 72 bytes; Spring Security rejects longer input rather than truncating it. */
    static final int MAX_BYTES = 72;

    private Passwords() {
    }

    /** {@code @Size} counts characters; non-ASCII characters take several bytes, so check bytes too. */
    static void requireWithinBcryptLimit(String field, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new RequestValidationException(field, "must be at most " + MAX_BYTES + " bytes");
        }
    }
}
