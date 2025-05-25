package ru.strbnm.accounts_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("accounts")
public class Account {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("currency")
    private String currency; // "RUB", "USD", "CNY"

    private BigDecimal balance;
}
