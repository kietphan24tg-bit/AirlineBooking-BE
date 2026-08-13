# Bản đồ intent chatbot sang RAG và API

Tài liệu này dùng cho chatbot/router để quyết định khi nào chỉ trả lời bằng RAG và khi nào cần gọi API. Không dùng file này như nội dung trả lời trực tiếp cho khách hàng.

Nguyên tắc chính:

- Dữ liệu tĩnh hoặc chính sách chung dùng RAG.
- Dữ liệu động theo booking, payment, inventory, ghế, dịch vụ, check-in phải gọi API.
- Các thao tác tạo, sửa, hủy, confirm, thanh toán phải có xác nhận rõ từ user trước khi gọi.
- Nếu thiếu dữ liệu bắt buộc, chatbot phải hỏi thêm hoặc hướng user vào màn hình phù hợp.
- Nếu API trả lỗi, chatbot dùng `error.code` và `error.message`, không tự đoán kết quả.

## Intent chỉ dùng RAG

| Intent | Khi dùng | Source |
| --- | --- | --- |
| `baggage_policy` | Hỏi quy định hành lý chung, hành lý theo fare, hành lý mua thêm | `fare-passenger-and-baggage-policy.md`, `ancillary-services-policy.md` |
| `checkin_policy` | Hỏi thời gian check-in, boarding, giấy tờ tại sân bay | `checkin-and-boarding-policy.md`, `airport-operations-policy.md` |
| `seat_policy` | Hỏi loại ghế, điều kiện chọn/đổi ghế, phí ghế | `seat-selection-policy.md` |
| `payment_policy` | Hỏi phương thức thanh toán, thời hạn thanh toán, trạng thái thanh toán nói chung | `booking-and-payment-policy.md` |
| `refund_policy` | Hỏi nguyên tắc đổi/hủy/hoàn tiền nói chung | `change-cancellation-and-refund-policy.md` |
| `passenger_policy` | Hỏi người lớn/trẻ em/em bé, giấy tờ hành khách | `fare-passenger-and-baggage-policy.md`, `travel-documents-policy.md` |
| `promotion_policy` | Hỏi cách áp dụng khuyến mãi, điều kiện promo chung | `pricing-and-promotions-policy.md` |
| `booking_status_explanation` | Hỏi ý nghĩa trạng thái booking/payment/settlement | `booking-status-glossary.md` |

## Intent tìm chuyến bay

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `flight_search` | `GET /api/flights/search` | `tripType`, `from`, `to`, `departureDate`, passenger counts; `returnDate` nếu khứ hồi | Không bắt buộc | Không |
| `flight_calendar_search` | `GET /api/flights/search-calendar` | `from`, `to`, `departureDate`, passenger counts | Không bắt buộc | Không |
| `fare_family_catalog` | `GET /api/flights/fare-families` | optional `countryCode`, `currency` | Không | Không |
| `fare_revalidate` | `POST /api/flights/revalidate-fare` | `flightId`, `fareId`, passenger counts, promo nếu có | Không | Có, nếu dùng để tiếp tục đặt vé |
| `itinerary_revalidate` | `POST /api/flights/revalidate-itinerary` | danh sách flight/fare theo itinerary, passenger counts | Không | Có, nếu dùng để tiếp tục đặt vé |
| `promotion_list` | `GET /api/flights/promotions` | Không | Không | Không |
| `seat_map_lookup` | `GET /api/flights/{flightId}/seats` | `flightId`, `fareId` | Không | Không |

## Intent sân bay

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `airport_picker` | `GET /api/airports/picker` | optional locale/timezone headers, limit | Không bắt buộc | Không |
| `airport_search` | `GET /api/airports/search` | `query` | Không | Không |
| `airport_list_grouped` | `GET /api/airports` | optional locale/timezone headers | Không bắt buộc | Không |
| `airport_grouped_by_country` | `GET /api/airports/grouped-by-country` | optional `country` | Không | Không |

