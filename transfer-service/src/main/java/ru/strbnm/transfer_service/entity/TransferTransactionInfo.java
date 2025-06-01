package ru.strbnm.transfer_service.entity;

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
@Table("transfer_transactions")
public class TransferTransactionInfo {
    @Id
    private Long id;
    @Column("from_login")
    private String fromLogin;

    @Column("to_login")
    private String toLogin;

    @Column("from_amount")
    private BigDecimal fromAmount;

    @Column("to_amount")
    private BigDecimal toAmount;

    @Column("from_currency")
    private String fromCurrency;

    @Column("to_currency")
    private String toCurrency;

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
