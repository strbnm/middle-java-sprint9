package ru.strbnm.front_ui.utils;

public enum Currency {
    RUB("Российский рубль"),
    USD("Доллар США"),
    CNY("Китайский юань");

    private final String title;

    Currency(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}