package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/users вызван, сервис аккаунтов должен создать пользователя"
    request {
        method 'POST'
        url '/api/v1/users'
        body([
                "login": "test_user1",
                "password": "\$2a\$12\$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                "name": "Иванов Иван",
                "email": "ivanov@example.ru",
                "birthdate": "2000-01-01"
        ])

        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 201
        headers {
            contentType(applicationJson())
        }
        body([
                "operationStatus": "SUCCESS",
                "errors": []
        ])
    }
}