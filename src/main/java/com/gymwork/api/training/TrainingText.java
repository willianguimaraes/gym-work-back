package com.gymwork.api.training;

final class TrainingText {

    private TrainingText() {
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
