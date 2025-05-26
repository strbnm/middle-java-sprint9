package ru.strbnm.accounts_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("outbox_notifications")
public class OutboxNotification {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    private String email;

    private String message;

    @Builder.Default
    @Column("created_at")
    private Long createdAt = Instant.now().getEpochSecond();;

    @Column("updated_at")
    private Long updatedAt;

    @Builder.Default
    @Column("is_sent")
    private boolean isSent = false;
}
