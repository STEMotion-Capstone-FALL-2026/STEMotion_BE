# Local negative-startup + liveness smoke. No model, provider call or BE startup.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$project = 'stemotion-ai-smoke'
$names = @('JWT_SECRET','RENDER_SERVICE_KEY','MINIO_ROOT_PASSWORD','DB_PASSWORD',
    'AI_SERVICE_ENV_FILE','AI_LOCAL_PORT','WHISPER_STARTUP_MODE','AI_SERVICE_SHARED_KEY','AI_PRICING_JSON')
$savedEnv = @{}
foreach ($name in $names) { $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$composeArgs = @('compose','--env-file','services/ai/.env.example','-p',$project)
$created = $false
Push-Location $repoRoot
try {
    $existing = & docker ps -aq --filter "label=com.docker.compose.project=$project"
    if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable' }
    if ($existing) { throw 'Smoke project already exists; refusing to replace existing containers' }
    $env:JWT_SECRET = 'local-smoke-placeholder-not-a-production-key'
    $env:RENDER_SERVICE_KEY = 'local-smoke-placeholder'
    $env:MINIO_ROOT_PASSWORD = 'local-smoke-placeholder'
    $env:DB_PASSWORD = 'local-smoke-placeholder'
    $env:AI_SERVICE_ENV_FILE = './services/ai/.env.example'
    $env:AI_SERVICE_SHARED_KEY = ''
    $env:AI_PRICING_JSON = '{}'
    $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $portProbe.Start()
    $port = $portProbe.LocalEndpoint.Port
    $portProbe.Stop()
    $env:AI_LOCAL_PORT = "$port"
    $env:WHISPER_STARTUP_MODE = 'required'
    $configText = & docker @composeArgs config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Compose config failed' }
    $config = $configText | ConvertFrom-Json
    if ($config.services.backend.environment.AI_SERVICE_URL -ne 'http://ai-service:8000' -or
        $config.services.backend.environment.AI_PRICING_JSON -ne '{}') { throw 'Unexpected BE AI configuration' }
    # Never output the rendered config: a real user's env could otherwise leak.
    & docker @composeArgs up -d --no-deps --no-build ai-service
    if ($LASTEXITCODE -ne 0) { throw 'Compose up failed' }
    $created = $true
    $containerId = (& docker @composeArgs ps -aq ai-service).Trim()
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $state = & docker inspect --format '{{.State.Status}}' $containerId
        if ($state -eq 'exited') { break }
        Start-Sleep -Seconds 1
    }
    $exitCode = & docker inspect --format '{{.State.ExitCode}}' $containerId
    $logs = (& docker @composeArgs logs --no-color ai-service 2>&1) -join "`n"
    if ($state -ne 'exited' -or $exitCode -ne '1' -or $logs -notmatch 'Whisper weights missing') {
        throw 'Expected bounded missing-weights failure (use an empty ./models folder for smoke)'
    }
    Write-Output 'PASS: required startup exits 1 with explicit missing-weights error'
    $env:WHISPER_STARTUP_MODE = 'optional'
    & docker @composeArgs up -d --no-deps --no-build ai-service
    if ($LASTEXITCODE -ne 0) { throw 'Optional smoke startup failed' }
    $containerId = (& docker @composeArgs ps -aq ai-service).Trim()
    $health = $null
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $health = Invoke-WebRequest "http://127.0.0.1:$port/health" -TimeoutSec 2
            if ($health.StatusCode -eq 200) { break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $health -or $health.StatusCode -ne 200) { throw 'Liveness did not become available' }
    $ready = Invoke-WebRequest "http://127.0.0.1:$port/ready" -TimeoutSec 2 -SkipHttpErrorCheck
    if ($ready.StatusCode -ne 503) { throw 'Missing model/config incorrectly reported ready' }
    $uid = (& docker exec $containerId id -u).Trim()
    if ($LASTEXITCODE -ne 0 -or $uid -ne '10001') { throw 'Container is not expected non-root user' }
    & docker exec $containerId python -c 'import av, ctranslate2, faster_whisper; print("PASS: Linux native imports")'
    if ($LASTEXITCODE -ne 0) { throw 'Native dependencies missing' }
    Write-Output 'PASS: /health=200, /ready=503 without weights/keys; UID=10001'
} finally {
    if ($created) {
        & docker @composeArgs rm -s -f ai-service
        # Remove only this isolated project's empty network; never prune globally.
        & docker network rm "${project}_default"
    }
    Pop-Location
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process') }
}
