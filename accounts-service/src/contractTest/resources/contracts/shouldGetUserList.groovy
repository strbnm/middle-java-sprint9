package contracts

import org.springframework.cloud.contract.spec.Contract

import java.time.LocalDate

Contract.make {
    description "Когда GET /api/v1/users вызван, сервис аккаунтов должен вернуть список пользователей"
    request {
        method 'GET'
        url '/api/v1/users'

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
            [
                [login: "test_user1", name: "Иванов Иван"],
                [login: "test_user2", name: "Петров Петр"]
            ]
        ])
    }
}