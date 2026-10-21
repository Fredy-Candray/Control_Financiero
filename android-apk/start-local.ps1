$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
Set-Location $workspace
function Port-Open([int]$port) {
    $tcp = New-Object Net.Sockets.TcpClient
    try { return $tcp.ConnectAsync('127.0.0.1',$port).Wait(1000) -and $tcp.Connected } catch { return $false } finally { $tcp.Dispose() }
}
$web = 'tmp/android-web-build/browser'
if (!(Test-Path "$web/index.html")) { $web = 'frontend/dist/control-financiero-ui/browser' }
if (!(Test-Path "$web/index.html")) { throw 'Falta compilar el frontend. Revisa android-apk/README.md.' }
$logs = Join-Path $workspace 'output/android-apk'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
if (!(Port-Open 8081)) {
    if (Test-Path backend/COFI_NO_UNIX_SOCKET_DIRECTORY) { throw 'La ruta temporal de compatibilidad debe permanecer inexistente.' }
    # An unavailable AF_UNIX temp directory makes OpenJDK PipeImpl use its TCP fallback.
    # This is process-local; it does not change Windows or firewall settings.
    $java = (Get-Command java.exe).Source
    Start-Process -FilePath $java -ArgumentList @('-Djdk.net.unixdomain.tmpdir=COFI_NO_UNIX_SOCKET_DIRECTORY','-jar','target/control-financiero-0.0.1-SNAPSHOT.jar','--app.gmail.import-enabled=false','--spring.jpa.hibernate.ddl-auto=validate','--spring.main.lazy-initialization=true') -WorkingDirectory (Join-Path $workspace 'backend') -WindowStyle Hidden -RedirectStandardOutput "$logs/backend-test.log" -RedirectStandardError "$logs/backend-test-error.log"
    Write-Output 'Iniciando backend local. La importacion de correo permanece desactivada para estas pruebas.'
}
if (!(Port-Open 8082)) {
    $node = (Get-Command node.exe).Source
    Start-Process -FilePath $node -ArgumentList @('frontend/scripts/serve-mobile.cjs',$web) -WorkingDirectory $workspace -WindowStyle Hidden -RedirectStandardOutput "$logs/mobile-server.log" -RedirectStandardError "$logs/mobile-server-error.log"
}
Write-Output 'Usa la misma red Wi-Fi en el telefono y la laptop. Direcciones de descarga:'
foreach ($network in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
    if ($network.OperationalStatus -ne 'Up') { continue }
    foreach ($address in $network.GetIPProperties().UnicastAddresses) {
        if ($address.Address.AddressFamily -eq 'InterNetwork' -and ![Net.IPAddress]::IsLoopback($address.Address)) {
            Write-Output "http://$($address.Address):8082/instalar ($($network.Name))"
        }
    }
}
Write-Output 'Mantén la laptop encendida. Para iniciar sesion, el backend debe terminar su arranque y la base local debe estar disponible.'
