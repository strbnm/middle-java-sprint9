package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/rates вызван, сервис конвертации валют сохраняет курсы в БД и возвращает ответ 201 с содержимым Success"
    request {
        method 'POST'
        url '/api/v1/rates'
        body([
                timestamp: anyPositiveInt(),
                rates: [
                        [title: "Доллар", name : "USD", value: anyNumber()],
                        [title: "Юань", name : "CNY", value: anyNumber()],
                        [title: "Рубль", name : "RUB", value: 1.0]
                ]
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