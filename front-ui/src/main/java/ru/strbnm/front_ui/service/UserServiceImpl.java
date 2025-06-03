package ru.strbnm.front_ui.service;

import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.strbnm.front_ui.client.accounts.api.AccountsServiceApi;
import ru.strbnm.front_ui.client.accounts.domain.UserDetailResponse;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class UserServiceImpl implements ReactiveUserDetailsService {
    private final AccountsServiceApi accountsServiceApi;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return accountsServiceApi.getUser(username)
                .onErrorResume(WebClientResponseException.NotFound.class, ex ->
                        Mono.error(new UsernameNotFoundException("User not found: " + username))
                )
                .map(this::mapToSpringSecurityUser)
                .cast(UserDetails.class);
    }

    private UserDetails mapToSpringSecurityUser(UserDetailResponse user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return User.builder()
                .username(user.getLogin())
                .password(user.getPassword()) // хэш уже из внешнего сервиса
                .authorities(authorities)
                .build();
    }
}
