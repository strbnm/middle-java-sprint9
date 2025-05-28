package contracts

import org.springframework.cloud.contract.spec.Contract

import java.time.LocalDate

Contract.make {
    description "Когда GET /api/v1/users/test_user4 вызван, сервис аккаунтов должен вернуть 404"
    request {
        method 'GET'
        url '/api/v1/users/test_user4'

        headers {
            accept('application/json')
        }
    }
    response {
        status 404
        headers {
            contentType(applicationJson())
        }
        body([
            status_code: 404,
            message: "Пользователь с логином test_user4 не существует"
        ])
    }
}