## Intent booking và quản lý chuyến đi

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `booking_create` | `POST /api/bookings` | revalidate id hoặc itinerary hợp lệ, passenger info, contact info, selected services/seats nếu có | Không bắt buộc | Có |
| `booking_lookup` | `POST /api/bookings/lookup` | PNR + thông tin xác minh như họ/email/phone theo form | Không | Có |
| `my_trips` | `GET /api/bookings/my-trips` | filter optional | Có | Không |
| `trip_detail` | `GET /api/bookings/{bookingId}/trips/{flightId}` | `bookingId`, `flightId` | Có hoặc booking lookup hợp lệ | Không |
| `manage_options` | `GET /api/bookings/{bookingId}/manage-options` | `bookingId` | Có hoặc booking lookup hợp lệ | Không |
| `booking_tickets` | `GET /api/bookings/{bookingId}/tickets` | `bookingId` | Có hoặc booking lookup hợp lệ | Không |
| `booking_services_replace` | `PUT /api/bookings/{bookingId}/services` | `bookingId`, danh sách service selections | Có hoặc booking lookup hợp lệ | Có |

Ghi chú: với đổi chuyến theo từng chặng, nếu cần `ticketIds`, ưu tiên lấy từ `trip_detail` của đúng `flightId`; không lấy toàn bộ ticket của booking nhiều chặng.

Ghi chú giấy tờ hành khách:

- Nếu user hỏi chính sách giấy tờ chung, dùng `passenger_policy`.
- Nếu user hỏi thông tin giấy tờ/ngày sinh/quốc tịch đã lưu trong booking, dùng `booking_lookup` hoặc `trip_detail` sau khi xác minh booking.
- Nếu user hỏi visa, thời hạn hộ chiếu, yêu cầu nhập cảnh hoặc quá cảnh theo quốc gia, không dùng RAG để khẳng định chắc chắn nếu chưa có nguồn chính thức; fallback hoặc hướng user kiểm tra nguồn chính thức.

## Intent ghế và dịch vụ bổ sung

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `seat_assignments_replace` | `PUT /api/bookings/{bookingId}/seat-assignments` | `bookingId`, danh sách passenger/flight/seat | Có hoặc booking lookup hợp lệ | Có |
| `seat_assignment_remove` | `DELETE /api/bookings/{bookingId}/seat-assignments` | `bookingId`, passenger/flight/seat cần xóa | Có hoặc booking lookup hợp lệ | Có |
| `seat_assignment_patch` | `PATCH /api/bookings/{bookingId}/seat-assignment` | `bookingId`, passengerId, flightId, seatId/fromSeatId nếu có | Có hoặc booking lookup hợp lệ | Có |
| `seat_change_preview` | `POST /api/bookings/{bookingId}/seat-changes/preview` | `bookingId`, danh sách thay đổi ghế | Có hoặc booking lookup hợp lệ | Có |
| `seat_change_confirm` | `POST /api/bookings/{bookingId}/seat-changes/confirm` | preview token, idempotency key, expected amounts | Có hoặc booking lookup hợp lệ | Có |
| `service_catalog` | `GET /api/services` | optional filters | Không | Không |
| `service_change_preview` | `POST /api/bookings/{bookingId}/service-changes/preview` | `bookingId`, selections theo passenger/flight/service | Có hoặc booking lookup hợp lệ | Có |
| `service_change_confirm` | `POST /api/bookings/{bookingId}/service-changes/confirm` | preview token, idempotency key, expected amounts | Có hoặc booking lookup hợp lệ | Có |

Ghi chú cho Manage Booking:

- Nếu user chỉ hỏi chính sách chọn ghế, dùng `seat_policy`.
- Nếu user hỏi ghế hiện tại, ghế còn trống, hoặc quyền chọn ghế của booking, gọi `manage_options`, `trip_detail` hoặc `seat_map_lookup`.
- Nếu user muốn đổi ghế trong Manage Booking và có thể phát sinh chênh lệch tiền, ưu tiên `seat_change_preview` trước, trình bày số tiền thu thêm/hoàn lại, rồi mới gọi `seat_change_confirm` sau khi user xác nhận.
- Nếu `seat_change_confirm` trả settlement cần thu thêm tiền, dùng `seat_change_payment_create`; không tự xác nhận đổi ghế khi payment chưa hoàn tất.
- Không tự quy đổi phụ phí ghế VND sang ngoại tệ. Với booking không dùng VND, phải dựa vào kết quả API.
- Không dùng API đổi ghế trực tiếp để lách bước preview/confirm khi thao tác nằm trong Manage Booking và có yếu tố tiền.

