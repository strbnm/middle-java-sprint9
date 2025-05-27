INSERT INTO users(login, password, name, email, birthdate, enabled) VALUES
    ('test_user1', '$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa', 'Иванов Иван', 'ivanov@example.ru', '2000-05-21', true),
    ('test_user2', '$2a$12$8iuXDswC26EzrjL0qWNOiuzwZi5/zGuuJY7gaEkoPnaIPZodfm.xi', 'Петров Петр', 'petrov@example.ru', '1990-05-21', true),
    ('test_user3', '$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6', 'Сидоров Степан', 'sidorov@example.ru', '1980-05-21', true);

INSERT INTO roles(role_name, description) VALUES
    ('ROLE_CLIENT', 'Клиент — зарегистрированный пользователь');

INSERT INTO users_roles(user_id, role_id) VALUES
    (1, 1),
    (2, 1),
    (3, 1);

INSERT INTO accounts(user_id, currency, balance) VALUES
    (1, 'RUB', 150000.0),
    (1, 'CNY', 20000.0),
    (2, 'USD', 1000.0),
    (2, 'CNY', 12000.0),
    (3, 'CNY', 5000.0);