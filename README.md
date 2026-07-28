# Демонстрационный gRPC-сервер

Автономный plaintext gRPC-сервер для проверки клиентов, генераторов и
инструментов разработки. API разделён по демонстрируемым возможностям, а каждый
публичный protobuf-пакет имеет версию `v1`.

## Структура API

| Сервис | Proto-файл | Назначение |
| --- | --- | --- |
| `demo.echo.v1.EchoService` | `demo/echo/v1/echo.proto` | Простой unary, `Empty`, repeated, map, oneof и optional message-поля |
| `demo.types.v1.TypesService` | `demo/types/v1/types.proto` | Все scalar-типы, enum, optional, вложенные сообщения и Well-Known Types |
| `demo.streaming.v1.StreamingService` | `demo/streaming/v1/streaming.proto` | Server, client и bidirectional streaming, а также ошибка после нескольких ответов |
| `demo.error.v1.ErrorService` | `demo/error/v1/error.proto` | Простая и rich error, deadline/cancellation и намеренный `UNIMPLEMENTED` |

Стандартные gRPC health service и server reflection включены отдельно
конфигурацией Spring gRPC.

Java-реализации повторяют те же границы:

```text
service/
├── echo/EchoGrpcService.java
├── types/TypesGrpcService.java
├── streaming/StreamingGrpcService.java
└── error/ErrorGrpcService.java
```

Такое разбиение не связывает проверку streaming с большим сообщением всех типов
и позволяет развивать каждый сценарий независимо.

## Демонстрационные сценарии

Сервер покрывает:

- unary, server-streaming, client-streaming и bidirectional-streaming RPC;
- все protobuf scalar-типы, enum, optional, repeated, map, oneof и вложенные
  сообщения;
- официальный набор `google.protobuf` Well-Known Types, включая `Any`;
- runtime-проверку входящих сообщений через Protovalidate;
- простую gRPC-ошибку и rich error details с `DemoErrorDetail`,
  `google.rpc.BadRequest` и `google.rpc.ErrorInfo`;
- ошибку после нескольких streaming-ответов;
- успешный пустой запрос/ответ и намеренно нереализованный RPC;
- deadline и обнаружение отмены долгого серверного вызова;
- ASCII и binary request metadata, response headers и response trailers;
- reflection и стандартный gRPC health service.

Protovalidate применяется глобальным server interceptor ко всем входящим
protobuf-сообщениям. Нарушения возвращаются с кодом `INVALID_ARGUMENT` и
payload `buf.validate.Violations` в rich error details.

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

Остановка:

```powershell
docker compose down
```

## Быстрая проверка через grpcurl

Получить список сервисов через reflection:

```powershell
grpcurl -plaintext localhost:9090 list
```

Выполнить unary echo:

```powershell
grpcurl -plaintext `
  -H "x-request-id: demo-42" `
  -d '{"message":"hello","labels":["demo"],"text":"primary"}' `
  localhost:9090 demo.echo.v1.EchoService/Echo
```

Увидеть Protovalidate-ошибку:

```powershell
grpcurl -plaintext -d '{}' `
  localhost:9090 demo.echo.v1.EchoService/Echo
```

Получить серверный поток:

```powershell
grpcurl -plaintext `
  -d '{"message":"stream","responseCount":3}' `
  localhost:9090 demo.streaming.v1.StreamingService/ServerStream
```

Получить rich error details:

```powershell
grpcurl -plaintext `
  -d '{"message":"invalid input","field":"message","requestId":"req-42"}' `
  localhost:9090 demo.error.v1.ErrorService/RichFailure
```

Для проверки metadata передайте `x-request-id: demo-42`. Сервер вернёт его в
response headers вместе с `x-demo-server`, а в trailers добавит
`x-demo-trailer` со статусом вызова. Binary metadata с ключом `x-demo-bin`
также возвращается в response headers.

## Миграция со старого `demo.DemoService`

Реструктуризация намеренно меняет demo API:

| Старый RPC | Новый RPC |
| --- | --- |
| `echo` | `demo.echo.v1.EchoService/Echo` |
| `nonStream` | `demo.types.v1.TypesService/EchoAllTypes` |
| `wellKnownTypes` | `demo.types.v1.TypesService/EchoWellKnownTypes` |
| `serverStream` | `demo.streaming.v1.StreamingService/ServerStream` |
| `clientStream` | `demo.streaming.v1.StreamingService/ClientStream` |
| `binaryStream` | `demo.streaming.v1.StreamingService/BidirectionalStream` |
| `partialFailure` | `demo.streaming.v1.StreamingService/PartialFailure` |
| `alwaysFails` | `demo.error.v1.ErrorService/RichFailure` |

Сообщения streaming теперь специализированы под сценарий, а `AllTypesRequest`
и `AllTypesResponse` заменены одним симметричным сообщением `AllTypes`.

## Следующие полезные примеры

Следующими стоит добавлять отдельными профилями или сервисами:

1. TLS и mTLS с тестовыми сертификатами;
2. authentication/authorization interceptor и `PERMISSION_DENIED`;
3. backpressure через `ServerCallStreamObserver.isReady()`;
4. gzip-compression и отдельный сценарий превышения лимита сообщения;
5. retryable `UNAVAILABLE` с `google.rpc.RetryInfo`;
6. schema evolution: пакет `v2`, `reserved`-поля и автоматическая breaking-check.

TLS и authentication лучше не смешивать с текущим plaintext-профилем: иначе
сервер перестанет быть быстрым универсальным стендом для локальных клиентов.
