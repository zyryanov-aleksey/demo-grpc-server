# Демонстрационный gRPC-сервер

Автономный plaintext gRPC-сервер с reflection и standard gRPC health service.
Он содержит сценарии, на которых проверяется Zyryal gRPC:

- unary, server-streaming, client-streaming и bidi-streaming RPC;
- scalar, optional, repeated, map, oneof и вложенные protobuf-поля;
- Well-Known Types и `Any`;
- rich gRPC error details и ошибку после нескольких streaming-ответов;
- request metadata, response headers и response trailers.

## Запуск

Локально:

```powershell
.\gradlew.bat bootRun
```

Сервер доступен по адресу `localhost:9090`.

Через Docker Compose:

```powershell
docker compose up --build -d
```

Порт на хосте можно изменить:

```powershell
$env:DEMO_GRPC_PORT = "19090"
docker compose up --build -d
```

Для проверки metadata передайте, например, `x-request-id: demo-42`. Сервер
вернёт его в response headers вместе с `x-demo-server`, а в trailers добавит
`x-demo-trailer`.

Остановка:

```powershell
docker compose down
```
