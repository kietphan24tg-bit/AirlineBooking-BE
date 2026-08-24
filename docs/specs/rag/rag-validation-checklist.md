# Checklist Kiểm Thử RAG

Tài liệu này dùng để kiểm tra bộ policy docs trước khi nạp vào RAG hoặc demo chatbot.

File này là tài liệu QA/evaluation, không phải tài liệu knowledge chính cho production RAG. Không nên nạp file này vào production vector index vì chatbot có thể retrieve nhầm test case thay vì policy thật.

## Cách dùng

Với mỗi test case, ghi kết quả:

- `PASS`: chatbot trả lời đúng hoặc gọi đúng API/tool.
- `FAIL`: chatbot bịa, trả lời sai policy, không gọi API khi cần, hoặc gọi API khi chỉ cần RAG.
- `NEEDS_REVIEW`: policy chưa chốt hoặc câu trả lời phụ thuộc business decision.

Mỗi lần test nên lưu:

- Câu hỏi.
- Retrieved chunks.
- Tool/API đã gọi nếu có.
- Câu trả lời cuối.
- Kết quả pass/fail.
- Ghi chú lỗi nếu fail.

## Checklist coverage

- Booking/payment: có rule `pnrExpiresAt`, `sessionExpiresAt`, PayOS QR, Stripe, Pay Later.
- Status glossary: có booking, ticket, payment, seat assignment, settlement.
- Fare/passenger: có adult, child, infant, tỷ lệ giá, infant không có ghế riêng.
- Seat selection: có điều kiện chọn ghế, đổi ghế trong Manage Booking, phụ thu ghế VND, non-VND, infant không có ghế riêng.
- Ancillary services: có baggage, meal, insurance, priority boarding, lounge.
- Check-in: có điều kiện booking `CONFIRMED`, ticket `ISSUED`, cửa sổ domestic/international.
- Change/cancel/refund: có đổi chuyến trước 180 phút, quote 15 phút, refund preview.
- RAG scope: có phân biệt câu hỏi tĩnh và câu hỏi phải gọi API.
- Travel documents: có giấy tờ khi đặt vé, adult bắt buộc thông tin định danh, child/infant có thể optional khi đặt nhưng vẫn cần fallback an toàn, visa/nhập cảnh không bịa.
- Airport operations: không bịa boarding gate/no-show nếu policy chưa chốt.
- Pricing/promotions: có rule không tự tính giá động; phải revalidate/gọi API.
- API intent map: có mapping các intent động sang endpoint backend.

## Must-answer bằng RAG

Các câu này phải trả lời được bằng RAG, không cần gọi API.

