package ru.strbnm.front_ui.dto;

import ru.strbnm.front_ui.utils.Currency;

import java.util.Arrays;
import java.util.List;

public class CurrencyDto {
    private final String title;
    private final String name;

    public CurrencyDto(String title, String name) {
        this.title = title;
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public static List<CurrencyDto> getAll() {
        return Arrays.stream(Currency.values())
                .map(c -> new CurrencyDto(c.getTitle(), c.name()))
                .toList();
    }
}
