package ru.strbnm.front_ui.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SignupFormDto {
    private String login;
    private String password;
    private String confirm_password;
    private String name;
    private String email;
    private LocalDate birthdate;
}
