package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда GET /api/v1/rates вызван, сервис конвертации валют возвращает ответ 200 с содержимым c массивом курсов валют"
    request {
        method 'GET'
        url '/api/v1/rates'
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
                [title: "Юань", name : "CNY", value: 0.11],
                [title: "Российский рубль", name : "RUB", value: 1.0],
                [title: "Американский доллар", name : "USD", value: 0.012]
        ])
    }
}