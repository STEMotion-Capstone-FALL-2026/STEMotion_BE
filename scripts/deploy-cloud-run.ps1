#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ProjectId = 'stemotion-511107',
    [string]$Region = 'asia-southeast1',
    [string]$ServiceName = 'stemotion-be-staging',
    [string]$EnvFile = '',
    [string]$CorsOrigins = 'http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173',
    [string]$EmbedBaseUrl = '',
    [string]$RenderServiceUrl = ''
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
if (!$EnvFile) { $EnvFile = Join-Path $repoPath '.env' }
if (!(Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw 'Missing .env file. Supply -EnvFile.' }

# Values remain local until uploaded to Secret Manager. Never print the map.
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile))) {
    if ($line -match '^\s*(?:export\s+)?(?<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?<value>.*)$') {
        $key = $Matches.key
        $value = $Matches.value.Trim()
        if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        } else {
            $value = $value -replace '\s+#.*$', ''
        }
        $settings[$key] = $value
    }
}
foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Missing required setting: $key" }
}
if ([Text.Encoding]::UTF8.GetByteCount($settings.JWT_SECRET) -lt 32) {
    throw 'JWT_SECRET must have at least 32 UTF-8 bytes.'
}
if ($settings.DB_URL -notmatch '^jdbc:postgresql://') { throw 'DB_URL must be a PostgreSQL JDBC URL.' }
if ($settings.DB_URL -notmatch '[?&]sslmode=(require|verify-full|verify-ca)(?:&|$)') {
    throw 'Cloud database connection must use SSL.'
}
if ($settings.DB_URL -match ':6543/' -and $settings.DB_URL -notmatch '[?&]prepareThreshold=0(?:&|$)') {
    throw 'Supabase transaction pooler requires prepareThreshold=0.'
}
if ($RenderServiceUrl -and ($RenderServiceUrl -notmatch '^https://' -or !$settings.RENDER_SERVICE_KEY)) {
    throw 'Render requires a public HTTPS URL and RENDER_SERVICE_KEY.'
}

function Invoke-Cloud {
    param([string[]]$CloudArgs)
    $result = & gcloud @CloudArgs "--project=$ProjectId" --quiet
    if ($LASTEXITCODE -ne 0) { throw "gcloud failed: $($CloudArgs[0]) $($CloudArgs[1])" }
    return $result
}

function Ensure-ServiceAccount {
    param([string]$AccountId)
    $email = "$AccountId@$ProjectId.iam.gserviceaccount.com"
    $existing = Invoke-Cloud -CloudArgs @('iam', 'service-accounts', 'list', "--filter=email=$email", '--format=value(email)')
    if (!$existing) {
        Invoke-Cloud -CloudArgs @('iam', 'service-accounts', 'create', $AccountId, "--display-name=$AccountId") | Out-Host
    }
    return $email
}