| ID | Câu hỏi test | Expected behavior |
| --- | --- | --- |
| RAG-001 | Check-in online mở và đóng lúc nào cho chuyến nội địa? | Nêu 24 giờ trước giờ bay và đóng 40 phút trước giờ bay. |
| RAG-002 | Check-in online mở và đóng lúc nào cho chuyến quốc tế? | Nêu 48 giờ trước giờ bay và đóng 60 phút trước giờ bay. |
| RAG-003 | Booking chưa thanh toán được giữ bao lâu? | Nêu mặc định 15 phút theo `pnrExpiresAt`. |
| RAG-004 | `pnrExpiresAt` là gì? | Giải thích đây là hạn giữ booking/thanh toán. |
| RAG-005 | `sessionExpiresAt` khác `pnrExpiresAt` như thế nào? | Phân biệt hạn booking và hạn payment session. |
| RAG-006 | PayOS QR có phải luôn cùng thời hạn với booking không? | Nói QR có session riêng, không được tự đồng nhất với booking hold. |
| RAG-007 | Pay Later dùng để làm gì? | Nói đây là method giữ chỗ lâu hơn/resume nếu hợp lệ. |
| RAG-008 | Stripe card khác PayOS QR thế nào? | Nói Stripe là redirect checkout, có session riêng và cần đủ thời gian. |
| RAG-009 | Booking `PENDING_PAYMENT` nghĩa là gì? | Giải thích đã tạo nhưng chưa thanh toán thành công. |
| RAG-010 | Booking `CONFIRMED` làm được những gì? | Nêu có thể issue ticket, check-in, manage booking nếu đủ điều kiện. |
| RAG-011 | Ticket `ISSUED` nghĩa là gì? | Nêu ticket đã xuất và là điều kiện cho check-in/đổi/hủy. |
| RAG-012 | Payment `PARTIALLY_REFUNDED` nghĩa là gì? | Nêu payment đã hoàn một phần. |
| RAG-013 | Infant có được chọn ghế riêng không? | Trả lời không, infant không có ghế riêng. |
| RAG-014 | Child tính giá vé như thế nào? | Nêu 75% fare người lớn. |
| RAG-015 | Infant tính giá vé như thế nào? | Nêu 10% fare người lớn. |
| RAG-016 | Adult tính giá vé như thế nào? | Nêu 100% fare người lớn. |
| RAG-017 | Fare `PAID` khi chọn ghế nghĩa là gì? | Được chọn trước và có thể phát sinh phụ thu. |
| RAG-018 | Fare `INCLUDED` khi chọn ghế nghĩa là gì? | Được chọn trước, một số category được bao gồm miễn phí. |
| RAG-019 | Fare `CHECKIN_ONLY` nghĩa là gì? | Chỉ được chọn ghế trong flow check-in. |
| RAG-020 | Fare `NOT_ALLOWED` nghĩa là gì? | Không được chọn ghế trước. |
| RAG-021 | Ghế `STANDARD` có phụ thu cơ bản bao nhiêu? | Nêu 0 VND theo seed hiện tại. |
| RAG-022 | Ghế `PREFERRED` có phụ thu cơ bản bao nhiêu? | Nêu 40,000 VND theo seed hiện tại. |
| RAG-023 | Ghế `EXIT_ROW` có phụ thu cơ bản bao nhiêu? | Nêu 120,000 VND theo seed hiện tại. |
| RAG-024 | Ghế `EXTRA_LEGROOM` có phụ thu cơ bản bao nhiêu? | Nêu 180,000 VND theo seed hiện tại. |
| RAG-025 | Seat type `WINDOW` và `AISLE` có phụ thu không? | Nêu 20,000 VND theo seed hiện tại. |
| RAG-026 | Có các nhóm dịch vụ bổ sung nào? | Nêu `BAGGAGE`, `MEAL`, `INSURANCE`, `PRIORITY_BOARDING`, `LOUNGE`. |
| RAG-027 | Có những gói hành lý mua thêm nào? | Nêu Extra 15/20/25/30/40kg và giá seed. |
| RAG-028 | Có những suất ăn đặt trước nào? | Nêu các meal option seed hiện tại. |
| RAG-029 | Nếu fare đã có lounge thì có nên mua thêm lounge không? | Nói không cần và backend có thể chặn mua trùng quyền lợi. |
| RAG-030 | Nếu fare đã có priority boarding thì có nên mua thêm không? | Nói không cần và backend có thể chặn mua trùng quyền lợi. |
| RAG-031 | Khi nào user không được check-in? | Nêu booking chưa confirmed, ticket chưa issued, ngoài cửa sổ, chưa có ghế, đã check-in. |
| RAG-032 | Đổi chuyến bay bị khóa trước giờ bay bao lâu? | Nêu 180 phút. |
| RAG-033 | Quote đổi chuyến có hạn bao lâu? | Nêu 15 phút. |
| RAG-034 | Itinerary nối chuyến cần layover tối thiểu bao lâu? | Nêu 90 phút. |
| RAG-035 | Infant khi đổi chuyến có rule gì? | Nêu infant phải đi cùng ít nhất một adult trong thay đổi. |
| RAG-036 | Refund gồm những thành phần nào? | Nêu fare gross, promo allocated, fare net, seat refund, service refund, refund fee, net refund. |
| RAG-037 | Net refund được tính thế nào? | Nêu công thức từ snapshot: fare net + seat refund + service refund - refund fee; không tự tính số tiền cụ thể nếu chưa gọi preview API. |
| RAG-038 | Stripe refund thường mất bao lâu? | Nêu 5-10 ngày làm việc theo provider estimate, thực tế phụ thuộc ngân hàng phát hành thẻ. |
| RAG-039 | PayOS refund thường mất bao lâu? | Nêu yêu cầu hoàn tiền được ghi nhận và xử lý 24/7, thời gian tiền về thực tế phụ thuộc ngân hàng nhận. |
| RAG-040 | Mã khuyến mãi không áp dụng trong những trường hợp nào? | Nêu not found, disabled, not active yet, expired, usage limit, min amount, scope không khớp. |
| RAG-041 | Giá vé có phụ thuộc currency và tax/fee không? | Nói có, và giá cuối phải lấy từ backend. |
| RAG-042 | Hệ thống hiện đã chốt rule passport/visa chưa? | Nói chưa chốt chi tiết; visa/entry requirement cần nguồn chính thức. |
| RAG-043 | Tôi đổi ghế trong Manage Booking được không? | Nêu được nếu booking còn hợp lệ; ghế mới phải đúng chuyến, đúng hạng vé, còn trống, và cần kiểm tra booking cụ thể. |
| RAG-044 | Phí chọn ghế có tự đổi sang USD được không? | Trả lời không; phí tham chiếu đang là VND, không tự quy đổi ngoại tệ. |
| RAG-045 | Booking không dùng VND có chọn ghế mất phí được không? | Nói phụ phí ghế hỗ trợ chắc chắn cho VND; booking tiền tệ khác cần kiểm tra hệ thống và có thể bị từ chối nếu phát sinh phụ phí. |
| RAG-046 | Booking đã xác nhận có đổi sang ghế đắt hơn được không? | Nói hiện có thể bị từ chối nếu hệ thống chưa hỗ trợ thu thêm phụ phí; cần kiểm tra booking/luồng thanh toán. |
| RAG-047 | Khi đặt vé người lớn cần nhập giấy tờ gì? | Nêu người lớn cần quốc tịch, loại giấy tờ và số giấy tờ. |
| RAG-048 | Trẻ em/em bé không nhập giấy tờ lúc đặt vé thì có chắc không cần giấy tờ ở sân bay không? | Trả lời không; vẫn nên mang giấy tờ hợp lệ và kiểm tra nguồn chính thức nếu cần. |
| RAG-049 | Hệ thống hỗ trợ loại giấy tờ nào khi lưu thông tin hành khách? | Nêu giấy tờ tùy thân trong nước và hộ chiếu. |

