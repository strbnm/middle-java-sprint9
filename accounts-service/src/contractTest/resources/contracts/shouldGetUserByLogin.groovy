package contracts

import org.springframework.cloud.contract.spec.Contract

import java.time.LocalDate

Contract.make {
    description "Когда GET /api/v1/users/test_user1 вызван, сервис аккаунтов должен вернуть детальную информацию о пользователе" +
            " test_user1"
    request {
        method 'GET'
        url '/api/v1/users/test_user1'

        headers {
            accept('application/json')
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
               login: "test_user1",
                password: "\$2a\$12\$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                name: "Иванов Иван",
                email: "ivanov@example.ru",
                birthdate: "2000-05-21",
                roles: ["ROLE_CLIENT"],
                accounts: [
                        [currency: "RUB", value: 150000.0, exists: true],
                        [currency: "CNY", value: 20000.0, exists: true],
                        [currency: "USD", value: 0.0, exists: false]
                ]
        ])
    }
}