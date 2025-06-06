package ru.strbnm.front_ui.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EditUserFormDto {
    private String name;
    private String email;
    private LocalDate birthdate;
    private List<String> accounts;
}