Push-Location $repoPath
try {
    $billing = (Invoke-Cloud -CloudArgs @('billing', 'projects', 'describe', $ProjectId, '--format=json')) | ConvertFrom-Json
    if (!$billing.billingEnabled) {
        throw "Billing is disabled. Link an active billing account first: https://console.cloud.google.com/billing/linkedaccount?project=$ProjectId"
    }
    Invoke-Cloud -CloudArgs @('services', 'enable', 'run.googleapis.com', 'cloudbuild.googleapis.com',
        'artifactregistry.googleapis.com', 'secretmanager.googleapis.com', 'iam.googleapis.com') | Out-Host

    $repository = 'stemotion'
    $existingRepo = Invoke-Cloud -CloudArgs @('artifacts', 'repositories', 'list', "--location=$Region",
        "--filter=name:$repository", '--format=value(name)')
    if (!$existingRepo) {
        Invoke-Cloud -CloudArgs @('artifacts', 'repositories', 'create', $repository,
            '--repository-format=docker', "--location=$Region") | Out-Host
    }
    $runtimeEmail = Ensure-ServiceAccount 'stemotion-runtime'
    $builderEmail = Ensure-ServiceAccount 'stemotion-builder'
    Invoke-Cloud -CloudArgs @('artifacts', 'repositories', 'add-iam-policy-binding', $repository,
        "--location=$Region", "--member=serviceAccount:$builderEmail",
        '--role=roles/artifactregistry.writer') | Out-Null
    Invoke-Cloud -CloudArgs @('projects', 'add-iam-policy-binding', $ProjectId,
        "--member=serviceAccount:$builderEmail", '--role=roles/logging.logWriter') | Out-Null

    $sourceBucket = "$ProjectId-stemotion-build-source"
    $bucketExists = Invoke-Cloud -CloudArgs @('storage', 'buckets', 'list',
        "--filter=name=$sourceBucket", '--format=value(name)')
    if (!$bucketExists) {
        Invoke-Cloud -CloudArgs @('storage', 'buckets', 'create', "gs://$sourceBucket",
            "--location=$Region", '--uniform-bucket-level-access') | Out-Host
    }
    Invoke-Cloud -CloudArgs @('storage', 'buckets', 'add-iam-policy-binding', "gs://$sourceBucket",
        "--member=serviceAccount:$builderEmail", '--role=roles/storage.objectViewer') | Out-Null

    $secretNames = [ordered]@{
        DB_URL = "$ServiceName-db-url"
        DB_USERNAME = "$ServiceName-db-user"
        DB_PASSWORD = "$ServiceName-db-password"
        JWT_SECRET = "$ServiceName-jwt"
    }
    if ($settings.GEMINI_API_KEY) { $secretNames['GEMINI_API_KEY'] = "$ServiceName-gemini" }
    if ($RenderServiceUrl) { $secretNames['RENDER_SERVICE_KEY'] = "$ServiceName-render-key" }
    $secretBindings = @()
    foreach ($key in $secretNames.Keys) {
        $secretName = $secretNames[$key]
        $existingSecret = Invoke-Cloud -CloudArgs @('secrets', 'list', "--filter=name:$secretName", '--format=value(name)')
        if (!$existingSecret) {
            Invoke-Cloud -CloudArgs @('secrets', 'create', $secretName, '--replication-policy=automatic') | Out-Null
        }
        $secretFile = Join-Path $repoPath ('.env.cloud-secret-' + [guid]::NewGuid().ToString('N'))
        try {
            [IO.File]::WriteAllText($secretFile, $settings[$key], [Text.UTF8Encoding]::new($false))
            $version = Invoke-Cloud -CloudArgs @('secrets', 'versions', 'add', $secretName,
                "--data-file=$secretFile", '--format=value(name)')
            $versionNumber = ($version.Trim() -split '/')[-1]
            $secretBindings += "${key}=${secretName}:$versionNumber"
        } finally {
            if (Test-Path -LiteralPath $secretFile) { Remove-Item -LiteralPath $secretFile -Force }
        }
        Invoke-Cloud -CloudArgs @('secrets', 'add-iam-policy-binding', $secretName,
            "--member=serviceAccount:$runtimeEmail", '--role=roles/secretmanager.secretAccessor') | Out-Null
    }

    $releaseTag = 'staging-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Invoke-Cloud -CloudArgs @('builds', 'submit', '.', '--config=cloudbuild.yaml',
        "--region=$Region", "--gcs-source-staging-dir=gs://$sourceBucket/source",
        "--service-account=projects/$ProjectId/serviceAccounts/$builderEmail",
        "--substitutions=_REGION=$Region,_REPOSITORY=$repository,_TAG=$releaseTag") | Out-Host
    $image = "$Region-docker.pkg.dev/$ProjectId/$repository/backend:$releaseTag"
    $environment = [ordered]@{
        SPRING_PROFILES_ACTIVE = 'cloud'
        CORS_ORIGINS = $CorsOrigins
        EMBED_BASE_URL = $EmbedBaseUrl
        RENDER_SERVICE_URL = $RenderServiceUrl
        RENDER_CALLBACK_URL = ''
        GEMINI_MODEL = $(if ($settings.GEMINI_MODEL) { $settings.GEMINI_MODEL } else { 'gemini-2.5-flash' })
    }
    $envVarsFile = Join-Path $repoPath ('.env.cloud-vars-' + [guid]::NewGuid().ToString('N') + '.json')
    try {
        [IO.File]::WriteAllText($envVarsFile, ($environment | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
        # Deploy privately first. Public IAM access is enabled only after smoke tests pass.
        Invoke-Cloud -CloudArgs @('run', 'deploy', $ServiceName, "--image=$image", "--region=$Region",
            "--service-account=$runtimeEmail", '--cpu=1', '--memory=1Gi', '--concurrency=8',
            '--min-instances=0', '--max-instances=2', '--timeout=300', '--cpu-boost',
            '--no-allow-unauthenticated', "--env-vars-file=$envVarsFile",
            "--set-secrets=$($secretBindings -join ',')",
            '--startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=10,timeoutSeconds=5,failureThreshold=24') | Out-Host
    } finally {
        if (Test-Path -LiteralPath $envVarsFile) { Remove-Item -LiteralPath $envVarsFile -Force }
    }
    $serviceUrl = (Invoke-Cloud -CloudArgs @('run', 'services', 'describe', $ServiceName,
        "--region=$Region", '--format=value(status.url)')).Trim()
    if ($RenderServiceUrl) {
        Invoke-Cloud -CloudArgs @('run', 'services', 'update', $ServiceName, "--region=$Region",
            "--update-env-vars=RENDER_CALLBACK_URL=$serviceUrl/api/v1/render/callback") | Out-Host
    }
    $identityToken = (Invoke-Cloud -CloudArgs @('auth', 'print-identity-token')).Trim()
    $authHeaders = @{ Authorization = "Bearer $identityToken" }
    $health = Invoke-RestMethod "$serviceUrl/actuator/health/readiness" -Headers $authHeaders -TimeoutSec 60
    if ($health.status -ne 'UP') { throw 'Readiness smoke test failed; service remains private.' }
    $protected = Invoke-WebRequest "$serviceUrl/api/v1/auth/me" -Headers $authHeaders -SkipHttpErrorCheck -TimeoutSec 60
    if ($protected.StatusCode -ne 401) { throw 'JWT smoke test failed; service remains private.' }
    Invoke-Cloud -CloudArgs @('run', 'services', 'add-iam-policy-binding', $ServiceName,
        "--region=$Region", '--member=allUsers', '--role=roles/run.invoker') | Out-Null
    $publicHealth = Invoke-RestMethod "$serviceUrl/actuator/health/readiness" -TimeoutSec 60
    if ($publicHealth.status -ne 'UP') { throw 'Public readiness smoke test failed.' }
    Write-Host "Deployed backend: $serviceUrl"
    Write-Host "Frontend API base: $serviceUrl/api/v1"
    Write-Host 'Render remains unavailable until its VPS is deployed and connected.'
} finally {
    Pop-Location
}
