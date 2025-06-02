package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/convert вызван, сервис конвертации валют рассчитывает по текущему курсу конвертацию и возвращает значение суммы в валюте получателе"
    request {
        method 'POST'
        url '/api/v1/convert'
        body([
            "from": "CNY",
            "to": "CNY",
            "amount": 1000.0
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
                "amount": 1000.0
        ])
    }
}