# Auth Rate Limiting

Tài liệu này giải thích cơ chế rate limiting hiện tại của dự án, tập trung vào `module-identity` và `app-bootstrap`.

Mục tiêu:

- giúp người mới đọc hiểu được "rate limit đang chạy như nào"
- chỉ rõ Redis đang lưu gì
- mô tả flow login, password reset, refresh token
- chỉ rõ các thành phần trong backend và trách nhiệm của từng thành phần
- giải thích ưu điểm, hạn chế, và hướng nâng cấp sau này

---

## 1. Tổng quan

Hiện tại dự án đang dùng rate limiting cho các flow auth nhạy cảm:

- login
- password reset request
- refresh token

Cơ chế này được triển khai theo hướng:

- state nằm trong Redis
- service layer là nơi enforce rule
- web layer cung cấp `clientKey` cho service
- thuật toán hiện tại là `fixed window` dựa trên `INCR + EXPIRE`

Nó không phải là gateway rate limiting, cũng không phải là sliding window hay token bucket.

---

## 2. Vì sao cần rate limiting

Nếu không có rate limiting, các endpoint auth public rất dễ bị abuse:

- brute-force password qua endpoint login
- spam password reset request
- spam refresh token để thử replay hoặc tăng tải hệ thống

Rate limiting giảm rủi ro đó bằng cách đặt ngưỡng:

- một email được thử login sai tối đa bao nhiêu lần trong 10 phút
- một IP được gọi password reset tối đa bao nhiêu lần trong 15 phút
- một user được refresh tối đa bao nhiêu lần trong 5 phút

Khi vượt ngưỡng, backend trả `429 Too Many Requests`.

---

## 3. Thành phần trong hệ thống

### 3.1. `RateLimitProperties`

File:

- `module-identity/src/main/java/com/example/moduleidentity/config/RateLimitProperties.java`

Trách nhiệm:

- bind config từ `application.yml` / env
- định nghĩa các rule cho login, password reset, refresh
- định nghĩa `enabled` và `failOpen`

Code rút gọn:

```java
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
  private boolean enabled = true;
  private boolean failOpen = true;
  private AuthWindow login = new AuthWindow(Duration.ofMinutes(10), 5, 20, 5);
  private AuthWindow passwordReset = new AuthWindow(Duration.ofMinutes(15), 3, 10, 3);
  private RefreshWindow refresh = new RefreshWindow(Duration.ofMinutes(5), 20, 60);
}
```

Giá trị mặc định hiện tại:

- `login`
    - `emailLimit = 5`
    - `clientLimit = 20`
    - `pairLimit = 5`
    - `window = 10 phút`
- `passwordReset`
    - `emailLimit = 3`
    - `clientLimit = 10`
    - `pairLimit = 3`
    - `window = 15 phút`
- `refresh`
    - `userLimit = 20`
    - `clientLimit = 60`
    - `window = 5 phút`

### 3.2. `AuthRateLimiter`

File:

- `module-identity/src/main/java/com/example/moduleidentity/domain/security/AuthRateLimiter.java`

Trách nhiệm:

- build key rate limit
- check key nào đã vượt ngưỡng
- tăng counter cho key
- xóa counter khi cần
- log và ghi metric khi block hoặc khi Redis bị lỗi

Đây là lớp "decision engine" của rate limiting.

### 3.3. `RateLimitStore`

File:

- `module-identity/src/main/java/com/example/moduleidentity/port/RateLimitStore.java`

Trách nhiệm:

- abstraction cho backend lưu state rate limit

Nó cho `module-identity` biết cách:

- đọc count
- tăng count
- xóa key

nhưng không cần biết đang dùng Redis, DB, hay in-memory.

### 3.4. `RedisRateLimitStore`

File:

- `app-bootstrap/src/main/java/com/example/appbootstrap/config/RedisRateLimitStore.java`

Trách nhiệm:

- implementation thật sự của `RateLimitStore` bằng Redis

Code rút gọn:

```java
@Component
public class RedisRateLimitStore implements RateLimitStore {

  @Override
  public long increment(String key, Duration window) {
    Long current = redisTemplate.opsForValue().increment(key);
    if (current == 1L) {
      redisTemplate.expire(key, window);
    }
    return current;
  }

  @Override
  public long getCount(String key) {
    String value = redisTemplate.opsForValue().get(key);
    return value == null ? 0L : Long.parseLong(value);
  }

  @Override
  public void delete(String key) {
    redisTemplate.delete(key);
  }
}
```

