# Explore With Me (EWM)

Проект афиша. Здесь можно предложить какое-либо событие от выставки до похода в кино и собрать компанию для участия в нём.

## Архитектура

Проект реализован на основе микросервисной архитектуры с использованием Spring Cloud.

### Микросервисы:

- **Event Service** — управление событиями
- **User Service** — управление пользователями
- **Category Service** — управление категориями событий
- **Request Service** — управление заявками на участие
- **Stats Server** — сбор и анализ статистики обращений

### Инфраструктурные сервисы:

- **Gateway Server** (Spring Cloud Gateway) — единая точка входа в приложение
- **Discovery Server** (Netflix Eureka) — регистрация и обнаружение сервисов
- **Config Server** (Spring Cloud Config) — централизованная конфигурация

### Особенности:

- Каждый сервис — **отдельное Spring Boot приложение**
- Все сервисы используют **общую базу данных** (PostgreSQL)
- Stats Server использует **отдельную БД** для статистики
- Межсервисная коммуникация через **REST API (OpenFeign)**
- Batch-запросы для оптимизации N+1 проблемы

## Внешнее API

Внешний API приложения предоставляет функциональность через API Gateway на порту **8080**. Все запросы проходят через Gateway, который маршрутизирует их на соответствующие микросервисы.

### Публичные API (доступны всем)

**Категории**:
- `GET /categories` - получение списка категорий
- `GET /categories/{catId}` - получение категории по идентификатору

**События**:
- `GET /events` - получение событий с фильтрацией
- `GET /events/{id}` - получение подробной информации о событии

### Закрытые API (требуют аутентификации)

**Пользователи**:
- `GET /users/{userId}` - получение информации о пользователе
- `POST /users` - регистрация пользователя

**События**:
- `POST /users/{userId}/events` - создание события
- `GET /users/{userId}/events` - получение событий пользователя
- `GET /users/{userId}/events/{eventId}` - получение события пользователя
- `PATCH /users/{userId}/events/{eventId}` - редактирование события

**Запросы на участие**:
- `POST /users/{userId}/requests?eventId={eventId}` - создание запроса на участие
- `GET /users/{userId}/requests` - получение запросов пользователя
- `PATCH /users/{userId}/requests/{requestId}/cancel` - отмена запроса

### Административные API

**Пользователи**:
- `GET /admin/users` - получение всех пользователей
- `POST /admin/users` - создание пользователя
- `DELETE /admin/users/{userId}` - удаление пользователя

**Категории**:
- `POST /admin/categories` - создание категории
- `DELETE /admin/categories/{catId}` - удаление категории

**События**:
- `GET /admin/events` - получение всех событий с фильтрацией
- `GET /admin/events/{eventId}` - получение события по ID
- `PATCH /admin/events/{eventId}` - редактирование события (публикация/отклонение)

## Технологический стек

- Java 21, Spring Boot 3.3.0
- Spring Cloud: Gateway, Config, Netflix Eureka
- Базы данных: PostgreSQL
- Сборка: Maven 3.8+
- Дополнительно: Lombok, MapStruct, QueryDSL, Feign Clients

## Запуск приложения

### 1. Подготовка базы данных (Docker)

**Запустите контейнер с PostgreSQL**:
```bash
docker-compose up -d postgres
2. Запуск инфраструктурных сервисов (по порядку)
Config Server (порт 8888)

bash
cd infra/config-server
mvn spring-boot:run
Discovery Server (порт 8761)

bash
cd infra/discovery-server
mvn spring-boot:run
Gateway Server (порт 8080)

bash
cd infra/gateway-server
mvn spring-boot:run
3. Запуск бизнес-сервисов (в любом порядке)
bash
# Сервис статистики
cd stats/stats-server
mvn spring-boot:run

# Сервис пользователей
cd core/user-service
mvn spring-boot:run

# Сервис категорий
cd core/category-service
mvn spring-boot:run

# Сервис событий
cd core/event-service
mvn spring-boot:run

# Сервис заявок
cd core/request-service
mvn spring-boot:run
Альтернативный запуск (Docker Compose)
bash
# Сборка всех сервисов
mvn clean package -DskipTests

# Запуск всех контейнеров
docker-compose up -d
Валидация входных данных
Поле	Требования
title	от 3 до 120 символов
annotation	от 20 до 2000 символов
description	от 20 до 7000 символов
eventDate	не ранее чем через 2 часа от текущего момента
participantLimit	>= 0