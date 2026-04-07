# Module Design Guide

Tài liệu này chốt convention thiết kế module cho repo `airline-booking`.

Mục tiêu:

- giữ codebase dễ đọc, dễ onboard
- không ép mọi module phải giống nhau 100%
- nhưng vẫn có một bộ nguyên tắc thống nhất để tránh mỗi nơi một kiểu

---

## 1. Nguyên tắc tổng quát

Repo này là `modular monolith`, không phải microservices và cũng chưa phải clean architecture đầy đủ.

Vì vậy, mục tiêu thực tế là:

- boundary rõ ở những module nhạy cảm
- pragmatic ở những module đơn giản
- đồng bộ về nguyên tắc, không cần đồng bộ tuyệt đối về hình thức

Nói ngắn gọn:

- không "thích sao làm vậy"
- cũng không "ép mọi module full clean architecture"

---

## 2. Đồng bộ cái gì, không đồng bộ cái gì

### 2.1. Cần đồng bộ

Cần đồng bộ về:

- cách đặt tên package
- cách xác định business flow nằm ở đâu
- cách xác định khi nào cần `port`
- cách tách module nhạy cảm và module đơn giản
- vị trí đặt `RestController`

### 2.2. Không cần đồng bộ tuyệt đối

Không cần ép mọi module đều có:

- `application`
- `domain`
- `port`
- `adapter`
- `mapper`
- `dto`
- `policy`

Lý do:

- module nhỏ sẽ bị over-engineer
- code nhiều hình thức hơn giá trị
- khó maintain và khó onboard hơn

---

## 3. Rule chung cho toàn repo

### Rule 1: Business flow nằm trong module business

Business logic không đặt trong `app-bootstrap`.

Module business phải giữ:

- entity
- repository
- application/service nghiệp vụ

`app-bootstrap` chỉ nên giữ:

- web layer
- security filter
- config
- adapter implementation

### Rule 2: `RestController` ưu tiên đặt trong `app-bootstrap`

Nếu muốn boundary rõ:

- `RestController` nằm ở `app-bootstrap`
- module business không biết HTTP request/response

Điều này giúp:

- web layer tập trung một chỗ
- business module sạch hơn
- dễ đổi web adapter sau này

### Rule 3: Chỉ dùng `port` khi có lý do rõ ràng

Không phải interface nào cũng là `port`.

Chỉ nên tạo `port` khi module cần một năng lực từ bên ngoài, ví dụ:

- tạo/kiểm tra token
- đọc IP/client context từ request
- rate limit store
- provider thanh toán
- event publisher
- notification sender

Nếu một abstraction chỉ là logic nội bộ, không phải boundary, thì không cần đưa vào `port`.

### Rule 4: Module nhạy cảm thì boundary chặt hơn

Những module có tính chất:

- security-sensitive
- external integration nhiều
- có khả năng đổi implementation
- cần test độc lập tốt

thì nên có:

- `application`
- `domain` hoặc `domain.security`
- `port`
- adapter bên ngoài module

### Rule 5: Module đơn giản thì pragmatic

Nếu module:

- chủ yếu query/nội bộ
- ít phụ thuộc hạ tầng ngoài
- luồng nghiệp vụ đơn giản

thì có thể chỉ cần:

- `entity`
- `repository`
- `application` hoặc `service`

không cần ép thêm `port`.

---

## 4. Các cấp độ module trong repo

Để codebase nhất quán, repo này nên chia module theo 3 cấp độ.

### 4.1. Cấp A: Strict Module

Dùng cho module nhạy cảm, cần boundary rõ.

Structure gợi ý:

```text
module-x/
  entity/
  repository/
  application/
  domain/
  port/
```

Dấu hiệu:

- có phụ thuộc ra JWT/Redis/provider/request context
- có logic security hoặc money flow
- có khả năng đổi adapter

Nên dùng cho:

- `module-identity`
- `module-payment`

### 4.2. Cấp B: Standard Module

Dùng cho module nghiệp vụ lớn, nhưng chưa cần boundary rất chặt.

Structure gợi ý:

```text
module-x/
  entity/
  repository/
  application/
```

Chỉ thêm `port` nếu phát sinh integration thật sự.

Nên dùng cho:

- `module-booking`

### 4.3. Cấp C: Pragmatic Module

Dùng cho module đơn giản hơn, ưu tiên tốc độ và độ dễ đọc.

Structure gợi ý:

```text
module-x/
  entity/
  repository/
  service/ or application/
```

Không ép tách `port` nếu chưa cần.

Nên dùng cho:

- `module-flight`
- `module-checkin`
- `module-notification`
- `module-scheduler`

---

## 5. Quy tắc chọn cấp độ module

Dùng bảng này để quyết định.

| Câu hỏi | Nếu có | Nếu không |
| --- | --- | --- |
| Module có dùng external infrastructure không? | Cân nhắc `port` | Có thể giữ pragmatic |
| Implementation có khả năng thay đổi không? | Cân nhắc `port` | Không cần abstraction sớm |
| Module có nhạy cảm về security/money không? | Nên stricter boundary | Có thể đơn giản hơn |
| Cần test độc lập với adapter không? | Dùng `port` hợp lý | Mock trực tiếp / không cần |
| Flow nghiệp vụ có lớn và sống lâu không? | Nên tăng kỷ luật package | Giữ gọn, dễ đọc |

---

## 6. Áp dụng cụ thể cho repo này

### 6.1. `module-identity`

Cấp độ:

- `Strict Module`

Lý do:

- dùng JWT
- dùng Redis rate limit
- dùng request client context
- là module security-sensitive