### 3.5. `ClientKeyResolver`

Files:

- `module-identity/src/main/java/com/example/moduleidentity/port/ClientKeyResolver.java`
- `app-bootstrap/src/main/java/com/example/appbootstrap/config/HttpRequestClientKeyResolver.java`

Trách nhiệm:

- lấy `clientKey` từ HTTP request
- thông thường `clientKey` ở đây là IP

Thứ tự lấy IP hiện tại (an toan hon voi trusted proxy):

1. lấy `request.getRemoteAddr()`
2. kiểm tra remoteAddr có thuộc trusted proxy list hay không
3. nếu trusted mới xét `X-Forwarded-For`, rồi `X-Real-IP`
4. nếu không trusted thì dùng luôn `request.getRemoteAddr()`

Code rút gọn:

```java
String remoteAddr = request.getRemoteAddr();
if (isTrustedProxy(remoteAddr)) {
  String forwardedFor = request.getHeader("X-Forwarded-For");
  if (StringUtils.hasText(forwardedFor)) {
    return forwardedFor.split(",")[0].trim();
  }

  String realIp = request.getHeader("X-Real-IP");
  if (StringUtils.hasText(realIp)) {
    return realIp.trim();
  }
}

return remoteAddr;
```

Xem tài liệu chi tiết:

- [docs/client-ip-trusted-proxy-guide.md](docs/client-ip-trusted-proxy-guide.md)

### 3.6. `AuthService` và `PasswordResetService`

Files:

- `module-identity/src/main/java/com/example/moduleidentity/application/AuthService.java`
- `module-identity/src/main/java/com/example/moduleidentity/application/PasswordResetService.java`

Trách nhiệm:

- đây là nơi flow nghiệp vụ auth thực sự chạy
- gọi `AuthRateLimiter` trước khi tiếp tục xử lý auth

Ngắn gọn:

- login dùng throttle theo `email`, `client`, `email|client`
- password reset request dùng throttle theo `email`, `client`, `email|client`
- refresh dùng throttle theo `client`, sau đó theo `user`

---

## 4. Fixed window là gì

### 4.1. Khái niệm

`Fixed window` là cách giới hạn request theo từng "ô thời gian" có độ dài cố định.

Ví dụ:

- login fail tối đa 5 lần trong 10 phút

Nếu cửa sổ bắt đầu lúc `10:03:12`, thì backend sẽ đếm:

- tất cả login fail từ `10:03:12` đến `10:13:12`

Hết `10:13:12`, window cũ hết hạn. Lần fail tiếp theo sẽ tạo một window mới.

### 4.2. Cách dự án này implement fixed window

Mỗi key rate limit được lưu trong Redis như một counter string:

```text
rate-limit:login:email:user@example.com -> "3"
```

Khi phát sinh một attempt:

1. gọi `INCR key`
2. nếu giá trị mới là `1` thì gọi `EXPIRE key <window>`

Điều này có nghĩa:

- lần đầu trong window tạo ra key
- key tự động hết hạn sau `window`
- trong suốt TTL đó, counter tiếp tục tăng

### 4.3. Ví dụ cụ thể

Giả sử rule login theo email là:

- 5 lần / 10 phút

Lúc `10:00:00` user login sai lần đầu:

```text
INCR rate-limit:login:email:user@example.com -> 1
EXPIRE rate-limit:login:email:user@example.com 600s
```

Lúc `10:02:00`, login sai tiếp:

```text
INCR rate-limit:login:email:user@example.com -> 2
```

Lúc `10:08:00`, key có thể đang là:

```text
rate-limit:login:email:user@example.com = "4"
TTL = 120s
```

Lúc `10:09:00`, thêm 1 lần sai nữa:

```text
INCR ... -> 5
```

Lúc `10:09:30`, trước khi tăng tiếp, backend check thấy count đã `>= 5`, nên block và trả `429`.

Lúc `10:10:00`, nếu TTL hết, key tự động biến mất:

```text
GET rate-limit:login:email:user@example.com -> null
```

Window mới bắt đầu từ lần attempt tiếp theo.

---

