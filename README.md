### Микросервисное приложение «Банк»

Микросервисное приложение «Банк» с использованием Spring Boot (реактивный стек) и паттернов микросервисной архитектуры 
в рамках выполнения задания спринта 9 курса Middle-Java Яндекс.Практикум.
- Spring Boot 3.4.4
- Spring Security
- Spring Cloud Gateway
- Spring Cloud Config Server
- Spring Cloud Netflix Eureka
- Spring Cloud Contract
- R2DBC
- WebFlux
- Thymeleaf
- Lombok

Приложение состоит из следующих микросервисов, реализованных в виде отдельных моделей многомодульного проекта:
- [сервера конфигураций Spring Cloud Config Server](config-server) - паттерн External Configuration
- [Netflix Eureka](eureka-server) - паттерн Service Discovery
- [шлюза и балансировщика нагрузки API Gateway](api-gateway) - паттерны API Gateway, Curcuit Breaker, Load Balancing, Access Token
- [фронта (Front UI)](front-ui)
- [сервиса аккаунтов (Accounts)](accounts-service)
- [сервиса обналичивания денег (Cash)](cash-service)
- [сервиса перевода денег между счетами одного или двух аккаунтов (Transfer)](transfer-service)
- [сервиса конвертации валют (Exchange)](exchange-service)
- [сервиса генерации курсов валют (Exchange Generator)](exchange-generator)
- [сервиса блокировки подозрительных операций (Blocker)](blocker-service)
- [сервиса уведомлений (Notifications)](notifications-service)

Все микросервисы реализующие [REST API](openapi), имеют контракты.

Все микросервисы разворачиваются в собственном контейнере - паттерн Single Service per Host.

Также часть сервисов имеют собственную БД - паттерн Database per Service.

Для OAuth2 используется Keycloak.

Все микросервисы предоставляют ручку /actuator/health для проверки своего состояния (используется для согласованного запуска сервисов в Docker) - паттерн Health Check API.

Сервис нотификации имитирует отправку сообщений на электронную почту, распечатывая их в лог.
Отправка уведомлений в сервис нотификации микросервисами Accounts, Cash и Transfer осуществляется с использованием паттерна Transactional Outbox.

Конфигурации хранятся [локально](config-server/src/main/resources/config-repo) на сервере конфигураций.

#### Сборка приложения

Для сборки всех модулей приложения использовать команду
```shell
./gradlew clean build
```

Для отдельной сборки модулей использовать указанную ниже команду, но сборка в этом случае должна осуществляться согласно 
следованию модулей в [settings.gradle](settings.gradle), чтобы обеспечить наличие в локальном репозитории артефактов *.stubs.jar, 
от которых зависят тесты модуля. 
```shell
./gradlew :<имя_модуля>:clean :<имя_модуля>:build
```

#### Деплой и запуск приложения в Docker

Для запуска приложения в Docker после выполнения сборки выполнить следующую команду в корневом каталоге:
```shell
docker compose up -d --build
```
После запуска контейнеров сайт приложения будет доступен по адресу: `http://localhost:8090/bank-app`
Генерация курсов валют начинается через 2 минуты после старта exchange-generator.

#### Пользователи
При первоначальной загрузке приложения создаются тестовые пользователи
1. `test_user1` c паролем `test_user1_pass`;
2. `test_user2` c паролем `test_user2_pass`;
3. `test_user3` c паролем `test_user3_pass`.

