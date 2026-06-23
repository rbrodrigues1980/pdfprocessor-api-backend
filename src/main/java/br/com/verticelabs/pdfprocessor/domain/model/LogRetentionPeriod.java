package br.com.verticelabs.pdfprocessor.domain.model;

import java.util.Arrays;

public enum LogRetentionPeriod {
    DAY(1, "Dia"),
    MONTH(30, "Mês"),
    YEAR(365, "Ano");

    private final int days;
    private final String label;

    LogRetentionPeriod(int days, String label) {
        this.days = days;
        this.label = label;
    }

    public int getDays() {
        return days;
    }

    public String getLabel() {
        return label;
    }

    public static LogRetentionPeriod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return MONTH;
        }
        return Arrays.stream(values())
                .filter(period -> period.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(MONTH);
    }
}