## 5. Redis đang lưu gì

### 5.1. Dạng dữ liệu

Redis lưu:

- key: string
- value: số đếm string
- TTL: thời gian hết hạn của key

Nó không lưu object JSON, không lưu hash map, không lưu list.

### 5.2. Format key

Format key được tạo trong `AuthRateLimiter.key(...)`:

```text
rate-limit:<action>:<dimension>:<identifier>
```

Ví dụ:

```text
rate-limit:login:email:user@example.com
rate-limit:login:client:203.0.113.10
rate-limit:login:pair:user@example.com|203.0.113.10
rate-limit:password-reset:email:user@example.com
rate-limit:refresh:user:42
rate-limit:refresh:client:203.0.113.10
```

### 5.3. Value và TTL

Ví dụ trong Redis:

```text
rate-limit:login:email:user@example.com      -> "3"   TTL 8m12s
rate-limit:login:client:203.0.113.10         -> "7"   TTL 8m12s
rate-limit:login:pair:user@example.com|203.0.113.10 -> "3" TTL 8m12s
rate-limit:refresh:user:42                   -> "11"  TTL 2m40s
```

### 5.4. Normalize identifier

Trong `AuthRateLimiter.normalize(...)`, identifier được chuẩn hóa:

- `trim()`
- `toLowerCase()`
- null/blank sẽ thành `anonymous`

Ví dụ:

- `" User@Example.com "` -> `user@example.com`
- `null` -> `anonymous`

Mục tiêu:

- tránh tạo ra nhiều key cho cùng một giá trị
- đơn giản hóa count

---

## 6. Các dimension hiện tại

### 6.1. Login

Login đang được giới hạn theo 3 chiều:

- `email`
- `client`
- `pair(email|client)`

Vì sao phải có 3 chiều:

- chỉ theo `email` là chưa đủ, vì attacker có thể đổi email liên tục
- chỉ theo `client` là chưa đủ, vì attacker có thể đổi IP
- `pair` giúp chặn một IP tấn công một email cụ thể nhanh hơn

### 6.2. Password reset request

Password reset request cũng dùng 3 chiều:

- `email`
- `client`
- `pair(email|client)`

Lý do:

- chặn spam reset theo email
- chặn spam reset từ một IP
- chặn một IP target một email cụ thể

### 6.3. Refresh

Refresh đang được giới hạn theo 2 chiều:

- `client`
- `user`

Flow này hợp lý vì refresh token đã có session và user rõ ràng.

---

## 7. Flow login

### 7.1. Mục tiêu

Login chỉ tăng counter khi đăng nhập thất bại.

Nếu đăng nhập thành công:

- xóa counter login để user được "mở khóa" ngay

### 7.2. Flow từng bước

Trong `AuthService.login(...)`:

1. nhận `email`, `password`, `clientKey`
2. nếu `clientKey` null thì resolve từ `ClientKeyResolver`
3. gọi `authRateLimiter.checkLoginAllowed(email, clientKey)`
4. nếu đã vượt ngưỡng thì throw `AppException(..., 429)`
5. tìm user theo email
6. nếu không tìm thấy hoặc password sai:
    - gọi `invalidCredentials(...)`
    - bên trong `invalidCredentials(...)` gọi `recordLoginFailure(...)`
    - tăng counter cho `email`, `client`, `pair`
    - trả `401 INVALID_CREDENTIALS`
7. nếu đúng password:
    - gọi `resetLoginFailures(...)`
    - xóa các key login hiện tại
    - tạo access token và refresh token

### 7.3. Code chính

```java
authRateLimiter.checkLoginAllowed(normalizedEmail, normalizedClientKey);

UserEntity user =
    userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> invalidCredentials(normalizedEmail, normalizedClientKey));

if (!passwordEncoder.matches(password, user.getPasswordHash())) {
  throw invalidCredentials(normalizedEmail, normalizedClientKey);
}

authRateLimiter.resetLoginFailures(normalizedEmail, normalizedClientKey);
```

### 7.4. Ví dụ

Giả sử:

- email: `user@example.com`
- client: `203.0.113.10`

Nếu login sai 3 lần, Redis có thể có:

```text
rate-limit:login:email:user@example.com = 3
rate-limit:login:client:203.0.113.10 = 3
rate-limit:login:pair:user@example.com|203.0.113.10 = 3
```

