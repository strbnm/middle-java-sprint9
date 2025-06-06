package ru.strbnm.front_ui.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashFormDto {
    private String currency;
    private BigDecimal amount;
    private String action;
}
