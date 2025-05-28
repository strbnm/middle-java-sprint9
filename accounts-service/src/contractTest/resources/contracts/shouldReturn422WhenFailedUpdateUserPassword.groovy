package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда PATCH /api/v1/users/test_user1/password вызван c невалидным хеш пароля, сервис аккаунтов должен вернуть" +
            " ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'PATCH'
        url '/api/v1/users/test_user1/password'
        body([
                "login": "test_user1",
                "newPassword": "R7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
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
                "errors": ["Ошибка при сохранении изменений пароля. Операция отменена"]
        ])
    }
}