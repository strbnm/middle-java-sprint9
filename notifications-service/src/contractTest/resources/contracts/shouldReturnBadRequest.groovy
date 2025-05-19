package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/notifications вызван c неверным application, сервис уведомлений возвращает ответ 400"
    request {
        method 'POST'
        url '/api/v1/notifications'
        body([
                email: "test@example.ru",
                message: "Пополнение счёта RUB на сумму 300.00 руб.",
                application: "some-service"
        ])
        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 400
        headers {
            contentType(applicationJson())
        }
        body([
                status_code: 400,
                message: "Unexpected value 'some-service'"
        ])
    }
}