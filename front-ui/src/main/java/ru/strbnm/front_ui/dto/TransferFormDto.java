package ru.strbnm.front_ui.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferFormDto {
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private String toLogin;
}