# P6 — Test baseline / evidence cuối

07/11/2026. Chỉ các lượt dưới tính cho bàn giao, không cộng retries. P0–P6 PARTIAL/chưa tự đóng.

| Suite | Actual | Log/result |
|---|---|---|
| BE mandatory + new/affected |360pass =268 cũ +55P6 +37affected,0fail/error/skip | `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\be-final-complete` / result.json, maven.log, build/surefire-reports |
| FE type/unit/build |258pass =227cũ +31P6; type/build exit0 | `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\fe-handoff-final` / results.json, unit-results.json, logs |
| AI exact-lock |85pass =69cũ +16P6,0fail/error/skip; import pass | `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\ai-final-complete` / result.json, results.xml, logs |
| Original P4/G06 live |2pass, server_closed=true | `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\p4-live-final-complete` / result.json, live-results.json, backend.log, DB_AUDIT.json |
| P6 FE/BE/Python live |2pass; nằm trong1BE orchestration case, báo riêng | `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\fe-live-be-final-complete` / results.json, vitest.log |

## Exact-lock / fake boundary

Lock hash `58dddea67399405402933de681ca71e1be33bd5420e97d3bde03c4d7bf95b5ed` trùng P0; đủ77pinned,0missing/mismatch, pip check không lỗi. Python3.12.3/Windows, venv mới `C:\Users\LeTran\FPT University\2026_FALL\STEMotion\.audit\stem-phase-2026-11-05\phase-03\STEMotion_BE\.audit\stem-p6\exact-lock\venv`; pip24.0 bootstrap riêng. Cài đúng lock từ PyPI vào venv/cache P6 sau sandbox DNS failure, không sửa lock hoặc global env. Không cài requirements.acceptance/edge-TTS, không tải modelweights/MP4.

Pytest dùng P6 network guard, monkeypatch provider/fake; real Java/Python HTTP chạy trên127.0.0.1 với provider override trong test-only module. Grounded output lỗi schema không tự gọi model lại; timeout/429/claims/missing/stale/foreign/in-flight cases có negative evidence. Không real provider quality/cost/SDK-retry proof hoặc expert validation. Legacy69tests giữ nguyên assertions.

## Source / regression và lịch sử lỗi

Source hashes tại SOURCE_MANIFEST_P6; input635BE/261FE baseline kiểm bảo toàn, old test/config/runner/migrations/locks giữ bytes. Mandatory268không cộng41 lần nữa; mandatory227không cộng216 lần nữa. Full439BE không chạy. Không compiler/package/stack upgrade để né lỗi.

Historical attempts giữ dưới `.audit/stem-p6`: initial pip sandbox DNS fail; initial pytest sandbox timeout300s/no summary; native baseline69pass; early FE fixture timeline1stage fail và new live test nullable type error, sửa đúng P6 fixture/test. Earlier40/346/357/358/81/84 là intermediate runs, không cộng vào số cuối. Production changes/test negatives hợp lệ được rerun trên source cuối. No old P4/P5 assertions/config/runner patched. FE cache/build/output trong audit BE, không node_modules junction/repo gốc.

No browser Playwright, actual AI/TTS/mail/Brevo/YouTube, MP4/pixel/audio, independent artifact attestation, shared/staging/production, expert acceptance hoặc full AT01–24. CASE_COVERAGE_P6.md tách52ca +AT21 và giới hạn, không lấy số test thay curricular assurance.