## Must-call-API

Các câu này không được trả lời bằng RAG thuần. Bot phải gọi API/tool hoặc yêu cầu thêm thông tin để gọi API.

| ID | Câu hỏi test | Expected behavior |
| --- | --- | --- |
| API-001 | Booking ABC123 của tôi đã thanh toán chưa? | Gọi booking lookup/payment API hoặc yêu cầu thông tin xác thực. |
| API-002 | Booking của tôi có còn hạn không? | Gọi API để kiểm tra `pnrExpiresAt`. |
| API-003 | QR thanh toán của tôi còn bao lâu? | Gọi payment API để lấy `sessionExpiresAt`/`expireAt`. |
| API-004 | Payment 123 đang trạng thái nào? | Gọi `GET /api/payments/{paymentId}` hoặc result-status. |
| API-005 | Tôi có thể dùng Stripe cho booking này không? | Gọi payment methods API vì phụ thuộc booking/currency/time/config. |
| API-006 | Ghế 12A trên chuyến VN123 còn trống không? | Gọi seat map API. |
| API-007 | Chuyến bay SGN-HAN ngày mai còn ghế không? | Gọi flight search API. |
| API-008 | Fare này còn giữ được không? | Gọi revalidate fare/itinerary API. |
| API-009 | Tổng tiền booking sau khi chọn ghế là bao nhiêu? | Gọi booking/seat change preview hoặc payment summary API. |
| API-010 | Tôi đã mua thêm hành lý chưa? | Gọi booking/trip/manage options API. |
| API-011 | Tôi có mua thêm lounge cho booking này được không? | Gọi manage/service preview API vì phụ thuộc fare và booking. |
| API-012 | Boarding pass của tôi đâu? | Gọi check-in/ticket/boarding pass API. |
| API-013 | Tôi check-in được chưa? | Gọi check-in continue API vì phụ thuộc flight/time/seat/ticket. |
| API-014 | Vé của tôi hoàn được bao nhiêu tiền? | Gọi cancellation preview API. |
| API-015 | Booking đã check-in rồi có hủy được không? | Gọi manage/cancellation preview API hoặc kiểm tra status. |
| API-016 | Tôi đổi sang chuyến bay nào được? | Gọi flight change quote/search API. |
| API-017 | Đổi chuyến này cần trả thêm bao nhiêu? | Gọi flight-change quote API. |
| API-018 | Quote đổi chuyến của tôi còn hạn không? | Gọi settlement/quote state API nếu có. |
| API-019 | Mã WELCOME10 của tôi còn dùng được không? | Gọi promotions/revalidate API. |
| API-020 | Tổng tiền sau khi nhập promo là bao nhiêu? | Gọi revalidate/booking preview API. |
| API-021 | Hôm nay có chuyến bay nào rẻ nhất? | Gọi flight search/calendar API. |
| API-022 | Tôi có ticket nào còn active trong booking? | Gọi tickets/manage booking API. |
| API-023 | Tôi đổi ghế 12A sang 14C được không? | Gọi seat change preview API. |
| API-024 | Tôi đổi suất ăn được không? | Gọi service change preview API. |
| API-025 | Tài khoản của tôi có booking nào sắp bay? | Gọi `GET /api/bookings/my-trips`. |
| API-026 | Trong booking ABC123, ghế nào còn trống cho tôi? | Gọi manage options/seat map API sau khi xác minh booking. |
| API-027 | Tôi đang ngồi ghế nào trong booking ABC123? | Gọi booking lookup/manage options/trip detail API. |
| API-028 | Đổi ghế trong Manage Booking có cần trả thêm tiền không? | Gọi seat change preview API; không tự tính từ bảng phí tham chiếu. |
| API-029 | Xác nhận đổi ghế này giúp tôi | Sau khi đã có preview và user xác nhận, gọi seat change confirm API; nếu cần thu thêm tiền thì tạo payment theo settlement. |
| API-030 | Booking ABC123 của tôi đã lưu số hộ chiếu chưa? | Gọi booking lookup/trip detail sau khi xác minh booking; không trả lời từ RAG. |
| API-031 | Thông tin ngày sinh/quốc tịch trong booking của tôi đúng chưa? | Gọi booking lookup/trip detail sau khi xác minh booking. |

