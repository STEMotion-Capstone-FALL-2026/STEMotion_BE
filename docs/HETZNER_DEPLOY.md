# Hetzner CX23 — chuẩn bị local, chưa tạo VPS / chưa deploy

Cập nhật 2026-10-16. Phase A chỉ tạo file, kiểm tra local và commit local; không tạo tài khoản/server, không gọi cloud API hay API trả phí, không push. Dưới đây là hướng dẫn cho một lần triển khai được duyệt **sau này**.

## Trạng thái và prerequisite còn thật

Bốn service: Caddy -> ai-service / worker; worker -> Redis. BE/DB tiếp tục ở Render/Supabase. Một bridge Docker duy nhất, chỉ Caddy publish 80/443. Không dùng `internal: true` vì OpenAI/TTS/ACME cần outbound internet; bridge riêng không có nghĩa là cấm egress. Redis/AI/worker không publish port. UFW không thể bảo vệ tùy ý các port Docker đã publish, vì vậy không thêm ports cho ba service này.

Worker có Dockerfile, cần **cả RENDER và context FE**. Build source hiện **FAIL** tại RENDER npm ci: package.json yêu cầu Remotion 4.0.526 nhưng package-lock.json có 4.0.527. Không sửa repo freeze. Hai đường đi hợp lệ: team RENDER sửa/kiểm thử lock trong task riêng, hoặc chủ dự án chọn một image đã build/nghiệm thu để chuyển sang VPS. Local topology đã dùng image thật cũ `stemotion-render:gcp-20261011`, không dùng nginx, không xác nhận image này tương đương source hiện tại và chưa render nghiệm thu clip. Không thể gọi mọi prerequisite release đã xong khi blocker này còn tồn tại.

Worker hiện bỏ qua REDIS_PASSWORD trong config.ts/queue.ts. Để Redis vẫn bắt AUTH mà không sửa source freeze, compose preload file BE `redis-auth.cjs` qua NODE_OPTIONS: cấu hình defaultOptions.password của đúng các instance ioredis mà worker/BullMQ sử dụng. Test thực đã xác nhận PONG từ worker và queue=bullmq, còn client không password bị NOAUTH. Đây là adapter deployment cần test lại khi đổi image/ioredis; loại bỏ sau khi RENDER đọc password native. Không phải thêm code nghiệp vụ vào worker.

**RAM:** ai-service limit 2.5 GiB, worker 1.5 GiB, Redis 192 MiB, Caddy 128 MiB; các mức trần cộng lại vượt RAM vật lý 4 GB. Limit không reserve RAM, không tự khóa tác vụ giữa service và không bảo đảm chống OOM. BullMQ/frame concurrency cố định 1 chỉ giới hạn worker. Swap 4 GiB giúp đệm nhưng có thể chậm. Chưa benchmark hai workload nặng trên CX23. Với một box 4 GB, vận hành tuần tự theo bước 8; nếu cần đồng thời thì cần scheduler/global mutex hoặc máy lớn hơn trong task riêng.

## Trước ngày triển khai: chuẩn bị bundle (không tốn phí cloud)

Trên máy dev, sau khi commit tất cả file deployment, tạo bundle từ file Git-tracked của đúng ba checkout. Không copy nguyên working directory có .env/.local-backups/node_modules. Script không tải model và không gửi gì lên mạng:

```powershell
.venv/Scripts/python.exe deploy/hetzner/package.py --output .audit/stemotion-hetzner-bundle.tar.gz --include-models --worker-image stemotion-render:gcp-20261011
```

Bundle chứa source ba repo, manifest SHA, **weights đã có local và đã kiểm checksum**, image worker đã có, không chứa .env thật, cache hay Git history. Không commit bundle. `--worker-image` không thay thế việc chủ dự án duyệt image; hiện chỉ biết topology/auth pass. Nếu chọn đường build source sau khi team sửa lock, bỏ --worker-image. Nếu không có weights verified, dừng chuẩn bị để provision riêng; startup không tự tải. Bản ghi model kiểm checksum ở services/ai/acceptance/vi-water-displacement/model-provenance.json. Không cần OpenAI credit để đóng gói.

## Các bước cho người lần đầu dùng VPS

1. **Tài khoản:** tự tạo tại https://accounts.hetzner.com, bật bảo vệ tài khoản. Nhà cung cấp có thể yêu cầu giấy tờ/xác minh; dự trù 1–2 ngày là kế hoạch của team, không phải SLA và không chỉ áp dụng ngoài EU. Đọc [FAQ xác minh](https://docs.hetzner.com/general/security-and-identify/fraud-prevention-faq/). Phase A không tạo tài khoản, nhập thẻ hay xác minh hộ.

