package ru.strbnm.front_ui.dto;

import lombok.Builder;
import lombok.Value;
import ru.strbnm.front_ui.utils.Currency;

import java.math.BigDecimal;

@Builder
@Value
public class AccountInfoDto {
    Currency currency;
    BigDecimal value;
    boolean exists;
}
