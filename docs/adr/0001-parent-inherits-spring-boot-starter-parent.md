# 0001. hephaestus-parent наследуется от spring-boot-starter-parent

- **Статус:** принято
- **Дата:** 2026-11-05

## Контекст

Все подпроекты используют Spring Boot и наследуют общий `hephaestus-parent`. Нужно получить согласованные
версии зависимостей и плагинов (compiler, surefire, failsafe, jacoco, enforcer, spring-boot/buildpacks).

## Рассмотренные варианты

1. `hephaestus-parent` с `<parent>spring-boot-starter-parent</parent>`.
2. Собственный parent без родителя + `import` BOM `spring-boot-dependencies` и ручной `pluginManagement`.

## Решение

Вариант 1. Starter-parent уже задаёт `maven.compiler.release` из `java.version`, версии и настройки
плагинов, resource filtering, конфигурацию `spring-boot:build-image`. Вариант 2 дублировал бы это вручную.
Testcontainers, JUnit, AssertJ, Mockito управляются BOM Boot — отдельный Testcontainers BOM не нужен.

## Последствия

- Плюсы: минимальный корневой POM; обновление Boot = одна строка.
- Минусы: единственный parent-слот занят Boot; если подпроекту понадобится не-Spring стек (Quarkus), он
  будет наследовать собственный parent, а не `hephaestus-parent`.
