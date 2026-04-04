# Hướng Dẫn Test Auth API

## Mục tiêu

Tài liệu này mô tả:

- auth API v1 hiện đã được cover những gì
- các file test chính đang dùng
- những phần còn thiếu nếu muốn đẩy bộ test lên mức production-grade

Phạm vi auth API v1 hiện tại:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`

---

## Các File Test Chính

### 1. Test tích hợp cho auth API

Files:

- [AuthControllerIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerIntegrationTest.java)
- [AuthControllerRateLimitIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerRateLimitIntegrationTest.java)
- [AuthControllerRateLimitFailOpenIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerRateLimitFailOpenIntegrationTest.java)

Vai trò:

- test trực tiếp contract HTTP của auth API
- xác nhận validation, business error, response wrapper, rate limiting, và OpenAPI cho nhóm auth

### 2. Test phụ trợ cho web và security infrastructure

Files:

- [ClientKeyResolverIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/ClientKeyResolverIntegrationTest.java)
- [JwtAuthenticationFilterIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/JwtAuthenticationFilterIntegrationTest.java)
- [WebInfrastructureIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/WebInfrastructureIntegrationTest.java)
- [RedisRateLimitStoreTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/config/RedisRateLimitStoreTest.java)

Vai trò:

- xác nhận resolver lấy `clientKey` đúng theo request/IP
- xác nhận filter JWT và web infrastructure vẫn hoạt động sau khi thêm auth API
- xác nhận Redis rate-limit store hoạt động đúng

### 3. Test application layer của identity

Files:

- [AuthServiceTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/module-identity/src/test/java/com/example/moduleidentity/application/AuthServiceTest.java)
- [PasswordResetServiceTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/module-identity/src/test/java/com/example/moduleidentity/application/PasswordResetServiceTest.java)
- [RefreshTokenSessionServiceTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/module-identity/src/test/java/com/example/moduleidentity/application/RefreshTokenSessionServiceTest.java)

Vai trò:

- test logic nghiệp vụ auth trong `module-identity`
- xác nhận các rule như refresh invalidation, throttle, password reset, session lookup, revoke, cleanup

---

## Hiện Tại Đã Cover Gì

### Register

Đã cover:

- đăng ký thành công
- trả về wrapper success đúng shape
- trả về user summary tối thiểu
- duplicate email trả `EMAIL_ALREADY_EXISTS`
- `acceptTerms=false` trả `TERMS_NOT_ACCEPTED`
- có `phone` nhưng thiếu `nationality` bị chặn
- có `nationality` nhưng thiếu `phone` bị chặn
- `nationality` không đúng ISO alpha-2 bị chặn
- số điện thoại sai format theo country bị chặn
- validation lỗi cho field `email`, `password`, `firstName`, `lastName`

### Login

Đã cover:

- login thành công
- trả `accessToken`
- trả `refreshToken`
- trả `tokenType`
- trả `expiresAt`
- trả `message = Operation successful`
- sai mật khẩu trả `INVALID_CREDENTIALS`
- login sai liên tiếp vượt ngưỡng trả `429`
- `clientKey` theo IP thực sự ảnh hưởng tới bucket rate limit

### Refresh

Đã cover:

- refresh thành công
- rotate refresh token
- trả token pair mới
- trả `message = Operation successful`
- refresh token hết hạn
- refresh token đã bị revoke
- refresh token malformed nhưng có session vẫn bị từ chối
- refresh token sai chữ ký bị từ chối
- refresh token thiếu `type` claim bị từ chối
- access token dùng sai vào endpoint refresh bị từ chối
- refresh token có `userId` không khớp bị từ chối
- refresh token phát hành trước `tokenInvalidBefore` bị từ chối
- refresh spam vượt ngưỡng trả `429`
- concurrent refresh trên cùng token chỉ cho một request thành công

### Logout

Đã cover:

- logout thành công với refresh token hợp lệ
- session refresh bị revoke
- refresh lại bằng token cũ trả lỗi
- logout lặp lại vẫn có behavior rõ ràng và không nổ lỗi
- logout với refresh token blank bị chặn bởi validation DTO
- trả `message = Logout successful`

### Password Reset

Đã cover:

- `password-reset/request` luôn trả generic success kể cả khi email không tồn tại
- `password-reset/request` spam vượt ngưỡng trả `429`
- `password-reset/request` không trả `data`
- `password-reset/confirm` thành công với token hợp lệ
- `password-reset/confirm` trả `RESET_TOKEN_INVALID` với token không hợp lệ
- reset token đã dùng rồi bị từ chối
- reset token hết hạn bị từ chối
- reset password revoke toàn bộ active refresh sessions của user
- reset password cập nhật `tokenInvalidBefore`
- `password-reset/confirm` trả `message = Password reset successful`

### Validation Error Contract

Đã cover:

- response lỗi validation có `code = VALIDATION_ERROR`
- `details` trả về theo field
- blank token/email cho các DTO auth chính bị chặn đúng

Ví dụ shape đang được verify:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": {
      "password": "Password must have at least 6 characters"
    }
  }
}
```