## Intent thanh toán

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `payment_methods` | `GET /api/payment-methods` | optional bookingId, onlySupported | Không bắt buộc | Không |
| `booking_payment_create` | `POST /api/payments` | `bookingId`, `method` | Có hoặc booking lookup hợp lệ | Có |
| `payment_detail` | `GET /api/payments/{paymentId}` | `paymentId` | Có hoặc booking lookup hợp lệ | Không |
| `payment_result_status` | `GET /api/payments/{paymentId}/result-status` | `paymentId` | Có hoặc booking lookup hợp lệ | Không |
| `payment_result_summary` | `GET /api/payments/{paymentId}/result-summary` | `paymentId` | Có hoặc booking lookup hợp lệ | Không |
| `payment_cancel` | `POST /api/payments/{paymentId}/cancel` | `paymentId`, reason nếu có | Có hoặc booking lookup hợp lệ | Có |
| `seat_change_payment_create` | `POST /api/seat-change-payments` | `bookingId`, `seatChangeSettlementId`, `method` | Có hoặc booking lookup hợp lệ | Có |
| `service_change_payment_create` | `POST /api/service-change-payments` | `bookingId`, `serviceChangeSettlementId`, `method` | Có hoặc booking lookup hợp lệ | Có |
| `flight_change_payment_create` | `POST /api/flight-change-payments` | `bookingId`, `flightChangeSettlementId`, `method` | Có hoặc booking lookup hợp lệ | Có |

## Intent check-in

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `checkin_continue` | `POST /api/check-in/continue` | booking/PNR hoặc trip context, passenger/flight context theo request | Có hoặc booking lookup hợp lệ | Có |
| `checkin_confirm` | `POST /api/check-in/confirm` | check-in context đầy đủ, passenger/flight/seat nếu cần | Có hoặc booking lookup hợp lệ | Có |

## Intent đổi chuyến, hủy vé và refund

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `flight_change_quote` | `POST /api/bookings/{bookingId}/flight-change/quote` | `bookingId`, `ticketIds` của đúng flight hiện tại, `currentFlightId`, `newFlightId`, `newFareId` | Có hoặc booking lookup hợp lệ | Có |
| `flight_change_confirm` | `POST /api/bookings/{bookingId}/flight-changes/confirm` | `quoteToken`, `idempotencyKey`, expected additional/refund/net amounts | Có hoặc booking lookup hợp lệ | Có |
| `cancellation_preview` | `POST /api/bookings/{bookingId}/cancellations/preview` | `bookingId`, `ticketIds` | Có hoặc booking lookup hợp lệ | Có |
| `cancellation_confirm` | `POST /api/bookings/{bookingId}/cancellations` | `bookingId`, `ticketIds` hoặc selection theo request hiện hành | Có hoặc booking lookup hợp lệ | Có |

## Intent tài khoản và xác thực

| Intent | API | Required context | Auth | Xác nhận trước khi gọi |
| --- | --- | --- | --- | --- |
| `auth_register` | `POST /api/auth/register` | email, password, tên, điều khoản, phone/nationality nếu có | Không | Có |
| `auth_login` | `POST /api/auth/login` | email, password | Không | Có |
| `auth_refresh` | `POST /api/auth/refresh` | refresh cookie hợp lệ | Không trực tiếp từ user | Không |
| `auth_logout` | `POST /api/auth/logout` | refresh cookie nếu có | Không bắt buộc | Có |
| `password_reset_request` | `POST /api/auth/password-reset/request` | email | Không | Có |
| `password_reset_confirm` | `POST /api/auth/password-reset/confirm` | OTP, mật khẩu mới | Không | Có |
| `current_user_profile` | `GET /api/users/me` | access token | Có | Không |
| `current_user_update` | `PATCH /api/users/me` | fields cần cập nhật | Có | Có |
| `current_user_change_password` | `POST /api/users/me/password` | current password, new password | Có | Có |
| `account_overview` | `GET /api/account/overview` | access token | Có | Không |

Không lưu password, OTP, token, refresh cookie vào RAG memory hoặc log hội thoại. Nếu user nhập thông tin nhạy cảm trong chat, chỉ dùng cho request tức thời nếu flow được thiết kế cho việc đó.

## API không dành cho chatbot/user

| API | Lý do |
| --- | --- |
| `POST /api/payments/webhooks/payos` | Chỉ provider PayOS gọi, cần xác thực webhook |
| `POST /api/payments/webhooks/stripe` | Chỉ Stripe gọi, cần header `Stripe-Signature` |

