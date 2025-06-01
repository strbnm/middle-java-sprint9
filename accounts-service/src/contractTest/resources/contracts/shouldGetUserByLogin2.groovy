package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда GET /api/v1/users/test_user2 вызван, сервис аккаунтов должен вернуть детальную информацию о пользователе" +
            " test_user1"
    request {
        method 'GET'
        url '/api/v1/users/test_user2'

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
               login: "test_user2",
                password: "\$2a\$12\$8iuXDswC26EzrjL0qWNOiuzwZi5/zGuuJY7gaEkoPnaIPZodfm.xi",
                name: "Петров Петр",
                email: "petrov@example.ru",
                birthdate: "1990-05-21",
                roles: ["ROLE_CLIENT"],
                accounts: [
                        [currency: "RUB", value: 0.0, exists: false],
                        [currency: "CNY", value: 12000.0, exists: true],
                        [currency: "USD", value: 1000.0, exists: true]
                ]
        ])
    }
}