Nếu lần thứ 6 theo email, backend block.

Nếu user đăng nhập đúng, backend xóa:

```text
DEL rate-limit:login:email:user@example.com
DEL rate-limit:login:client:203.0.113.10
DEL rate-limit:login:pair:user@example.com|203.0.113.10
```

---

## 8. Flow password reset request

### 8.1. Mục tiêu

Password reset request là endpoint dễ bị spam, nên mỗi request đều phải bị tính vào quota.

Khác với login:

- login chỉ tăng khi fail
- password reset request tăng cho mỗi request

### 8.2. Flow từng bước

Trong `PasswordResetService.requestPasswordReset(...)`:

1. nhận `email`, `clientKey`
2. resolve `clientKey` nếu cần
3. gọi `consumePasswordResetAttempt(email, clientKey)`
4. nếu vượt ngưỡng thì trả `429 TOO_MANY_PASSWORD_RESET_REQUESTS`
5. tìm user theo email
6. nếu không có user:
    - vẫn trả kết quả "thành công giả"
    - không lộ user enumeration
7. nếu có user:
    - xóa reset token cũ
    - tạo raw token mới
    - hash token
    - lưu hash vào DB

### 8.3. Code chính

```java
authRateLimiter.consumePasswordResetAttempt(
    normalizedEmail, normalizeClientKey(clientKey, normalizedEmail));

UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);

if (user == null) {
  return null;
}
```

### 8.4. Ví dụ

Mỗi lần request reset cho `user@example.com` từ IP `203.0.113.10`:

```text
rate-limit:password-reset:email:user@example.com
rate-limit:password-reset:client:203.0.113.10
rate-limit:password-reset:pair:user@example.com|203.0.113.10
```

Nếu `pairLimit = 3`, request thứ 4 trong 15 phút sẽ bị block.

---

## 9. Flow refresh token

### 9.1. Mục tiêu

Refresh token cần được chặn spam ở 2 mức:

- một client/IP spam refresh liên tục
- một user bị refresh quá nhiều lần trong thời gian ngắn

### 9.2. Flow từng bước

Trong `AuthService.refresh(...)`:

1. nhận `refreshToken`, `clientKey`
2. resolve `clientKey`
3. gọi `consumeRefreshClientAttempt(clientKey)`
4. tìm active session qua `refreshToken`
5. lấy `user` từ session
6. gọi `consumeRefreshUserAttempt(user.getId())`
7. kiểm tra token type, token valid, user id, token invalidation
8. rotate refresh token

### 9.3. Code chính

```java
authRateLimiter.consumeRefreshClientAttempt(normalizedClientKey);

var session = refreshTokenSessionService.requireActiveSession(normalizedRefreshToken);
UserEntity user = session.getUser();

authRateLimiter.consumeRefreshUserAttempt(user.getId());
```

### 9.4. Ví dụ

Nếu user `42` từ IP `203.0.113.10` spam refresh:

```text
rate-limit:refresh:client:203.0.113.10 = 18
rate-limit:refresh:user:42 = 18
```

Nếu `userLimit = 20`, lần thứ 21 trong 5 phút sẽ bị block.

---

## 10. `check` và `consume` khác nhau như nào

### 10.1. `checkLoginAllowed(...)`

Hàm này chỉ kiểm tra:

- key hiện tại đã vượt ngưỡng chưa

Nó không tăng counter.

Dùng cho login vì login thành công không nên bị tính vào quota fail.

### 10.2. `consume...(...)`

Hàm `consume`:

1. check trước xem đã vượt ngưỡng chưa
2. nếu chưa thì tăng counter
3. nếu sau khi tăng mà vượt ngưỡng thì block

Dùng cho:

- password reset request
- refresh
- login fail

Code rút gọn:

```java
private void consume(...) {
  assertBelowLimit(...);

  for (LimitCandidate candidate : candidates) {
    long current = safeIncrement(candidate);
    if (current > candidate.limit()) {
      throw new AppException(code, message, 429);
    }
  }
}
```

---

## 11. `failOpen` là gì

`failOpen` được config trong `RateLimitProperties`.

Nếu `failOpen = true`:

- Redis bị lỗi thì không block auth
- hệ thống log warning
- metric `auth.rate_limit.degraded` được tăng
- count được coi là `0`

