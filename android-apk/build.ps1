param([string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk", [string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$Jdk) { $Jdk = Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent }
$project = $PSScriptRoot
$workspace = Split-Path $project -Parent
$out = Join-Path $workspace 'output/android-apk'
$work = Join-Path $workspace 'tmp/android-apk-build'
$tools = Join-Path $AndroidSdk 'build-tools/35.0.0'
$platform = Join-Path $AndroidSdk 'platforms/android-35/android.jar'
foreach ($dir in @($out,$work,"$work/res/drawable","$work/compiled","$work/classes","$work/dex")) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
Copy-Item -LiteralPath "$workspace/frontend/src/assets/icons/cofi-192.png" -Destination "$work/res/drawable/cofi.png" -Force
function Run-Tool([string]$exe,[string[]]$arguments) { & $exe @arguments; if ($LASTEXITCODE -ne 0) { throw "Falló $(Split-Path $exe -Leaf) ($LASTEXITCODE)" } }
Run-Tool "$tools/aapt2.exe" @('compile','--dir',"$work/res",'-o',"$work/compiled")
Run-Tool "$tools/aapt2.exe" (@('link','-I',$platform,'--manifest',"$project/AndroidManifest.xml",'-o',"$work/resources.apk",'--auto-add-overlay') + @(Get-ChildItem "$work/compiled/*.flat" | ForEach-Object FullName))
Run-Tool "$Jdk/bin/javac.exe" @('--release','8','-encoding','UTF-8','-classpath',$platform,'-d',"$work/classes","$project/src/app/micofi/android/MainActivity.java")
$classFiles = @(Get-ChildItem "$work/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
$env:JAVA_HOME = $Jdk
Run-Tool "$tools/d8.bat" (@('--lib',$platform,'--min-api','26','--output',"$work/dex") + $classFiles)
Copy-Item -LiteralPath "$work/resources.apk" -Destination "$work/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
$zip = [IO.Compression.ZipFile]::Open("$work/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$work/dex/classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
Run-Tool "$tools/zipalign.exe" @('-f','4',"$work/unsigned.apk","$work/aligned.apk")
$keystore = "$out/cofi-testing.keystore"
# Disposable local test signature, not a production signing identity.
if (!(Test-Path -LiteralPath $keystore)) { Run-Tool "$Jdk/bin/keytool.exe" @('-genkeypair','-keystore',$keystore,'-storepass','android','-keypass','android','-alias','cofi-testing','-dname','CN=COFI Local Testing','-keyalg','RSA','-keysize','2048','-validity','3650') }
Run-Tool "$tools/apksigner.bat" @('sign','--ks',$keystore,'--ks-key-alias','cofi-testing','--ks-pass','pass:android','--key-pass','pass:android','--out',"$out/COFI-pruebas.apk","$work/aligned.apk")
Run-Tool "$tools/apksigner.bat" @('verify','--verbose',"$out/COFI-pruebas.apk")
Get-FileHash -Algorithm SHA256 "$out/COFI-pruebas.apk"
Write-Output "APK generado: $out/COFI-pruebas.apk"
