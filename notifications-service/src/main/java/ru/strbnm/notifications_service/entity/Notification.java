package ru.strbnm.notifications_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@ToString
@Builder
@Table("notifications")
public class Notification {
  @Id private Long id;
  private String email;
  private String message;
  private String application;
  @Column("is_sent")
  private boolean isSent;
}