2. **SSH key:** trên máy cá nhân dùng ssh-keygen -t ed25519, đặt passphrase; chỉ upload file .pub vào Hetzner Console, không upload private key. Tạo CX23 x86 Intel/AMD, Ubuntu 24.04, region EU nbg1 hoặc fsn1 nếu dashboard có hàng; kiểm tra lại stock/spec/giá trước khi bấm Create. Chọn Primary IPv4 và add SSH public key. Tạo server phát sinh tiền ngay; chưa được làm trong Phase A. [Spec chính thức](https://www.hetzner.com/cloud/cost-optimized/) liệt kê 2 shared vCPU, 4 GB, 40 GB NVMe, 20 TB EU; tình trạng khả dụng có thể thay đổi. Không tự đổi sang ARM CAX vì image/Chromium cần nghiệm thu riêng.

3. **Upload và SSH:** dùng WinSCP/SFTP với tài khoản root và private key để upload bundle vào /root/stemotion-bundle.tar.gz. SSH root@SERVER_IP, đối chiếu host-key fingerprint trong console trước khi chấp nhận. Lệnh chính thứ nhất (chỉ chạy trên VPS mới đúng mục tiêu):

   ```bash
   mkdir -p /opt/stemotion && tar -xzf /root/stemotion-bundle.tar.gz -C /opt/stemotion && bash /opt/stemotion/STEMotion_BE/deploy/hetzner/setup.sh
   ```

   setup.sh chỉ nhận Ubuntu 24.04/root, copy public authorized_keys sang deploy, tạo user deploy thuộc sudo/docker, sudo NOPASSWD (quyền quản trị tương đương root), cài Docker/Compose từ repo Docker chính thức, cập nhật packages, UFW 22/80/443, fail2ban SSH và swap /swapfile 4 GiB. Nó không reset rule UFW tùy biến có sẵn. Chạy lại không nhân key/fstab và không format lại swap. Nó chỉ chown workspace chuyên dụng /opt/stemotion, không động nơi khác. Nếu dùng key file khác, đặt DEPLOY_SSH_KEY_FILE trước khi chạy. SSH port khác 22 bị từ chối.

4. **Verify host trước khi đóng session:** xem checklist script in ra: docker --version, docker compose version, ufw status verbose, free -h, swapon --show. UFW trên máy mới chỉ có 22/tcp, 80/tcp, 443/tcp; Docker có thể vượt UFW nếu publish thêm port. Mở session thứ hai `ssh deploy@SERVER_IP`, kiểm tra `sudo -n true`, `docker ps`, `sudo fail2ban-client status sshd`. Nếu có /var/run/reboot-required, reboot trong cửa sổ được duyệt rồi kiểm tra SSH lại. Setup chỉ được mô phỏng local bằng mock; đây là nghiệm thu host thật bắt buộc ngày triển khai.

5. **Điền secrets bằng SFTP/editor, không dán vào shell history:** copy deploy/hetzner/.env.example thành deploy/hetzner/.env, chmod 600, owner deploy. Mỗi biến mẫu có comment/ví dụ. AI_SERVICE_SHARED_KEY, RENDER_SERVICE_KEY, REDIS_PASSWORD phải khác nhau, random >=32 ký tự hex/base64url (openssl rand -hex 32), riêng các key khớp phía BE tương ứng. OPENAI_API_KEY do chủ dự án cấp; FPT key TTS_API_KEY chỉ cần khi bật USE_TTS=true. AI_PRICING_JSON là cấu hình **BE trên Render**, không phải Python spend cap; AI_MONTHLY_BUDGET_USD chưa được enforce. Không in docker compose config ra màn hình/log vì có secrets.

   Mode test: DEPLOY_MODE=test, chưa có domain, WHISPER_STARTUP_MODE=required khi đã có model. Dùng secret test và không đưa key trả phí vào HTTP public; chỉ kiểm /health hoặc dùng SSH tunnel. WORKER_PUBLIC_BASE_URL=http://SERVER_IP/worker/videos. Mode prod: DEPLOY_MODE=prod, AI_DOMAIN và WORKER_DOMAIN trỏ DNS A tới IPv4 này, ACME_EMAIL thật, WORKER_PUBLIC_BASE_URL=https://WORKER_DOMAIN/videos, OPENAI_API_KEY và weights đầy đủ. Không bật optional startup để che thiếu model trong prod. Cấu hình này mặc định bind host IPv4 0.0.0.0; không tạo DNS AAAA cho đến khi cấu hình và kiểm thử publish IPv6 riêng.

6. **Khởi động:** nếu dùng bundle image worker đã được chọn, lệnh chính thứ hai là:

   ```bash
   docker load -i /opt/stemotion/worker-image.tar
   ```

   Đặt WORKER_IMAGE trong .env đúng tag vừa load. Lệnh chính thứ ba:

   ```bash
   sudo -u deploy bash -c 'cd /opt/stemotion/STEMotion_BE && bash deploy/hetzner/deploy.sh up --worker-image-only'
   ```

   Script preflight kiểm mode/secrets/ports/model cho prod, snapshot image-ID/config cũ nếu đã có stack, build AI rồi dùng image worker đã load. Nếu team đã sửa lock và chọn build source, bỏ --worker-image-only: script build tuần tự AI rồi worker, tránh hai build nặng song song. Nó tương đương flow compose up nhưng kiểm tra trước. Lệnh trực tiếp sau khi source/prerequisite đều đã sẵn sàng là `docker compose --env-file deploy/hetzner/.env -f docker-compose.hetzner.yml up -d --build`; **hiện lệnh build này bị blocker lock RENDER**. Không chạy nó để hy vọng dependency tự sửa. Sau ba lệnh chính vẫn cần các thao tác UI/SSH/DNS/secret/host verification nói trên; không có phép màu bỏ qua prerequisite.

7. **Verify endpoint và wiring BE:** chạy bash deploy/hetzner/deploy.sh verify. Test IP: http://SERVER_IP/health (Caddy), /ai/health (AI), /worker/health (worker). Prod: https://AI_DOMAIN/health, https://AI_DOMAIN/ready, https://WORKER_DOMAIN/health. /health chỉ liveness; /ready cần configured key và model offline preflight, không kiểm chứng credit/provider key thật. Worker health phải queue=bullmq; Redis AUTH phải PONG, không password phải NOAUTH. Chỉ Caddy có host ports; lệnh docker compose ps phải phản ánh điều đó. Caddy tự xin TLS Let's Encrypt ở prod; chưa kiểm issuance thật trong Phase A. DNS/public 80/443 phải đúng và không bị cloud firewall chặn.

   Chỉ trong lần thay đổi Render được duyệt riêng, đặt:

   | Nơi | Biến | Giá trị prod |
   |---|---|---|
   | Render BE | AI_SERVICE_URL | https://AI_DOMAIN |
   | Render BE | AI_SERVICE_MODE / AI_SERVICE_SHARED_KEY | remote / cùng secret phía AI |
   | Render BE | RENDER_SERVICE_URL / RENDER_SERVICE_KEY | https://WORKER_DOMAIN / cùng secret worker |
   | Render BE | RENDER_CALLBACK_URL | https://BE_HOST/api/v1/render/callback |
   | VPS worker | API_CALLBACK_URL | https://BE_HOST/api/v1/render/callback |
   | VPS worker | WORKER_PUBLIC_BASE_URL | https://WORKER_DOMAIN/videos |

   Callback luôn quay về BE, không trỏ ngược vào worker. BE gửi X-AI-Api-Key sang AI và X-Api-Key sang worker. Caddy còn chặn các worker route tính toán/TTS thiếu key vì worker hiện có vài route không guard; health và media/status vẫn public. Không đưa key vào FE. Với HTTP test, AI_SERVICE_URL=http://IP/ai và RENDER_SERVICE_URL=http://IP/worker chỉ là cấu hình nghiệm thu test; không chuyển production BE sang HTTP bằng task này. Không apply migration/V15 trong quy trình VPS. Chưa có render/TTS/OpenAI trả phí acceptance trên VPS.

8. **Giữ workload tuần tự trên 4 GB:** quiesce đầu vào BE trước, chờ không còn request/render job đang chạy hoặc chờ. Dùng `bash deploy/hetzner/workload.sh ai --quiesced` để dừng worker rồi bật AI; dùng `... render --quiesced` để dừng AI rồi bật worker. Helper kiểm Redis active/wait khi chuyển về AI và chờ stop worker trước khi bật AI. Service bị dừng sẽ trả 502/503 qua proxy; đây là downtime chủ động. Không gọi deploy.sh up lại trong cửa sổ này vì nó bật cả hai. Helper không tự khóa request từ nhiều user; nếu không thể quiesce traffic, CX23/chính sách này chưa đủ để hứa vận hành an toàn. Chưa test peak memory/clip thật, không tuyên bố 1.5 GB worker luôn đủ.

9. **Rollback:** trước mỗi up, script ghi snapshot private dưới deploy/hetzner/releases/TIMESTAMP gồm .env, compose, Caddy/bootstrap và **image IDs thực của container cũ**. Giữ bundle source cũ và không xóa old images. Quiesce BE và drain các job trước khi chạy:

   ```bash
   bash deploy/hetzner/rollback.sh deploy/hetzner/releases/TIMESTAMP --quiesced
   ```

   Script dùng compose down **không -v**, phục hồi config cũ và up với image IDs cũ, pull_policy=never, không rebuild. Lệnh tay tương đương khi đang dùng đúng bundle/config cũ:

   ```bash
   docker compose --env-file deploy/hetzner/.env -f docker-compose.hetzner.yml down
   AI_IMAGE=stemotion-ai:OLD_TAG WORKER_IMAGE=stemotion-worker:OLD_TAG docker compose --env-file deploy/hetzner/.env -f docker-compose.hetzner.yml up -d --no-build --pull never
   ```

   Không prune image/volume trong lúc cần rollback. Redis queue, video volume, model mount và Caddy cert không quay ngược theo code; key cũ đã revoke cần cập nhật. Không có bản deploy tốt trước đó thì rollback là dừng stack mới và khôi phục wiring BE trước đó; không bịa một SHA đã nghiệm thu. Rollback trên VPS thật chưa được diễn tập trong Phase A.

10. **Theo dõi và dọn tài nguyên tính phí:** kiểm tra Usage/Billing trong Hetzner; shutdown máy không dừng billing. Khi kết thúc test được duyệt, backup dữ liệu cần giữ rồi xóa server **và Primary IPv4**; kiểm tra không còn snapshot, volume, backup hay tài nguyên khác tính phí. Chỉ xóa server không bảo đảm hóa đơn về 0. Không làm các thao tác đó trong Phase A này.

## Giá tham khảo đã kiểm chứng 2026-10-16

| Hạng mục EU | Theo giờ | Trần tháng, chưa VAT |
|---|---:|---:|
| CX23 | €0.0088 | €5.49 |
| Primary IPv4 | €0.0008 | €0.50 |
| Tổng cơ bản | **€0.0096** | **€5.99** |
| 3 giờ test (ước lượng) | **€0.0288** | không phải tiền đã tiêu |
| 4 giờ test (ước lượng) | **€0.0384** | chưa VAT |

Giá €0.007/giờ trong prompt không còn khớp bảng giá hiện tại. Vài giờ (ví dụ 3–4) có thể dưới €0.05 trước VAT; 6 giờ là €0.0576, nên không hứa mọi đợt test đều dưới €0.05. Chưa tính domain, VAT, API OpenAI/FPT, vượt traffic, backup/snapshot/volume. Billing làm tròn lên giờ và có monthly cap riêng mỗi resource. Không có chi phí Hetzner phát sinh trong Phase A.

Nguồn: [bảng điều chỉnh CX23](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/), [Primary IP pricing](https://docs.hetzner.com/cloud/servers/primary-ips/overview/), [billing/stopped server/IP độc lập](https://docs.hetzner.com/cloud/billing/faq/), [Docker Ubuntu installation](https://docs.docker.com/engine/install/ubuntu/), [Caddy env/site concepts](https://caddyserver.com/docs/caddyfile/concepts). Availability phải kiểm tra lại trong dashboard, không được suy ra từ giá niêm yết.

## Kết quả local / giới hạn bàn giao

- Task 1: compose/config PASS; wire Dockerfile/context thật. **Build source worker FAIL do lock RENDER**, không sửa repo freeze. Image cũ được test thật, không nhầm với source mới.
- Task 2: bash syntax + chạy setup hai lần trong Ubuntu 24.04 container PASS với apt/curl/systemctl/UFW/swapon mock; kiểm quyền, group, swap không format lại, SSH/fstab không duplicate và từ chối SSH port khác. Không coi là test VM/kernel/firewall thật.
- Task 3: Caddy HTTP proxy PASS; prod config validate PASS với --network none, không xin certificate thật.
- Task 4: template env/comment + preflight PASS. Secret test random chỉ sống trong subprocess; không commit secret.
- Task 5: runbook/bundle/deploy/rollback/workload helpers đã chuẩn bị; cloud/host thật cần nghiệm thu sau khi được duyệt.
- Task 6: bốn container healthy trong project smoke riêng; Caddy/AI/worker health 200, worker queue=bullmq, AUTH và Redis restart persistence PASS; không model/key nên AI ready 503 đúng. Render nặng, TTS, OpenAI, TLS issuance không được chạy. Chỉ publish Caddy vào loopback port local. Project/volume/network test đã dọn, các container có sẵn giữ nguyên.

Re-run local topology: `.venv/Scripts/python.exe deploy/hetzner/smoke.py --worker-image stemotion-render:gcp-20261011`. Nếu không có image thật, `--mock-worker` dùng nginx:alpine, chỉ topology, không kiểm BullMQ/render; báo rõ mock. Mock mode chưa được dùng cho kết quả nêu trên. Nginx không phải image dùng production.

Bằng chứng máy đọc được: deploy/hetzner/tests/local-results-2026-10-16.json. ShellCheck PASS; 4 preflight regression tests PASS; snapshot helper đã kiểm đúng bốn image IDs thực của container smoke.
