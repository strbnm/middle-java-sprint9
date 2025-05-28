package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда PUT /api/v1/users/test_user1 вызван c ошибками в данных, сервис аккаунтов должен вернуть" +
            " ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'PUT'
        url '/api/v1/users/test_user1'
        body([
                "login": "test_user1",
                "password": "\$2a\$12\$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                "name": "",
                "email": "test@example.ru",
                "birthdate": "2020-01-01"
        ])

        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 422
        headers {
            contentType(applicationJson())
        }
        body([
                "operationStatus": "FAILED",
                "errors": ["Заполните поле Фамилия Имя", "Вам должно быть больше 18 лет"]
        ])
    }
}