### Contract Metadata

Đã cover:

- success response có `X-Request-Id`
- business error có `path`
- error response có `requestId`
- error response có `timestamp`
- nhiều auth endpoint đã được verify đi qua success wrapper đúng shape
- invalid login error đã được verify đầy đủ `statusCode`, `path`, `requestId`, `timestamp`

### Swagger / OpenAPI

Đã cover:

- `/v3/api-docs/identity` có chứa auth endpoints
- các endpoint public như `register` và `login` không bị gắn bearer requirement ở operation level
- schema chính có các field quan trọng cho `RegisterRequest`, `AuthTokenResponse`, `AuthenticatedUserSummary`
- example quan trọng của `RegisterRequest` như `email`, `password`, `nationality`
- summary/description của một số operation auth quan trọng

### Persistence / Rotation Behavior

Đã cover:

- refresh thành công tạo session mới và revoke session cũ đúng ở DB
- logout idempotent theo behavior hiện tại
- reset password revoke toàn bộ active refresh sessions
- `RefreshTokenSessionService` xử lý:
  - require active session
  - reject revoked session
  - reject expired session
  - migrate legacy raw token row sang hashed token
  - atomic revoke
  - expired-session cleanup

### Fail-Open Behavior

Đã cover:

- khi rate-limit backend lỗi nhưng `failOpen=true`, login không bị gãy hệ thống
- khi rate-limit backend lỗi nhưng `failOpen=true`, password reset request vẫn trả generic success

---

## Những Gì Chưa Cover

Nếu muốn gọi bộ test là production-grade hoàn chỉnh, hiện vẫn còn thiếu các nhóm sau.

### 1. Security Edge Cases Còn Lại

Cần thêm:

- protected-request security matrix sâu hơn nữa ngoài các case đã có như malformed token, invalid signature, missing type claim, unknown user
- verify chi tiết hơn sự khác biệt response giữa `NOT_FOUND`, `REVOKED`, `EXPIRED`, `INVALID`

### 2. Validation Matrix Còn Thiếu

Cần thêm:

- request body null hoặc thiếu field bắt buộc ở từng endpoint
- matrix đầy đủ hơn cho toàn bộ DTO auth thay vì chỉ các case chính

### 3. Contract Consistency

Cần thêm:

- xác nhận toàn bộ success endpoint đều đi qua wrapper đúng shape
- xác nhận đồng đều `statusCode`, `timestamp`, `path`, `requestId` ở toàn bộ error response quan trọng

### 4. Swagger Quality

Cần thêm:

- verify schema request/response chi tiết hơn
- verify example field và description field trong OpenAPI
- verify toàn bộ auth endpoints đều nằm đúng group mong muốn

### 5. Resilience Và Quan Sát Hệ Thống

Cần thêm:

- metrics assertions cho rate limit nếu team cần quan sát production chặt hơn
- logging assertions nếu muốn lock chặt contract vận hành

---

## Mức Đánh Giá Hiện Tại

Hiện bộ test auth API đang ở mức:

- đủ tốt cho auth API v1
- đủ để bắt regression chính của contract HTTP
- đủ để bảo vệ các luồng register, login, refresh, logout, reset password cơ bản
- đã cover được phần lớn edge case quan trọng của auth v1

Nhưng vẫn chưa phải production-grade hoàn chỉnh vì:

- chưa verify sâu toàn bộ OpenAPI schema
- chưa phủ sâu hơn protected-request security matrix
- chưa có metrics và logging assertions phục vụ vận hành

---

## Lệnh Test Hiện Đang Dùng

### Module identity

```powershell
.\gradlew.bat :module-identity:test
```

### Bộ test auth và web liên quan

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest --tests com.example.appbootstrap.AuthControllerRateLimitIntegrationTest --tests com.example.appbootstrap.AuthControllerRateLimitFailOpenIntegrationTest --tests com.example.appbootstrap.ClientKeyResolverIntegrationTest --tests com.example.appbootstrap.JwtAuthenticationFilterIntegrationTest --tests com.example.appbootstrap.WebInfrastructureIntegrationTest --tests com.example.appbootstrap.config.RedisRateLimitStoreTest
```

---

## Thứ Tự Ưu Tiên Nên Viết Tiếp

Nếu tiếp tục mở rộng test suite, nên ưu tiên theo thứ tự:

1. protected-request security matrix sâu hơn
2. contract consistency cho toàn bộ success và error endpoint
3. OpenAPI schema assertions chi tiết
4. metrics và logging assertions nếu team thực sự cần