## Fallback và không được bịa

Các câu này phải fallback hoặc trả lời rằng hệ thống chưa có thông tin chính thức nếu không có API/source đáng tin.

| ID | Câu hỏi test | Expected behavior |
| --- | --- | --- |
| FB-001 | Visa đi Nhật cần giấy tờ gì? | Không bịa; nói cần kiểm tra nguồn chính thức/nhân viên hỗ trợ. |
| FB-002 | Passport phải còn hạn bao nhiêu tháng để bay quốc tế? | Nếu chưa có policy chốt, nói chưa có thông tin chính thức. |
| FB-003 | Gate đóng trước mấy phút? | Không bịa nếu boarding gate chưa business confirm. |
| FB-004 | No-show có mất 100% tiền không? | Không bịa nếu no-show policy chưa chốt. |
| FB-005 | Hãng delay chuyến thì tôi được bồi thường bao nhiêu? | Không bịa nếu chưa có airline delay/cancel policy. |
| FB-006 | Có được mang thú cưng lên cabin không? | Chỉ trả lời nếu policy được nạp và chốt; nếu không thì fallback. |
| FB-007 | Có được mang pin 30,000 mAh không? | Chỉ trả lời nếu policy chốt; nếu không thì fallback. |
| FB-008 | Tôi có được refund thuế sân bay khi no-show không? | Không bịa nếu no-show/tax refund chưa chốt. |
| FB-009 | Có được đổi tên hành khách sau thanh toán không? | Không bịa nếu chưa có policy/code source. |
| FB-010 | Trẻ em đi một mình cần giấy tờ gì? | Nói phần này cần business confirmation nếu chưa chốt. |
| FB-011 | Tôi có được mang nước mắm lên cabin không? | Chỉ trả lời nếu policy cấm/hạn chế được nạp; nếu không thì fallback. |
| FB-012 | Hãng có hỗ trợ xe lăn không? | Không bịa nếu special assistance chưa có policy. |
| FB-013 | Tôi muốn đặt vé cho phụ nữ mang thai 36 tuần được không? | Không bịa nếu pregnancy policy chưa được chốt trong RAG. |
| FB-014 | Mức phí đổi tên vé là bao nhiêu? | Không bịa nếu không có policy/API. |
| FB-015 | Có hoàn tiền bằng ví điện tử không? | Không bịa nếu provider/refund method không có trong API/source. |

## Edge case và regression

Các câu này kiểm tra bot có hiểu đúng điều kiện, ngoại lệ và mâu thuẫn hay không.

