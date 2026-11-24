package com.hyperplane.rodo.model.enums;

import java.time.DayOfWeek;

public enum DayOfWeekEnum {
    SEGUNDA(DayOfWeek.MONDAY),
    TERCA(DayOfWeek.TUESDAY),
    QUARTA(DayOfWeek.WEDNESDAY),
    QUINTA(DayOfWeek.THURSDAY),
    SEXTA(DayOfWeek.FRIDAY),
    SABADO(DayOfWeek.SATURDAY),
    DOMINGO(DayOfWeek.SUNDAY);

    private final DayOfWeek javaDayOfWeek;

    DayOfWeekEnum(DayOfWeek javaDayOfWeek) {
        this.javaDayOfWeek = javaDayOfWeek;
    }

    public DayOfWeek toJavaDayOfWeek() {
        return javaDayOfWeek;
    }

    public static DayOfWeekEnum fromJavaDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> SEGUNDA;
            case TUESDAY -> TERCA;
            case WEDNESDAY -> QUARTA;
            case THURSDAY -> QUINTA;
            case FRIDAY -> SEXTA;
            case SATURDAY -> SABADO;
            case SUNDAY -> DOMINGO;
        };
    }
}
