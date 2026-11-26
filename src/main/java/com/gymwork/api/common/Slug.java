package com.gymwork.api.common;

import java.text.Normalizer;
import java.util.Locale;

/** Texto em minúsculas, sem acentos, só com letras, números e hífen (nomes de arquivo). */
public final class Slug {

    private Slug() {
    }

    public static String of(String text) {
        if (text == null) {
            return "gymwork";
        }
        String slug = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "gymwork" : slug;
    }
}
