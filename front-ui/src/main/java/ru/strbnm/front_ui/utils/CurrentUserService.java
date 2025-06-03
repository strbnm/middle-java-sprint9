package ru.strbnm.front_ui.utils;

import reactor.core.publisher.Mono;

public interface CurrentUserService {
    Mono<String> getCurrentUserLogin();
}
