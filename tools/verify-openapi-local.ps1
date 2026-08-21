$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $workspace 'backend/src/main/resources/application-local.yml'
$config = Get-Content -Raw -LiteralPath $configPath
$urlMatch = [regex]::Match($config, 'url:\s*jdbc:postgresql://([^:/]+):(\d+)/([^\s]+)')
$dbHost = $urlMatch.Groups[1].Value
$dbPort = $urlMatch.Groups[2].Value
$dbName = $urlMatch.Groups[3].Value
$dbUser = [regex]::Match($config, 'username:\s*([^\s]+)').Groups[1].Value
$dbPassword = [regex]::Match($config, 'password:\s*([^\s]+)').Groups[1].Value
if (-not $dbHost -or -not $dbPort -or -not $dbName -or -not $dbUser -or -not $dbPassword) {
    throw 'Unable to resolve local PostgreSQL settings.'
}

$schema = 'learnflow_sprint4_verify'
$psql = 'D:\PostgreSQL\18\bin\psql.exe'
$existingListener = Get-NetTCPConnection -LocalPort 18082 -State Listen -ErrorAction SilentlyContinue
if ($existingListener) {
    throw 'Port 18082 is already in use; refusing to stop an unrelated process.'
}
$env:PGPASSWORD = $dbPassword
$existing = & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -At -v ON_ERROR_STOP=1 -c "select count(*) from information_schema.schemata where schema_name='$schema';"
if ($existing -ne '0') {
    throw "Verification schema already exists: $schema"
}

$process = $null
try {
    & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -c "create schema $schema;"
    $jdbcUrl = "jdbc:postgresql://${dbHost}:${dbPort}/${dbName}?currentSchema=$schema"
    $env:SPRING_DATASOURCE_URL = $jdbcUrl
    $env:SPRING_DATASOURCE_USERNAME = $dbUser
    $env:SPRING_DATASOURCE_PASSWORD = $dbPassword
    $env:LEARNFLOW_MIGRATION_DB_URL = $jdbcUrl
    $env:LEARNFLOW_MIGRATION_DB_USER = $dbUser
    $env:LEARNFLOW_MIGRATION_DB_PASSWORD = $dbPassword
    $env:LEARNFLOW_JWT_SECRET = 'local-verify-0123456789abcdef0123456789abcdef'
    $env:LEARNFLOW_OPENAPI_ENABLED = 'true'
    $env:LEARNFLOW_SECURE_COOKIE = 'false'

    $stdout = Join-Path $workspace 'tmp/openapi-verify.stdout.log'
    $stderr = Join-Path $workspace 'tmp/openapi-verify.stderr.log'
    $jar = Join-Path $workspace 'backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar'
    $process = Start-Process -FilePath 'java' -ArgumentList @(
        '-jar', $jar,
        '--server.port=18082',
        "--spring.flyway.schemas=$schema",
        "--spring.jpa.properties.hibernate.default_schema=$schema"
    ) -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru

    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 500
        try {
            Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18082/v3/api-docs' -OutFile (Join-Path $workspace 'tmp/openapi.json')
            $ready = $true
            break
        } catch {
            if ($process.HasExited) { break }
        }
    }
    if (-not $ready) {
        throw "OpenAPI verification application did not become ready. See $stdout and $stderr"
    }
    & python (Join-Path $workspace 'tools/check_openapi_contract.py') (Join-Path $workspace 'tmp/openapi.json') (Join-Path $workspace 'docs/openapi/required-paths.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OpenAPI contract verification failed.' }
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
    Get-NetTCPConnection -LocalPort 18082 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object { Stop-Process -Id $_ -Force }
    & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -c "drop schema if exists $schema cascade;"
}
