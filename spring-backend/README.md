# Spring Boot Backend

This module is the main application backend for the captioning system. It handles authentication, persistence, image storage, caption history, and FastAPI orchestration.

## Stack

- Java 17+
- Spring Boot 3
- Spring Security with JWT
- Spring Data JPA
- PostgreSQL

## Project Structure

```text
src/main/java/com/example/app/
    controller/
    service/
    repository/
    model/
    dto/
    config/
    security/
    exception/
```

## Main Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`
- `POST /api/images/upload`
- `GET /api/images/user/{id}`
- `POST /api/captions/generate`
- `POST /api/captions/select`
- `GET /api/captions/history`

## Caption Generation Flow

`POST /api/captions/generate` accepts:

- `image` as multipart file, or
- `imageId` for a previously uploaded image
- `style` optional
- `count` optional

If the current FastAPI service returns a single caption, this backend can call it multiple times and persist the resulting set without changing the Python code.

## Environment Variables

```text
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/caption_app
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_JPA_DDL_AUTO=update
JWT_SECRET=change-me-to-a-long-random-32-byte-secret
JWT_EXPIRATION_MINUTES=60
FASTAPI_BASE_URL=http://localhost:8000/api
FASTAPI_GENERATE_PATH=/generate-caption
FASTAPI_CAPTIONS_PER_REQUEST=3
DEFAULT_CAPTION_STYLE=casual
UPLOAD_DIR=uploads
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

## Run

```bash
mvn spring-boot:run
```

## Test

```bash
mvn test
```

Tests use an in-memory H2 database. Runtime configuration still targets PostgreSQL.
