package ru.strbnm.accounts_service.dto;

import ru.strbnm.accounts_service.entity.Account;

import java.util.List;

public record AccountCheckResult(Account account, List<String> errors) {}