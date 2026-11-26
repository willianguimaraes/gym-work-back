package com.gymwork.api.user;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** E-mails são guardados e comparados sempre em minúsculo e sem espaços nas pontas. */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
