package ru.strbnm.exchange_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("exchange_rates")
public class ExchangeRate {
    @Id
    private Long id;
    private String currencyCode;
    private Double rateToRub; // RUB is base
    private Long timestamp;
}