package ru.strbnm.front_ui.dto;

import lombok.Data;

@Data
public class UpdateUserPasswordFormDto {
  private String password;
  private String confirm_password;
}
