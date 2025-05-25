package ru.strbnm.accounts_service.entity;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("users")
public class User {

  @Id private Long id;

  private String login;

  private String password;

  private String name;

  private String email;

  private LocalDate birthdate;

  @Builder.Default
  private boolean enabled = true;
}
