package ru.strbnm.cash_service.entity;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("cash_transactions")
public class CashTransactionInfo {
    @Id
    private Long id;

    private String login;

    private BigDecimal amount;

    private String currency;
    
    private String action;
    
    @Column("is_blocked")
    private boolean isBlocked;

    @Column("is_success")
    private boolean isSuccess;

    @Builder.Default
    @Column("created_at")
    private Long createdAt = Instant.now().getEpochSecond();

    @Column("updated_at")
    private Long updatedAt;
}
