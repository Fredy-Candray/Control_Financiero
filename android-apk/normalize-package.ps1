param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($Path,[IO.Compression.ZipArchiveMode]::Update)
try {
    # Windows aapt2 can emit backslashes for -A assets. Android AssetManager
    # requires forward slashes regardless of the host used for compilation.
    $entries = @($archive.Entries | Where-Object { $_.FullName.Contains('\') })
    foreach ($entry in $entries) {
        $name = $entry.FullName.Replace('\','/')
        if ($archive.GetEntry($name)) { throw 'Duplicate normalized package path' }
        $target = $archive.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
        $inputStream = $entry.Open()
        $outputStream = $target.Open()
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        $entry.Delete()
    }
    if (!$archive.GetEntry('assets/www/index.html')) { throw 'APK has no bundled interface' }
} finally { $archive.Dispose() }
