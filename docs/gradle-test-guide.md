# Hướng Dẫn Chạy Test Với Gradle

## Mục tiêu

Tài liệu này giải thích:

- lệnh test Gradle trong repo này có ý nghĩa gì
- lệnh đó sẽ chạy module nào, phần nào
- log thường hiện những gì
- cách nhận biết test thành công hay lỗi

Repo hiện tại là `multi-module Gradle project`, nên hiểu đúng `module` và `task` là rất quan trọng.

---

## Cấu Trúc Cơ Bản Của Lệnh

Ví dụ:

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest
```

Lệnh này gồm 3 phần:

- `.\gradlew.bat`
  - chạy Gradle Wrapper của repo
- `:app-bootstrap:test`
  - chạy task `test` của module `app-bootstrap`
- `--tests com.example.appbootstrap.AuthControllerIntegrationTest`
  - chỉ chạy test class này trong module đó

---

## Ý Nghĩa Của `:module:task`

### Ví dụ 1

```powershell
.\gradlew.bat :app-bootstrap:test
```

Ý nghĩa:

- chạy task `test` của module `app-bootstrap`
- chỉ chạy test trong:
  - `app-bootstrap/src/test/java`

### Ví dụ 2

```powershell
.\gradlew.bat :module-identity:test
```

Ý nghĩa:

- chạy task `test` của module `module-identity`
- chỉ chạy test trong:
  - `module-identity/src/test/java`

### Ví dụ 3

```powershell
.\gradlew.bat test
```

Ý nghĩa:

- chạy task `test` của toàn bộ project
- module nào có task test thì sẽ được chạy

---

## Khi Có `--tests`

`--tests` dùng để filter test theo class hoặc method.

### Chạy một class

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest
```

Chỉ chạy:

- [AuthControllerIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerIntegrationTest.java)

### Chạy nhiều class

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest --tests com.example.appbootstrap.JwtAuthenticationFilterIntegrationTest
```

Chỉ chạy các class khớp 2 pattern này.

### Chạy một method cụ thể

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest.shouldLoginRefreshAndLogoutSuccessfully
```

Chỉ chạy đúng method đó.

---

## Lệnh Test Sẽ Chạy Những Phần Nào

### `:app-bootstrap:test`

Lệnh này:

- chạy test trong `app-bootstrap/src/test/java`
- compile code chính của `app-bootstrap`
- compile test code của `app-bootstrap`
- compile các module mà `app-bootstrap` đang phụ thuộc nếu cần
- process resources
- chạy JUnit tests
- sinh test report

Nhưng nó **không chạy test của các module khác** như:

- `module-identity`
- `module-flight`
- `module-booking`

trừ khi bạn gọi task test của chúng.

### `:module-identity:test`

Lệnh này:

- chạy test trong `module-identity/src/test/java`
- không chạy test của `app-bootstrap`

---

## Vì Sao Chạy `app-bootstrap:test` Mà Vẫn Thấy Log Compile Của Module Khác

Ví dụ log:

```text
> Task :common:compileJava UP-TO-DATE
> Task :module-identity:compileJava UP-TO-DATE
> Task :app-bootstrap:compileJava
> Task :app-bootstrap:test
```

Điều này không có nghĩa là Gradle đang chạy test của `module-identity`.

Nó chỉ có nghĩa là:

- `app-bootstrap` phụ thuộc vào `module-identity`
- để test `app-bootstrap`, Gradle phải chắc rằng code phụ thuộc đã được compile

Tóm lại:

- compile phụ thuộc khác: có thể có
- chạy test phụ thuộc khác: không, trừ khi gọi task test của module đó

---

## Các Lệnh Test Hay Dùng Trong Repo

### 1. Chạy toàn bộ test của `app-bootstrap`

```powershell
.\gradlew.bat :app-bootstrap:test
```

### 2. Chạy toàn bộ test của `module-identity`

```powershell
.\gradlew.bat :module-identity:test
```

### 3. Chạy riêng auth API integration test

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest
```

### 4. Chạy nhóm test auth/web liên quan

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest --tests com.example.appbootstrap.AuthControllerRateLimitIntegrationTest --tests com.example.appbootstrap.AuthControllerRateLimitFailOpenIntegrationTest --tests com.example.appbootstrap.ClientKeyResolverIntegrationTest --tests com.example.appbootstrap.JwtAuthenticationFilterIntegrationTest --tests com.example.appbootstrap.WebInfrastructureIntegrationTest --tests com.example.appbootstrap.config.RedisRateLimitStoreTest
```