Code tương đương:

```java
if (rateLimitProperties.isFailOpen()) {
  log.warn(...);
  meterRegistry.counter("auth.rate_limit.degraded", "operation", operation).increment();
  return 0L;
}
```

Ý nghĩa thực tế:

- ưu tiên auth vẫn sống nếu Redis down
- chấp nhận tạm thời mất lớp bảo vệ rate limiting

Nếu `failOpen = false`:

- Redis lỗi sẽ nổi thẳng ra ngoài
- request auth có thể fail theo backend error

Với giai đoạn hiện tại, `failOpen = true` là lựa chọn an toàn vận hành hơn.

---

## 12. Logging và metrics

### 12.1. Logging

Khi block:

```text
Rate limit blocked: action=login, dimension=email, keyHash=abc123..., current=5, limit=5
```

Khi Redis bị lỗi:

```text
Rate limit backend degraded: operation=increment, dimension=client, keyHash=def456..., failOpen=true
```

Lưu ý:

- log không ghi raw email/IP
- log chỉ ghi hash rút gọn

### 12.2. Metrics

Micrometer counters hiện tại:

- `auth.rate_limit.consume`
- `auth.rate_limit.blocked`
- `auth.rate_limit.degraded`

Tác dụng:

- quan sát tần suất request bị limit
- quan sát Redis backend có bị degrade không

---

## 13. Config trong `application.yml` và env

Dự án bind config từ `app.rate-limit.*`.

Ví dụ:

```yaml
app:
    rate-limit:
        enabled: ${APP_RATE_LIMIT_ENABLED:true}
        fail-open: ${APP_RATE_LIMIT_FAIL_OPEN:true}
        login:
            window: ${APP_RATE_LIMIT_LOGIN_WINDOW:10m}
            email-limit: ${APP_RATE_LIMIT_LOGIN_EMAIL_LIMIT:5}
            client-limit: ${APP_RATE_LIMIT_LOGIN_CLIENT_LIMIT:20}
            pair-limit: ${APP_RATE_LIMIT_LOGIN_PAIR_LIMIT:5}
        password-reset:
            window: ${APP_RATE_LIMIT_PASSWORD_RESET_WINDOW:15m}
            email-limit: ${APP_RATE_LIMIT_PASSWORD_RESET_EMAIL_LIMIT:3}
            client-limit: ${APP_RATE_LIMIT_PASSWORD_RESET_CLIENT_LIMIT:10}
            pair-limit: ${APP_RATE_LIMIT_PASSWORD_RESET_PAIR_LIMIT:3}
        refresh:
            window: ${APP_RATE_LIMIT_REFRESH_WINDOW:5m}
            user-limit: ${APP_RATE_LIMIT_REFRESH_USER_LIMIT:20}
            client-limit: ${APP_RATE_LIMIT_REFRESH_CLIENT_LIMIT:60}
```

Redis được config qua:

```yaml
spring:
    data:
        redis:
            host: ${SPRING_DATA_REDIS_HOST:localhost}
            port: ${SPRING_DATA_REDIS_PORT:6379}
            password: ${SPRING_DATA_REDIS_PASSWORD:}
            timeout: ${SPRING_DATA_REDIS_TIMEOUT:2s}
```

---

## 14. Tại sao service layer là nơi enforce

Dự án đang đặt rate limiting ở service layer thay vì controller hoặc filter vì:

- mọi caller đi qua auth service đều bị bảo vệ
- logic auth và logic throttle nằm gần nhau, dễ test hơn
- dễ thay đổi rule theo business flow

Nếu đặt chỉ ở web filter:

- có thể bỏ sót internal caller
- logic khó kết hợp với user/session/token flow

Ví dụ refresh:

- rate limit cần theo `client`
- sau khi tìm được session mới rate limit tiếp theo `user`

Flow đó service layer xử lý dễ hơn web filter.

---

## 15. Hạn chế của implementation hiện tại

### 15.1. Đây là fixed window, không phải sliding window

Nghĩa là có hiệu ứng "boundary":

- cuối window cũ và đầu window mới có thể tạo ra hai cụm request sát nhau

Ví dụ:

- 5 request ở `10:09:59`
- 5 request nữa ở `10:10:01`

