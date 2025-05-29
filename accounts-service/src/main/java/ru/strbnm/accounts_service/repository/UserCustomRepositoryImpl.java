package ru.strbnm.accounts_service.repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import lombok.NonNull;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.UserDetailResponse;

@Repository
public class UserCustomRepositoryImpl implements UserCustomRepository {
    private final DatabaseClient databaseClient;

    public UserCustomRepositoryImpl(@NonNull R2dbcEntityTemplate template) {
        this.databaseClient = template.getDatabaseClient();
    }

    private record UserWithRoleRow(
            String login,
            String password,
            String name,
            String email,
            LocalDate birthdate,
            String role
    ) {}

    @Override
    public Mono<UserDetailResponse> getUserWithRolesByLogin(String login) {
        String query =
                """
                SELECT u.id, u.login, u.password, u. name, u.email, u.birthdate, r.role_name
                FROM users u
                LEFT JOIN users_roles ur ON u.id = ur.user_id
                LEFT JOIN roles r ON ur.role_id = r.id
                WHERE u.login = :login
                """;
        return databaseClient
                .sql(query)
                .bind("login", login)
                .map((row, metadata) -> new UserWithRoleRow(
                        row.get("login", String.class),
                        row.get("password", String.class),
                        row.get("name", String.class),
                        row.get("email", String.class),
                        row.get("birthdate", LocalDate.class),
                        row.get("role_name", String.class)
                ))
                .all()
                .collectList()
                .filter(list -> !list.isEmpty())
                .map(rows -> {
                    UserWithRoleRow first = rows.getFirst();
                    List<String> roles = rows.stream()
                            .map(UserWithRoleRow::role)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    return new UserDetailResponse(
                            first.login(),
                            first.password(),
                            first.name(),
                            first.email(),
                            first.birthdate(),
                            roles
                    );
                });
    }
}
