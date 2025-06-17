package ru.strbnm.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ExchangeRateMessage {
    Long timestamp;
    List<Rate> rates;
}