về mặt cảm nhận, user có thể gửi 10 request trong khoảng rất ngắn nếu trúng ranh giới.

### 15.2. Chưa atomic trên nhiều key

Hiện tại flow là:

1. `getCount`
2. `increment`

và làm trên nhiều key riêng:

- email
- client
- pair

Trong traffic đồng thời cao, vẫn có khả năng race condition nhỏ.

Nếu muốn chặt hơn, có thể:

- dùng Lua script trong Redis
- hoặc dùng token bucket/sliding window atomic hơn

### 15.3. Chưa có request-id trong metric dimension

Hiện tại metric đếm tổng quan, không track theo request.

Điều này ổn cho monitoring cơ bản, nhưng chưa phục vụ forensic chi tiết.

---

## 16. Hướng nâng cấp sau này

Nếu cần nâng cấp production hơn nữa, các bước hợp lý là:

### 16.1. Lua script cho multi-key atomic check + increment

Mục tiêu:

- check và increment nhiều key trong một round-trip Redis
- giảm race condition

### 16.2. Sliding window hoặc token bucket

Mục tiêu:

- hành vi mềm hơn
- giảm boundary effect của fixed window

### 16.3. Prefix / namespace theo môi trường

Ví dụ:

```text
rate-limit:prod:login:email:user@example.com
rate-limit:staging:login:email:user@example.com
```

Nếu nhiều môi trường dùng chung Redis thì nên có namespace rõ.

### 16.4. Thêm gateway/WAF rate limiting

Layer hiện tại là auth-business rate limiting.

Sau này có thể thêm:

- NGINX rate limiting
- Cloudflare WAF
- API Gateway rate limiting

để chặn sớm theo IP ở edge.

---

## 17. Tóm tắt flow ngắn gọn

### 17.1. Login

```text
HTTP request
-> resolve client IP
-> AuthService.login(...)
-> AuthRateLimiter.checkLoginAllowed(email, client)
-> nếu blocked: 429
-> kiểm tra user + password
-> nếu sai: recordLoginFailure(email, client)
-> nếu đúng: resetLoginFailures(email, client)
-> phát token
```

### 17.2. Password reset request

```text
HTTP request
-> resolve client IP
-> PasswordResetService.requestPasswordReset(...)
-> AuthRateLimiter.consumePasswordResetAttempt(email, client)
-> nếu blocked: 429
-> nếu user không tồn tại: return bình thường
-> nếu tồn tại: tạo reset token mới
```

### 17.3. Refresh

```text
HTTP request
-> resolve client IP
-> AuthService.refresh(...)
-> AuthRateLimiter.consumeRefreshClientAttempt(client)
-> tìm active refresh session
-> AuthRateLimiter.consumeRefreshUserAttempt(userId)
-> validate refresh token
-> rotate refresh token
-> trả token mới
```

---

## 18. Các file quan trọng cần đọc

Nếu muốn đọc code theo thứ tự để hiểu nhanh, nên đọc:

1. `module-identity/src/main/java/com/example/moduleidentity/config/RateLimitProperties.java`
2. `module-identity/src/main/java/com/example/moduleidentity/port/RateLimitStore.java`
3. `app-bootstrap/src/main/java/com/example/appbootstrap/config/RedisRateLimitStore.java`
4. `module-identity/src/main/java/com/example/moduleidentity/domain/security/AuthRateLimiter.java`
5. `app-bootstrap/src/main/java/com/example/appbootstrap/config/HttpRequestClientKeyResolver.java`
6. `module-identity/src/main/java/com/example/moduleidentity/application/AuthService.java`
7. `module-identity/src/main/java/com/example/moduleidentity/application/PasswordResetService.java`

---

## 19. Kết luận

Implementation hiện tại là một rate limiting layer:

- đơn giản
- dễ hiểu
- dễ vận hành
- phù hợp cho auth flow production có Redis

Nó giải bài toán thực tế:

- brute-force login
- spam password reset
- spam refresh

nhưng vẫn giữ architecture sạch:

- `module-identity` chỉ biết abstraction
- `app-bootstrap` cung cấp Redis implementation và request IP resolver

Nếu cần mở rộng sau này, hướng nâng cấp hợp lý nhất là:

- Redis Lua script
- sliding window / token bucket
- edge rate limiting tại gateway hoặc WAF
