package ru.strbnm.accounts_service.repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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

    @Override
    public Mono<UserDetailResponse> getUserWithRolesByLogin(String login) {
        String query =
                """
                SELECT u.id, u.login, u.password, u. name, u.email, u.birthday, ARRAY_AGG(r.role_name) AS roles
                FROM users u
                LEFT JOIN users_roles ur ON u.id = ur.user_id
                LEFT JOIN roles r ON ur.role_id = r.id
                WHERE u.login = :login
                GROUP BY u.id, u.login, u.password, u. name, u.email, u.birthday
                """;
        return databaseClient
                .sql(query)
                .bind("login", login)
                .map(
                        row -> {
                            String[] rolesArray = row.get("roles", String[].class);
                            return new UserDetailResponse(
                                    row.get("login", String.class),
                                    row.get("password", String.class),
                                    row.get("name", String.class),
                                    row.get("email", String.class),
                                    row.get("birthdate", LocalDate.class),
                                    rolesArray != null ? Arrays.asList(rolesArray) : List.of()
                            );
                        })
                .one();
    }
}
