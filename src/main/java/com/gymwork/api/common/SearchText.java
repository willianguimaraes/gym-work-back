package com.gymwork.api.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normaliza textos para busca: minúsculas, sem acentos e com espaços simples. */
public final class SearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutAccents.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }
}