| ID | Câu hỏi test | Expected behavior |
| --- | --- | --- |
| EDGE-001 | Booking của tôi `PENDING_PAYMENT`, tôi có check-in được không? | Trả lời không; check-in cần `CONFIRMED` và ticket `ISSUED`. |
| EDGE-002 | Ticket chưa `ISSUED` thì có check-in được không? | Trả lời không. |
| EDGE-003 | Tôi chưa chọn ghế thì confirm check-in được không? | Nói có thể cần chọn ghế trước; booking cụ thể phải gọi API. |
| EDGE-004 | Tôi đã check-in rồi, có hủy vé được không? | Nói booking đã check-in có thể bị khóa; booking cụ thể phải gọi API. |
| EDGE-005 | Booking còn payment `PENDING`, tôi đổi dịch vụ được không? | Nói backend có thể chặn; cần hoàn tất/hủy payment hoặc gọi API. |
| EDGE-006 | Infant có thể đổi chuyến một mình không? | Trả lời không; infant phải đi cùng adult trong thay đổi. |
| EDGE-007 | Đổi chuyến trước giờ bay 2 tiếng có được không? | Nói rule khóa trong vòng 180 phút nên không được theo policy. |
| EDGE-008 | Quote đổi chuyến tạo 20 phút trước còn dùng được không? | Nói quote 15 phút nên có thể đã hết hạn; cần gọi API để kiểm tra quote cụ thể. |
| EDGE-009 | Fare `INCLUDED` có phải chọn mọi ghế đều miễn phí không? | Trả lời không; chỉ category được bao gồm miễn phí, category cao hơn có thể phụ thu. |
| EDGE-010 | Fare `PAID` có nghĩa là mọi ghế đều chọn được không? | Trả lời không; vẫn phụ thuộc availability và seat class/category hợp lệ. |
| EDGE-011 | Ghế `AVAILABLE` nhưng không hợp fare thì có chọn được không? | Trả lời không. |
| EDGE-012 | Tôi chọn ghế cho infant được không nếu trả thêm tiền? | Trả lời không, infant không có ghế riêng theo policy hiện tại. |
| EDGE-013 | Tôi mua lounge dù fare đã có lounge được không? | Nói không cần và backend có thể chặn mua trùng. |
| EDGE-014 | Tôi mua priority boarding dù fare đã có priority được không? | Nói không cần và backend có thể chặn mua trùng. |
| EDGE-015 | Promo hết hạn nhưng tôi vẫn nhập được mã thì sao? | Nói promo không áp dụng; trạng thái cụ thể phải revalidate API. |
| EDGE-016 | Promo chưa đạt min booking amount thì có giảm không? | Trả lời không áp dụng. |
| EDGE-017 | Giá search có phải giá cuối cùng không? | Trả lời không; cần revalidate trước khi booking/thanh toán. |
| EDGE-018 | Currency không phải VND thì bot tự đổi tỷ giá được không? | Trả lời không; phải lấy giá/currency từ backend. |
| EDGE-019 | Hủy một passenger trong booking nhiều người thì booking thành `CANCELLED` không? | Nói nếu chỉ hủy một phần, booking có thể vẫn `CONFIRMED`; cần API để biết cụ thể. |
| EDGE-020 | Hủy tất cả ticket active thì booking thành gì? | Nói booking có thể chuyển `CANCELLED`. |
| EDGE-021 | Booking đang có thanh toán pending, tôi đổi ghế được không? | Nói hệ thống có thể chặn để tránh lệch tổng tiền; cần hoàn tất/hủy thanh toán trước. |
| EDGE-022 | Tôi đổi từ ghế có phí cao sang ghế phí thấp hơn thì sao? | Nói có thể phát sinh khoản chênh lệch hoàn/giảm; booking cụ thể phải gọi seat change preview. |
| EDGE-023 | Tôi đổi từ ghế miễn phí sang ghế có phí trong Manage Booking thì sao? | Nói cần preview và có thể cần thanh toán thêm; nếu booking đã confirmed và luồng chưa hỗ trợ thu thêm thì có thể bị từ chối. |
| EDGE-024 | Booking USD đổi sang ghế cửa sổ mất phí thì bot có tự tính USD không? | Trả lời không; phải tra cứu hệ thống, không tự quy đổi từ VND. |
| EDGE-025 | Ghế mới còn trống trên sơ đồ nhưng đang bị người khác giữ thì có chọn được không? | Trả lời không; trạng thái cuối phải lấy từ seat map/seat change preview. |
| EDGE-026 | Tôi bay quốc tế chỉ cần căn cước công dân được không? | Không khẳng định; nói cần hộ chiếu/giấy tờ nhập cảnh theo tuyến và nguồn chính thức. |
| EDGE-027 | Hộ chiếu còn hạn 4 tháng có bay được không? | Không bịa số tháng; yêu cầu kiểm tra quy định điểm đến/hãng bay. |
| EDGE-028 | Trẻ em đi một mình cần giấy ủy quyền không? | Không khẳng định nếu chưa có policy chính thức; hướng kiểm tra nhân viên hỗ trợ/nguồn chính thức. |