Nên giữ:

- `application`
- `domain.security`
- `port`
- `application.security`

Không nên:

- đưa `RestController` vào đây
- để service auth gọi trực tiếp Redis/JWT/request

### 6.2. `module-payment`

Cấp độ:

- `Strict Module`

Lý do:

- sẽ dùng payment provider
- có callback/webhook/signature
- có rủi ro nghiệp vụ và tài chính

Gợi ý:

- `application`
- `port`
- `entity`
- `repository`

Port có thể xuất hiện sau:

- `PaymentGatewayClient`
- `PaymentSignatureVerifier`
- `PaymentEventPublisher`

### 6.3. `module-booking`

Cấp độ:

- `Standard Module`

Lý do:

- là module nghiệp vụ lớn
- nhiều flow business
- nhưng không cần ép abstraction mức auth/payment ngay lập tức

Nên có:

- `entity`
- `repository`
- `application`

Chỉ thêm `port` nếu:

- cần gọi payment
- cần publish event
- cần gọi inventory/seat lock bên ngoài

### 6.4. `module-flight`

Cấp độ:

- `Pragmatic Module`

Lý do:

- thường query-heavy
- read-oriented
- ít external dependency hơn

Nên có:

- `entity`
- `repository`
- `application` hoặc `service`

Không cần ép `port` sớm.

### 6.5. `module-checkin`

Cấp độ:

- `Pragmatic Module`

Nếu sau này có:

- QR integration
- external boarding system
- security-sensitive workflow

thì có thể nâng cấp lên `Standard` hoặc `Strict`.

### 6.6. `module-notification`

Cấp độ hiện tại:

- `Pragmatic Module`

Nếu sau này có:

- email provider
- SMS provider
- push notification provider

thì có thể cần `port` cho sender implementation.

### 6.7. `module-scheduler`

Cấp độ:

- `Pragmatic Module`

Lý do:

- thường chỉ orchestration/background jobs
- không cần ceremony lớn nếu workflow chưa phức tạp

---

## 7. Vị trí đặt controller

Convention hiện tại:

- `RestController` đặt trong `app-bootstrap`

Lý do:

- `app-bootstrap` là web app duy nhất
- gom web layer một chỗ để dễ quan sát
- module business không phải biết HTTP

Gợi ý package:

```text
app-bootstrap/src/main/java/com/example/appbootstrap/controller/identity/
app-bootstrap/src/main/java/com/example/appbootstrap/controller/booking/
app-bootstrap/src/main/java/com/example/appbootstrap/controller/flight/
```

Nếu sau này có lý do rất rõ ràng mới đưa controller vào module business, và phải được thống nhất trước.

---

## 8. Khi nào tạo `port`

Tạo `port` khi có đủ một trong các dấu hiệu sau:

- module cần gọi ra infrastructure bên ngoài
- implementation có thể thay đổi
- logic nghiệp vụ không nên biết công nghệ cụ thể
- cần test business flow mà không muốn boot adapter thật

Ví dụ nên là `port`:

- token service
- request client resolver
- rate limit store
- payment provider
- notification sender
- event publisher

Ví dụ chưa cần thành `port`:

- helper logic nội bộ
- validator nhỏ chỉ dùng trong module
- utility có thuật toán cố định và không phải boundary

---

## 9. Khi nào không nên tạo `port`

Không nên tạo `port` nếu:

- interface chỉ tồn tại để "cho đẹp kiến trúc"
- không có khả năng đổi implementation
- module quá nhỏ
- abstraction làm code khó đọc hơn

Dấu hiệu over-engineer:

- 1 interface + 1 implementation + không có lý do boundary rõ
- người mới đọc code mất nhiều thời gian lần theo
- module đơn giản mà phải đi qua quá nhiều lớp

---

## 10. Hướng dẫn đặt package

### 10.1. Strict Module

```text
module-x/
  entity/
  repository/
  application/
  domain/
  port/
```

### 10.2. Standard Module

```text
module-x/
  entity/
  repository/
  application/
```

### 10.3. Pragmatic Module

```text
module-x/
  entity/
  repository/
  service/ or application/
```

---

## 11. Cách ra quyết định khi tạo code mới

Khi thêm code mới, tự hỏi theo thứ tự sau:

1. Đây có phải business flow chính không?
   - Nếu có: đặt vào `application`

2. Đây có phải rule/policy/security utility nội bộ không?
   - Nếu có: đặt vào `domain` hoặc `domain.security`

3. Đây có phải nhu cầu từ bên ngoài module không?
   - Nếu có: đặt interface vào `port`

4. Đây có phải implementation công nghệ cụ thể không?
   - Nếu có: đặt ở `app-bootstrap` hoặc adapter layer

5. Đây có phải chỉ là persistence không?
   - Nếu có: đặt vào `repository`

---

## 12. Kết luận

Repo này không theo hướng "mọi module phải giống nhau 100%".

Thay vào đó:

- đồng bộ về nguyên tắc
- linh hoạt về mức độ ceremony
- auth và payment chặt hơn
- booking vừa phải
- flight/checkin/notification/scheduler pragmatic hơn

Convention chốt:

- `module-identity`: strict
- `module-payment`: strict
- `module-booking`: standard
- `module-flight`: pragmatic
- `module-checkin`: pragmatic
- `module-notification`: pragmatic, có thể nâng cấp sau
- `module-scheduler`: pragmatic

Nếu giữ đúng bộ quy tắc này, codebase sẽ:

- dễ onboard
- dễ review
- dễ mở rộng
- không bị over-engineer không cần thiết
