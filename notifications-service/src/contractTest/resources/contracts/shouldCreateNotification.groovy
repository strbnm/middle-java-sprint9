package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/notifications вызван, сервис уведомлений возвращает ответ 201 после сохранения сообщения"
    request {
        method 'POST'
        url '/api/v1/notifications'
        body([
                email: "test@example.ru",
                message: "Пополнение счёта RUB на сумму 300.00 руб.",
                application: "cash-service"
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
        body("Success")
    }
}