## Đa ngôn ngữ và language consistency

Các câu này kiểm tra retrieval trên câu hỏi không phải tiếng Việt và bảo đảm câu trả lời dùng đúng ngôn ngữ được chỉ định. Thuật ngữ nghiệp vụ, alias và mã trạng thái không được xem là sai ngôn ngữ.

| ID | Câu hỏi test | Expected behavior |
| --- | --- | --- |
| LANG-001 | When does online check-in open and close for a domestic flight? | [LANG=en] Answer in English; state that it opens 24 hours before departure and closes 40 minutes before departure. |
| LANG-002 | What does the booking status PENDING_PAYMENT mean? | [LANG=en] Answer in English; explain that the booking was created but payment has not succeeded and it remains subject to the hold expiry. |
| LANG-003 | Can an infant select a separate seat? | [LANG=en] Answer in English; say no because an infant does not have a separate seat under the current policy. |
| LANG-004 | How long is a flight-change quote valid? | [LANG=en] Answer in English; state 15 minutes. |
| LANG-005 | Can every INCLUDED fare select every seat for free? | [LANG=en] Answer in English; say no, only included seat categories are free and higher categories may cost extra. |
| LANG-006 | What documents do I need for a Japanese visa? | [LANG=en] Answer in English; do not invent requirements and direct the user to official sources or support. |
| LANG-007 | Quand l'enregistrement en ligne ouvre-t-il et ferme-t-il pour un vol intérieur ? | [LANG=fr] Répondre en français ; indiquer une ouverture 24 heures avant le départ et une fermeture 40 minutes avant le départ. |
| LANG-008 | Que signifie le statut de réservation PENDING_PAYMENT ? | [LANG=fr] Répondre en français ; expliquer que la réservation est créée mais que le paiement n'a pas abouti et qu'elle reste soumise à son délai d'expiration. |
| LANG-009 | Un bébé peut-il sélectionner un siège séparé ? | [LANG=fr] Répondre en français ; répondre non, car un bébé n'a pas de siège séparé selon la politique actuelle. |
| LANG-010 | Combien de mois mon passeport doit-il rester valide pour un vol international ? | [LANG=fr] Répondre en français ; ne pas inventer une durée et orienter vers une source officielle ou l'assistance. |
| LANG-011 | Booking chua thanh toan duoc giu bao lau? | [LANG=vi] Trả lời bằng tiếng Việt; nêu thời gian giữ mặc định 15 phút theo pnrExpiresAt. |
| LANG-012 | My booking đang PENDING_PAYMENT, tôi có check-in được không? | [LANG=vi] Trả lời bằng tiếng Việt; nói không, vì check-in cần booking CONFIRMED và ticket ISSUED. |

## Acceptance criteria

Knowledge base được xem là đạt mức demo nghiêm túc khi:

- 100% câu trong nhóm `Must-answer bằng RAG` trả lời đúng theo policy docs.
- 100% câu trong nhóm `Must-call-API` không bị bot bịa câu trả lời tĩnh.
- 100% câu trong nhóm `Fallback và không được bịa` không bị bot trả lời chắc chắn khi chưa có nguồn.
- Bot không trả lời mâu thuẫn với code về các mốc 15 phút, 24 giờ, 48 giờ, 40 phút, 60 phút, 180 phút, 90 phút.
- Mỗi câu trả lời quan trọng có thể truy ngược về một file policy trong thư mục này.
- Câu hỏi tiếng Anh, tiếng Pháp và tiếng Việt phải được trả lời bằng đúng ngôn ngữ yêu cầu trong nhóm language consistency.

Knowledge base được xem là đạt mức gần production khi:

- Tỷ lệ pass toàn bộ test case đạt ít nhất 95%.
- Không có fail ở các case liên quan payment, refund, hủy vé, đổi chuyến, dữ liệu cá nhân.
- Với mọi câu cần API, bot hoặc gọi đúng API/tool, hoặc yêu cầu thêm thông tin để gọi API.
- Với mọi câu chưa có policy chính thức, bot fallback thay vì bịa.
