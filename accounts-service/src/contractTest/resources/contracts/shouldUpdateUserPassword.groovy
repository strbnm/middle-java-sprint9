package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда PATCH /api/v1/users/test_user1/password вызван, сервис аккаунтов должен обновить пароль пользователя" +
            " test_user1 и вернуть 200 с SUCCESS и пустым списком ошибок"
    request {
        method 'PATCH'
        url '/api/v1/users/test_user1/password'
        body([
                "login": "test_user1",
                "newPassword": "\$2a\$12\$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
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