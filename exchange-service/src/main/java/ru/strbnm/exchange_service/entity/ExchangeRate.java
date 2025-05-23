package ru.strbnm.exchange_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("exchange_rates")
public class ExchangeRate {
    @Id
    private Long id;
    private String title;
    @Column("currency_code")
    private String currencyCode;
    @Column("rate_to_rub")
    private BigDecimal rateToRub;
    @Column("created_at")
    private Long createdAt;
}