### 5. Chạy một test method

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest.shouldRegisterUserAndReturnSummary
```

### 6. Chạy toàn bộ test toàn repo

```powershell
.\gradlew.bat test
```

---

## Log Thường Hiện Những Gì

Khi chạy test, log thường có 3 lớp thông tin.

### 1. Log của Gradle task

Ví dụ:

```text
> Task :app-bootstrap:compileJava UP-TO-DATE
> Task :app-bootstrap:compileTestJava
> Task :app-bootstrap:test
```

Ý nghĩa:

- `UP-TO-DATE`
  - task không cần chạy lại vì đầu vào không đổi
- không có `UP-TO-DATE`
  - task vừa thực sự được chạy

### 2. Log của test result

Nếu có test fail, thường sẽ thấy:

```text
AuthControllerIntegrationTest > shouldLoginRefreshAndLogoutSuccessfully() FAILED
```

Hoặc:

```text
24 tests completed, 1 failed
```

### 3. Log của Spring Boot app trong integration test

Nếu test là integration test, bạn sẽ thấy thêm:

- Spring context startup
- Hibernate SQL
- Hikari datasource
- Tomcat startup/shutdown
- stack trace khi có lỗi

Ví dụ:

```text
Starting AuthControllerIntegrationTest using Java 21...
Tomcat initialized with port 0
HikariPool-1 - Starting...
```

Đây là bình thường với các test như:

- [AuthControllerIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerIntegrationTest.java)
- [JwtAuthenticationFilterIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/JwtAuthenticationFilterIntegrationTest.java)

---

## Làm Sao Biết Test Thành Công Hay Lỗi

Nhìn 4 chỗ:

### 1. Dòng cuối của Gradle

Pass:

```text
BUILD SUCCESSFUL
```

Fail:

```text
BUILD FAILED
```

### 2. Exit code của lệnh

- `0` là pass
- khác `0` là fail

### 3. Summary số lượng test

Ví dụ:

```text
24 tests completed, 1 failed
```

hoặc nếu pass thường chỉ thấy build thành công mà không có dòng fail.

### 4. HTML report

Test report thường nằm tại:

- [app-bootstrap/build/reports/tests/test/index.html](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/build/reports/tests/test/index.html)
- [module-identity/build/reports/tests/test/index.html](/d:/Workspace/CNPM/AirlineBooking/airline-booking/module-identity/build/reports/tests/test/index.html)

Đây là nơi dễ xem nhất nếu cần:

- test nào fail
- stack trace
- thời gian chạy
- tổng quan pass/fail theo class

---

## Phân Biệt Unit Test Và Integration Test

### Unit test

Thường:

- log ngắn hơn
- chạy nhanh hơn
- ít boot Spring hơn

### Integration test

Thường:

- log dài hơn
- boot Spring Boot context
- có Hibernate/JPA/Tomcat
- chậm hơn unit test

Trong repo hiện tại, các file như:

- [AuthControllerIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerIntegrationTest.java)
- [AuthControllerRateLimitIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/AuthControllerRateLimitIntegrationTest.java)
- [JwtAuthenticationFilterIntegrationTest.java](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/src/test/java/com/example/appbootstrap/JwtAuthenticationFilterIntegrationTest.java)

là integration test nên log thường nhiều hơn.

---

## Cách Đọc Log Nhanh

Khi chạy test, có thể đọc theo thứ tự này:

1. Xem cuối log có `BUILD SUCCESSFUL` hay `BUILD FAILED`
2. Nếu fail, tìm dòng `FAILED`
3. Xem tên class và method bị fail
4. Mở HTML report nếu stack trace dài hoặc khó đọc

---

## Cheat Sheet Ngắn

Chạy toàn bộ test web:

```powershell
.\gradlew.bat :app-bootstrap:test
```

Chạy toàn bộ test identity:

```powershell
.\gradlew.bat :module-identity:test
```

Chạy riêng auth API test:

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest
```

Chạy một test method:

```powershell
.\gradlew.bat :app-bootstrap:test --tests com.example.appbootstrap.AuthControllerIntegrationTest.shouldRegisterUserAndReturnSummary
```

Xem report:

- [app-bootstrap/build/reports/tests/test/index.html](/d:/Workspace/CNPM/AirlineBooking/airline-booking/app-bootstrap/build/reports/tests/test/index.html)

