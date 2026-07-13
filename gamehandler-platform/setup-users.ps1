<#
.SYNOPSIS
  Crea 4 utenti (uno per ruolo) su gamehandler-platform.
.DESCRIPTION
  1) Registra 4 utenti su central-system via POST /api/users (tutti come PLAYER)
  2) Attende la replica su local.replicated_users (polling ogni 10s, max 6 min)
  3) Imposta i ruoli via SQL su entrambi i DB (chicken-egg workaround per il primo PLATFORM_ADMIN)
  4) Fa il binding LOCAL_ADMIN <--> building-1 via API central + replica immediata via SQL sul local
  5) Emette in output una tabella con nome / password / ruolo / userId
  Idempotente: se gli utenti esistono gia', si limita ad aggiornare i ruoli e i binding (INSERT ... ON DUPLICATE KEY).
.PREREQUISITES
  - Container Docker central-db e local-db-1 UP
  - central-system (https://localhost:8180) e local-server (https://localhost:8181) avviati
#>
#Requires -Version 7
$ErrorActionPreference = 'Stop'

$CENTRAL_BASE = 'https://localhost:8180'
$LOCAL_BASE   = 'https://localhost:8181'
$DB_PWD       = 'root'

$users = @(
    @{ username = 'player1';         password = 'player-password';        email = 'player1@example.com';        role = 'PLAYER' }
    @{ username = 'localadmin1';     password = 'localadmin-password';    email = 'localadmin1@example.com';    role = 'LOCAL_ADMIN' }
    @{ username = 'gameadmin1';      password = 'gameadmin-password';     email = 'gameadmin1@example.com';     role = 'GAME_ADMIN' }
    @{ username = 'platformadmin1';  password = 'platformadmin-password'; email = 'platformadmin1@example.com'; role = 'PLATFORM_ADMIN' }
)

function Invoke-Api {
    param([string]$Method, [string]$Url, [string]$Body = $null)
    $params = @{
        Method                = $Method
        Uri                   = $Url
        SkipCertificateCheck  = $true
        TimeoutSec            = 15
    }
    if ($Body) { $params.Body = $Body; $params.ContentType = 'application/json' }
    Invoke-RestMethod @params
}

function Get-DbScalar {
    param([string]$Container, [string]$Query)
    $out = docker exec $Container mysql -uroot "-p$DB_PWD" -s -N -e $Query 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Query fallita su $Container`: $Query" }
    return ($out -join '').Trim()
}

function Exec-Db {
    param([string]$Container, [string]$Query)
    docker exec $Container mysql -uroot "-p$DB_PWD" -e $Query 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Query fallita su $Container`: $Query" }
}

function Test-Health {
    param([string]$Url, [string]$Label)
    try {
        $h = Invoke-Api -Method GET -Url "$Url/actuator/health"
        if ($h.status -ne 'UP') { throw "status=$($h.status)" }
        Write-Host "  $Label`: UP su $Url" -ForegroundColor Green
    } catch {
        Write-Host "  FAIL: $Label non raggiungibile su $Url ($($_.Exception.Message))" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "=== Setup utenti gamehandler-platform ===" -ForegroundColor Cyan
Write-Host ""

# === STEP 1: Prerequisiti ===
Write-Host "[1/6] Verifica prerequisiti..." -ForegroundColor Yellow

$centralContainer = (docker ps --filter "name=central-db"  --format "{{.Names}}" | Select-Object -First 1)
$localContainer   = (docker ps --filter "name=local-db-1"  --format "{{.Names}}" | Select-Object -First 1)
if (-not $centralContainer) { Write-Host "  FAIL: container central-db non UP" -ForegroundColor Red; exit 1 }
if (-not $localContainer)   { Write-Host "  FAIL: container local-db-1 non UP"  -ForegroundColor Red; exit 1 }
Write-Host "  Container Docker: $centralContainer + $localContainer UP" -ForegroundColor Green

Test-Health -Url $CENTRAL_BASE -Label "central-system"
Test-Health -Url $LOCAL_BASE   -Label "local-server"

try { Get-DbScalar -Container $centralContainer -Query "SELECT 1 FROM central_db.users LIMIT 1;" | Out-Null; Write-Host "  DB central: OK" -ForegroundColor Green }
catch { Write-Host "  FAIL: impossibile leggere central_db.users (root/$DB_PWD)" -ForegroundColor Red; exit 1 }
try { Get-DbScalar -Container $localContainer -Query "SELECT 1 FROM local_db.replicated_users LIMIT 1;" | Out-Null; Write-Host "  DB local: OK" -ForegroundColor Green }
catch { Write-Host "  FAIL: impossibile leggere local_db.replicated_users" -ForegroundColor Red; exit 1 }
Write-Host ""

# === STEP 2: Registrazione utenti (public API su central) ===
Write-Host "[2/6] Registrazione utenti via POST /api/users (central)..." -ForegroundColor Yellow
foreach ($u in $users) {
    $userCount = [int](Get-DbScalar -Container $centralContainer -Query "SELECT COUNT(*) FROM central_db.users WHERE username='$($u.username)';")
    if ($userCount -gt 0) {
        $u.userId = Get-DbScalar -Container $centralContainer -Query "SELECT id FROM central_db.users WHERE username='$($u.username)';"
        Write-Host "  $($u.username): esiste -> userId=$($u.userId)" -ForegroundColor DarkYellow
    } else {
        $body = @{ username = $u.username; password = $u.password; email = $u.email } | ConvertTo-Json -Compress
        try {
            $resp = Invoke-Api -Method POST -Url "$CENTRAL_BASE/api/users" -Body $body
            $u.userId = $resp.userId
            if (-not $u.userId) {
                $u.userId = Get-DbScalar -Container $centralContainer -Query "SELECT id FROM central_db.users WHERE username='$($u.username)';"
            }
            Write-Host "  $($u.username): creato -> userId=$($u.userId)" -ForegroundColor Green
        } catch {
            Write-Host "  FAIL creazione $($u.username): $($_.Exception.Message)" -ForegroundColor Red
            exit 1
        }
    }
}
Write-Host ""

# === STEP 3: Attesa replica su local.replicated_users ===
Write-Host "[3/6] Attesa replica su local.replicated_users (polling 10s, max 6 min)..." -ForegroundColor Yellow
$usernamesCsv = ($users | ForEach-Object { "'$($_.username)'" }) -join ','
$maxWait = 360
$startTime = Get-Date
$replicated = $false
$prevCount = -1
while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds($maxWait)) {
    $count = [int](Get-DbScalar -Container $localContainer -Query "SELECT COUNT(*) FROM local_db.replicated_users WHERE username IN ($usernamesCsv);")
    if ($count -ne $prevCount) {
        Write-Host "  replicati: $count / $($users.Count)..." -ForegroundColor DarkGray
        $prevCount = $count
    }
    if ($count -eq $users.Count) { $replicated = $true; break }
    Start-Sleep -Seconds 10
}
if ($replicated) {
    Write-Host "  Replica completa ($($users.Count)/$($users.Count))" -ForegroundColor Green
} else {
    Write-Host "  Timeout: replica non completa dopo $maxWait s. Avvia il local-server o attendi il prossimo tick dello scheduler." -ForegroundColor Red
    exit 1
}
Write-Host ""

# === STEP 4: Assegnazione ruoli via SQL ===
Write-Host "[4/6] Assegnazione ruoli via SQL su central.users + local.replicated_users..." -ForegroundColor Yellow
foreach ($u in $users) {
    Exec-Db -Container $centralContainer -Query "UPDATE central_db.users SET roles='$($u.role)' WHERE username='$($u.username)';"
    Exec-Db -Container $localContainer   -Query "UPDATE local_db.replicated_users SET roles='$($u.role)' WHERE username='$($u.username)';"
    Write-Host "  $($u.username) -> $($u.role)" -ForegroundColor Green
}
Write-Host ""

# === STEP 5: Binding LOCAL_ADMIN <--> building-1 ===
Write-Host "[5/6] Binding LOCAL_ADMIN <--> building-1..." -ForegroundColor Yellow

# 5a: ottieni JWT platform admin sul central (utente appena promosso via SQL nello step 4)
$loginBody = @{ username = 'platformadmin1'; password = 'platformadmin-password' } | ConvertTo-Json -Compress
try {
    $loginResp = Invoke-Api -Method POST -Url "$CENTRAL_BASE/api/auth/login" -Body $loginBody
    $platformToken = $loginResp.token
    Write-Host "  Login central come platformadmin1: OK (token ottenuto)" -ForegroundColor Green
} catch {
    Write-Host "  FAIL login platformadmin1 su central: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  Il binding puo' essere eseguito manualmente in seguito." -ForegroundColor DarkYellow
    $platformToken = $null
}

$localAdmin = $users | Where-Object { $_.role -eq 'LOCAL_ADMIN' } | Select-Object -First 1
if ($platformToken -and $localAdmin -and $localAdmin.userId) {
    # 5b: chiama API central POST /api/admin/local/buildings (triggera outbox LOCAL_ADMIN_BUILDING_ASSIGNED)
    $bindingBody = @{ userId = $localAdmin.userId; buildingIds = @('building-1') } | ConvertTo-Json -Compress
    try {
        $headers = @{ Authorization = "Bearer $platformToken" }
        Invoke-RestMethod -Method POST -Uri "$CENTRAL_BASE/api/admin/local/buildings" -Body $bindingBody -ContentType 'application/json' -Headers $headers -SkipCertificateCheck -TimeoutSec 15 | Out-Null
        Write-Host "  API: binding $($localAdmin.username) <-> building-1 emesso (outbox LOCAL_ADMIN_BUILDING_ASSIGNED)" -ForegroundColor Green
    } catch {
        # idempotenza: se gia' bindato, l'API puo' ritornare 200 no-op; se 409/400 ignoriamo
        Write-Host "  API binding: $($_.Exception.Message) (potrebbe essere gia' bindato - si prova SQL diretto)" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  Saltato step API (no token o no localadmin userId)" -ForegroundColor DarkYellow
}

# 5c: replica immediata su local_db.local_admin_buildings_local (per evitare attesa tick di scheduler)
if ($localAdmin -and $localAdmin.userId) {
    Exec-Db -Container $localContainer -Query "INSERT INTO local_db.local_admin_buildings_local (user_id, building_id, assigned_at) VALUES ('$($localAdmin.userId)','building-1',NOW()) ON DUPLICATE KEY UPDATE assigned_at=NOW();"
    Write-Host "  SQL locale: binding $($localAdmin.username) <-> building-1 replicato in local_admin_buildings_local" -ForegroundColor Green

    # 5d: idempotenza su central_db.local_admin_buildings (se l'API non l'ha gia' scritto)
    Exec-Db -Container $centralContainer -Query "INSERT INTO central_db.local_admin_buildings (user_id, building_id, assigned_at) VALUES ('$($localAdmin.userId)','building-1',NOW()) ON DUPLICATE KEY UPDATE assigned_at=NOW();"
    Write-Host "  SQL centrale: binding $($localAdmin.username) <-> building-1 assicurato in central_db.local_admin_buildings" -ForegroundColor Green
}
Write-Host ""

# === STEP 6: Output finale ===
Write-Host "[6/6] Riepilogo utenti creati:" -ForegroundColor Yellow
Write-Host ""

$table = foreach ($u in $users) {
    [PSCustomObject]@{
        Username = $u.username
        Password = $u.password
        Ruolo    = $u.role
        UserId   = $u.userId
    }
}
$table | Format-Table -AutoSize

Write-Host "Comandi di login (su local-server):" -ForegroundColor Cyan
foreach ($u in $users) {
    $json = '{"username":"' + $u.username + '","password":"' + $u.password + '"}'
    Write-Host ("  curl -k -X POST https://localhost:8181/api/auth/login " + `
                 "-H `"Content-Type: application/json`" -d '" + $json + "'  # " + $u.role) -ForegroundColor DarkGray
}
Write-Host ""
Write-Host "Note:" -ForegroundColor Cyan
Write-Host "  - LOCAL_ADMIN ($($localAdmin.username)) e' bindato a building-1 (sia su central che su local)."
Write-Host "  - Login sul CENTRAL (https://localhost:8180) per API admin central (binding building, statistiche globali)."
Write-Host "  - Login sul LOCAL   (https://localhost:8181) per Game Client Emulator (JavaFX) e API admin local."
Write-Host ""
Write-Host "Setup completato." -ForegroundColor Green
