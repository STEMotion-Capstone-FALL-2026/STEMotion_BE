# Review orchestration BT06/BT07 trước execution

Chỉ chạy existing src/components/stem/StemFlow.p4-live.test.tsx của FE clone read-only, không sửa source/config/runner/cache/log baseline FE. Hai test này là P4-Q10 + G06; không Unit216/fullBE439/livevideo bổ sung.

- Config mới nằm trong gói BE này, native .mjs loader, nhập Vitest/React plugin từ deps FE hiện có, root/alias giữ FE/src. Không copy/rewrite test. envDir=false, không đọc .env; cache/module cache tắt và cacheDir/build/report ở BE .audit/gói sửa riêng.
- setup observe-live-http.mjs chỉ bọc fetch thật, trả nguyên response; không mock BE/DB. Chặn origin ngoài owned127.0.0.1:port, chỉ log method/path/status/request key names và settings baseRevision/mode; không log token/password/actor mapping.
- Backend dùng bytecode build after của clone BE, E2eServer.Config/launchArguments có sẵn, stem-p3-test/fresh embeddedPG25migrations, provider fake/off, email off. Catalog SQL TEST_ONLY/DRAFT của FE handoff chỉ đọc làm fixture DB mới; không migration/config shared.
- Launcher Java chỉ tồn tại trong Temp, boot đầy đủ main Spring; in owned port sau khi boot thành công, chờ stdin STOP rồi ctx.close để đóng EmbeddedPG. Không actuator/new product endpoint hoặc unwrap repository target.
- Sau hai tests, audit SELECT count riêng trên DB test mới: 1assessment/1approval decision/1receipt,3mode audit/revision3, payload settings chỉ baseRevision. Không chạy thêm script/video approval request.
- Native process/DB do runner tạo được đóng/đối soát; lỗi không được đổi sang mock. Chỉ các file BE orchestration/evidence mới, chưa đụng FE.
