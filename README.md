## [REST API](http://localhost:8080/doc)

## Концепция:

- Spring Modulith
    - [Spring Modulith: достигли ли мы зрелости модульности](https://habr.com/ru/post/701984/)
    - [Introducing Spring Modulith](https://spring.io/blog/2022/10/21/introducing-spring-modulith)
    - [Spring Modulith - Reference documentation](https://docs.spring.io/spring-modulith/docs/current-SNAPSHOT/reference/html/)

```
  url: jdbc:postgresql://localhost:5432/jira
  username: jira
  password: JiraRush
```

- Есть 2 общие таблицы, на которых не fk
    - _Reference_ - справочник. Связь делаем по _code_ (по id нельзя, тк id привязано к окружению-конкретной базе)
    - _UserBelong_ - привязка юзеров с типом (owner, lead, ...) к объекту (таска, проект, спринт, ...). FK вручную будем
      проверять

## Аналоги

- https://java-source.net/open-source/issue-trackers

## Тестирование

- https://habr.com/ru/articles/259055/

Список выполненных задач:

  2.Выполнено, упоминания VK и YANDEX удалены.

  3.Выполнено. Для запуска локально раскомментировать часть переменных ответственных за локальный запуск
  закомментировать часть переменных ответственных для запуска в docker-compose,
  в src/main/resources/secret_data.properties.

  4.Выполнено.

  6.Выполнено, com.javarush.jira.bugtracking.attachment.FileUtil#upload был переделан из IO под NIO.

  7.Выполнено.

  8.Выполнено, время выполнения задачи и её тестирования выводится в консоль, 
  методы находятся здесь com.javarush.jira.bugtracking.task.TaskService. Для проверки работы надо заполнить БД данными,
  после чего на фронте, во вкладке дашборд подёргать тестовые задачи от toDo до Done

  9.Выполнено, Dockerfile написан.

  10.Выполнено, Docker-compose написан.

  11.Выполнено, файлы локализации лежат в resources, конфиг
  в com.javarush.jira.common.internal.config.LocalizationKonfig;