Chatbot không được tự gọi webhook, không mô phỏng webhook để cập nhật trạng thái thanh toán. Muốn kiểm tra trạng thái thì dùng payment detail/result status API.

## Error handling bắt buộc

| Error code | Cách phản hồi |
| --- | --- |
| `CHANGE_WINDOW_CLOSED` | Báo user đã quá hạn đổi chuyến/hủy theo policy, không gợi ý thử lại cùng thao tác. |
| `INVALID_FLIGHT_CHANGE_ROUTE` | Báo flight mới không hợp lệ cho route hiện tại; yêu cầu chọn chuyến cùng điểm đi/đến và khác chuyến cũ. |
| `CONNECTION_INVALID` | Báo đổi chuyến sẽ làm hỏng nối chuyến hoặc không đủ thời gian nối chuyến; yêu cầu chọn lịch khác. |
| `SEAT_UNAVAILABLE` | Báo chuyến/fare không còn đủ chỗ; yêu cầu chọn chuyến/fare khác. |
| `FARE_NOT_CHANGEABLE` | Báo fare hiện tại không cho đổi chuyến; không tự tạo quote khác. |
| `PASSENGER_RULE_VIOLATION` | Báo selection hành khách không hợp lệ, ví dụ infant phải đi cùng adult. |
| `FLIGHT_CHANGE_QUOTE_EXPIRED` | Báo báo giá đã hết hạn; yêu cầu tạo quote mới trước khi confirm. |
| `FLIGHT_CHANGE_QUOTE_STALE` | Báo giá tiền đã thay đổi; yêu cầu user xem lại quote mới. |
| `BOOKING_CHANGE_PAYMENT_ALREADY_STARTED` | Báo booking đang có giao dịch thay đổi chờ thanh toán; yêu cầu hoàn tất/hủy giao dịch trước. |
| `PAYMENT_HOLD_WINDOW_EXPIRED` | Báo đã hết thời hạn giữ thanh toán, không tạo payment mới. |
| `BOOKING_NOT_PAYABLE` | Báo booking không còn ở trạng thái thanh toán được. |
| `BOOKING_PAYMENT_ALREADY_STARTED` | Báo booking đang có thanh toán chờ xử lý; không đổi ghế/dịch vụ để tránh lệch tổng tiền. |
| `SEAT_NOT_ALLOWED` | Báo ghế không hợp lệ cho booking/hạng vé/hành khách; không tự chọn ghế khác nếu user chưa xác nhận. |
| `SEAT_PAYMENT_REQUIRED` | Báo ghế mới phát sinh thêm phí và cần luồng thanh toán phù hợp; không xác nhận đổi ghế nếu payment chưa xong. |
| `SEAT_SURCHARGE_CURRENCY_UNSUPPORTED` | Báo phụ phí ghế hiện không hỗ trợ loại tiền tệ của booking; không tự quy đổi sang VND hoặc ngoại tệ khác. |
| `SEAT_CHANGE_STALE` hoặc `SEAT_CHANGE_PREVIEW_STALE` | Báo thông tin ghế/giá đã thay đổi; yêu cầu tải lại sơ đồ ghế hoặc tạo preview mới. |
| `CANCELLATION_WINDOW_CLOSED` | Báo đã quá hạn hủy/hoàn theo policy. |
| `PAYMENT_NOT_FOUND` | Báo không tìm thấy giao dịch, yêu cầu kiểm tra lại mã thanh toán hoặc booking. |
| `UNAUTHORIZED` hoặc `FORBIDDEN` | Yêu cầu đăng nhập hoặc xác minh booking trước khi xem/thao tác dữ liệu cá nhân. |

## Quy tắc an toàn khi gọi API

- Các intent confirm hoặc create payment phải có xác nhận cuối cùng từ user.
- Với preview/quote, chatbot phải trình bày kết quả cho user trước khi confirm.
- Với các thao tác có tiền, chatbot phải nói rõ số tiền thu thêm hoặc hoàn trước khi gọi confirm/payment.
- Không được tự chọn phương thức thanh toán nếu user chưa chọn.
- Không được dùng dữ liệu RAG để trả lời số tiền, số ghế còn lại, trạng thái booking/payment/check-in hiện tại.
- Khi thiếu booking context, hỏi PNR/thông tin xác minh hoặc yêu cầu user đăng nhập.
- Nếu user hỏi chính sách chung, trả lời bằng RAG và không gọi API.
