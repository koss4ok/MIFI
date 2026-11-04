# Бронирование гостиниц

## Запуск

В четырёх отдельных терминалах из этой же папки:

```bash
java -jar discovery/target/discovery-1.0.0-SNAPSHOT.jar
java -jar hotel/target/hotel-1.0.0-SNAPSHOT.jar
java -jar booking/target/booking-1.0.0-SNAPSHOT.jar
java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

Сначала запустите Eureka, затем остальные сервисы. После запуска регистрация и обновление кэша Eureka могут занять около минуты; до этого Gateway может отвечать 503. Реестр: <http://localhost:8761/>. Внешний API: <http://localhost:8080>. Swagger: <http://localhost:8081/swagger-ui/index.html> и <http://localhost:8082/swagger-ui/index.html>.

По умолчанию создаются пользователи `admin` / `admin123` и `student` / `student123`, отель с номерами 101, 102 и недоступным 103. Пароли хранятся как BCrypt. H2 находится в памяти, поэтому при перезапуске соответствующего сервиса все его данные сбрасываются.

Для локального запуска заданы демонстрационные секреты. Их можно заменить одинаковыми значениями на обоих бизнес-сервисах через `JWT_SECRET` (не менее 32 байт) и `INTERNAL_SECRET`. Адрес Eureka задаётся через `EUREKA_URL`. Роль администратора нельзя получить через публичную регистрацию.

## Пример через PowerShell

```powershell
$auth = Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post -ContentType 'application/json' -Body '{"username":"student","password":"student123"}'
$headers = @{ Authorization = "Bearer $($auth.token)" }
Invoke-RestMethod http://localhost:8080/api/hotels -Headers $headers

$start = (Get-Date).AddDays(2).ToString('yyyy-MM-dd')
$end = (Get-Date).AddDays(4).ToString('yyyy-MM-dd')
$body = @{ requestId = [guid]::NewGuid().ToString(); hotelId = 1; autoSelect = $true; startDate = $start; endDate = $end } | ConvertTo-Json
$booking = Invoke-RestMethod http://localhost:8080/api/bookings -Method Post -ContentType 'application/json' -Headers $headers -Body $body
$booking
Invoke-RestMethod "http://localhost:8080/api/bookings/$($booking.id)" -Method Delete -Headers $headers
```

`requestId` — UUID, который клиент сохраняет и повторно использует для одного запроса. Повтор с теми же данными возвращает уже созданную запись, с другими — 409. `autoSelect: false` требует `roomId`; при `true` он игнорируется, а `hotelId` необязателен. Дата окончания не включается в интервал. При автоподборе выбирается свободный номер с наименьшим счётчиком, при равенстве — с меньшим ID.

## Основные маршруты

Все маршруты ниже доступны через Gateway. Кроме регистрации и входа требуется `Authorization: Bearer <token>`; JWT действует один час и проверяется каждым бизнес-сервисом.

| Метод и путь | Назначение |
|---|---|
| POST `/api/auth/register`, `/api/auth/login` | Регистрация и вход: `username`, `password` |
| GET `/api/hotels`, `/api/hotels/{id}` | Отели |
| GET `/api/rooms?startDate=...&endDate=...&hotelId=...` | Свободные номера; `hotelId` необязателен |
| GET `/api/rooms/recommend?startDate=...&endDate=...` | Те же номера, отсортированные по загрузке |
| GET `/api/rooms/{id}` | Информация о номере |
| POST `/api/bookings` | Создать бронь |
| GET `/api/bookings?page=0&size=20` | Своя история с пагинацией |
| GET, DELETE `/api/bookings/{id}` | Своя бронь / отмена |
| GET, POST `/api/users`; PUT, DELETE `/api/users/{id}` | Управление пользователями, ADMIN |
| POST `/api/hotels`; PUT, DELETE `/api/hotels/{id}` | Управление отелями, ADMIN |
| POST `/api/rooms`; PUT, DELETE `/api/rooms/{id}` | Управление номерами, ADMIN |
| GET `/api/rooms/statistics` | Номера со счётчиками `timesBooked`, ADMIN |

Для создания/обновления отеля передаются `name`, `address`; номера — `hotelId`, `number`, `available`; пользователя — `username`, `password`, `role`. Удаление пользователя или номера с историей запрещено, чтобы не потерять данные. При смене роли уже выданный JWT сохраняет прежние права до истечения срока действия.

## Устройство согласования

1. Booking фиксирует `PENDING` в локальной транзакции и отправляет Hotel ID бронирования и даты.
2. Hotel блокирует строку номера на время своей транзакции, проверяет пересечение интервалов и сохраняет слот. Повтор того же запроса не увеличивает счётчик повторно. `available` отвечает только за эксплуатационную доступность.
3. При успехе Booking фиксирует `CONFIRMED`; при ошибке — `CANCELLED` и запрашивает освобождение слота. Тайм-аут подключения и чтения — по 2 секунды; до трёх попыток с паузами 200 и 400 мс. Ответ 409 не повторяется.
4. Освобождение идемпотентно. Даже если отмена пришла раньше запоздавшего подтверждения, запись об отмене блокирует его. При неудачной компенсации остаётся `releasePending`; фоновая задача повторяет её, а зависший `PENDING` через минуту переводит в отменённое состояние. В логах шаги помечаются `bookingId`.

Внутренние маршруты `/internal/rooms/{id}/confirm-availability`, `/internal/rooms/{id}/release` и `/internal/rooms/recommend` не маршрутизируются через Gateway и требуют `X-Internal-Secret`. Gateway создаёт `X-Trace-Id`, передаёт его дальше и записывает результат запроса в лог.

## Проверка

```powershell
.\mvnw.cmd test
```

Интеграционные тесты проверяют параллельные бронирования, сортировку, повторную доставку и позднее подтверждение, успех и отмену саги, восстановление компенсации, роли и JWT. Отдельные тесты клиента проверяют тайм-ауты, число повторов и отказ без повтора при конфликте.
