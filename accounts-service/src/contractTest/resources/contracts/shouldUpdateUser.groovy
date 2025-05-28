package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда PUT /api/v1/users/test_user1 вызван, сервис аккаунтов должен обновить пользователя" +
            " test_user1 и вернуть 200 с SUCCESS и пустым списком ошибок"
    request {
        method 'PUT'
        url '/api/v1/users/test_user1'
        body([
                "login": "test_user1",
                "password": "\$2a\$12\$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                "name": "Иванов Иван",
                "email": "test@example.ru",
                "birthdate": "1999-01-01",
                "accounts": ["RUB", "CNY"]
        ])

        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                "operationStatus": "SUCCESS",
                "errors": []
        ])
    }
}