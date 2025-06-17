package ru.strbnm.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Rate {
    private String title;
    private String name;
    private BigDecimal value;
}
