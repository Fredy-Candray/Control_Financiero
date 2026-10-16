param(
    [Parameter(Mandatory=$true)][string]$GoogleClientFile
)
$ErrorActionPreference = 'Stop'
# Reads a Google OAuth WEB client downloaded by the user. Never prints credentials.
$clientDocument = Get-Content -LiteralPath $GoogleClientFile -Raw | ConvertFrom-Json
if (-not $clientDocument.web.client_id -or -not $clientDocument.web.client_secret) {
    throw 'El archivo debe contener un cliente OAuth de tipo Aplicación web.'
}
$privateConfigPath = Join-Path $PSScriptRoot 'application-local.properties'
$privateConfig = if (Test-Path -LiteralPath $privateConfigPath) { [IO.File]::ReadAllText($privateConfigPath) } else { '' }
function Set-PrivateSetting([string]$settingName, [string]$settingValue) {
    if ($settingValue -match '[\r\n]') { throw 'Valor de configuración inválido.' }
    $escaped = $settingValue.Replace('\', '\\')
    $pattern = '(?m)^' + [regex]::Escape($settingName) + '=.*$'
    $line = $settingName + '=' + $escaped
    if ([regex]::IsMatch($script:privateConfig, $pattern)) {
        $script:privateConfig = [regex]::Replace($script:privateConfig, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $line })
    } else { $script:privateConfig += "`n$line`n" }
}
Set-PrivateSetting 'app.gmail.client-id' ([string]$clientDocument.web.client_id)
Set-PrivateSetting 'app.gmail.client-secret' ([string]$clientDocument.web.client_secret)
if ($privateConfig -notmatch '(?m)^app\.gmail\.encryption-key=.+$') {
    $randomBytes = New-Object byte[] 32
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
    Set-PrivateSetting 'app.gmail.encryption-key' ([Convert]::ToBase64String($randomBytes))
}
Set-PrivateSetting 'app.gmail.redirect-uri' 'http://localhost:8081/api/gmail-bac/callback'
Set-PrivateSetting 'app.gmail.frontend-url' 'http://localhost:4200/cuentas'
[IO.File]::WriteAllText($privateConfigPath, $privateConfig, (New-Object System.Text.UTF8Encoding $false))
Write-Output 'Configuración privada guardada. Reinicia Spring, crea la tarjeta y conecta Gmail desde Cuentas.'
