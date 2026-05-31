param(
    [ValidateSet("exe", "msi", "app-image")]
    [string] $Type = "exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDirectory
$ProjectRootPath = (Resolve-Path -LiteralPath $ProjectRoot).Path

$AppName = "Keyword Extractor"
$AppVersion = "1.0.0"
$Vendor = "Keyword Extractor"
$Description = "Desktop keyword line extractor for text and CSV files"
$JarName = "keyword-extractor-1.0-SNAPSHOT.jar"

$TargetDirectory = Join-Path $ProjectRoot "target"
$PackageWorkDirectory = Join-Path $TargetDirectory "jpackage"
$InputDirectory = Join-Path $PackageWorkDirectory "input"
$InstallerDirectory = Join-Path $TargetDirectory "installer"
$SourceIcon = Join-Path $ProjectRoot "src\main\resources\com\KeywordExtractor\basic\app-icon.png"
$WindowsIcon = Join-Path $PackageWorkDirectory "app-icon.ico"
$JarPath = Join-Path $TargetDirectory $JarName

function Remove-BuildDirectory {
    param([Parameter(Mandatory)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $ResolvedPath = (Resolve-Path -LiteralPath $Path).Path
    if (-not $ResolvedPath.StartsWith($ProjectRootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the project: $ResolvedPath"
    }

    Remove-Item -LiteralPath $ResolvedPath -Recurse -Force
}

function Write-IconEntry {
    param(
        [Parameter(Mandatory)][System.IO.BinaryWriter] $Writer,
        [Parameter(Mandatory)][int] $Size,
        [Parameter(Mandatory)][byte[]] $Bytes,
        [Parameter(Mandatory)][int] $Offset
    )

    $IconSize = if ($Size -eq 256) { 0 } else { $Size }
    $Writer.Write([byte] $IconSize)
    $Writer.Write([byte] $IconSize)
    $Writer.Write([byte] 0)
    $Writer.Write([byte] 0)
    $Writer.Write([UInt16] 1)
    $Writer.Write([UInt16] 32)
    $Writer.Write([UInt32] $Bytes.Length)
    $Writer.Write([UInt32] $Offset)
}

function Convert-PngToWindowsIcon {
    param(
        [Parameter(Mandatory)][string] $PngPath,
        [Parameter(Mandatory)][string] $IconPath
    )

    Add-Type -AssemblyName System.Drawing

    $IconSizes = @(16, 24, 32, 48, 64, 128, 256)
    $SourceImage = [System.Drawing.Image]::FromFile($PngPath)
    $Entries = New-Object System.Collections.Generic.List[object]

    try {
        foreach ($Size in $IconSizes) {
            $Bitmap = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $Graphics = [System.Drawing.Graphics]::FromImage($Bitmap)

            try {
                $Graphics.Clear([System.Drawing.Color]::Transparent)
                $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $Graphics.DrawImage($SourceImage, 0, 0, $Size, $Size)

                $Stream = New-Object System.IO.MemoryStream
                $Bitmap.Save($Stream, [System.Drawing.Imaging.ImageFormat]::Png)
                $Entries.Add([PSCustomObject]@{
                    Size = $Size
                    Bytes = $Stream.ToArray()
                })
            }
            finally {
                if ($null -ne $Stream) {
                    $Stream.Dispose()
                }
                $Graphics.Dispose()
                $Bitmap.Dispose()
            }
        }
    }
    finally {
        $SourceImage.Dispose()
    }

    $OutputDirectory = Split-Path -Parent $IconPath
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

    $FileStream = [System.IO.File]::Create($IconPath)
    $Writer = New-Object System.IO.BinaryWriter $FileStream

    try {
        $Writer.Write([UInt16] 0)
        $Writer.Write([UInt16] 1)
        $Writer.Write([UInt16] $Entries.Count)

        $Offset = 6 + (16 * $Entries.Count)
        foreach ($Entry in $Entries) {
            Write-IconEntry -Writer $Writer -Size $Entry.Size -Bytes $Entry.Bytes -Offset $Offset
            $Offset += $Entry.Bytes.Length
        }

        foreach ($Entry in $Entries) {
            $Writer.Write($Entry.Bytes)
        }
    }
    finally {
        $Writer.Dispose()
        $FileStream.Dispose()
    }
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found. Install a full JDK 21 and make sure its bin directory is on PATH."
}

if (-not (Test-Path -LiteralPath $SourceIcon)) {
    throw "Application icon was not found: $SourceIcon"
}

$MavenCommand = Join-Path $ProjectRoot "mvnw.cmd"
if (-not (Test-Path -LiteralPath $MavenCommand)) {
    $MavenCommand = "mvn"
}

Push-Location $ProjectRoot
try {
    & $MavenCommand -q -DskipTests package
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Packaged jar was not found: $JarPath"
}

Remove-BuildDirectory -Path $PackageWorkDirectory
Remove-BuildDirectory -Path $InstallerDirectory
New-Item -ItemType Directory -Force -Path $InputDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $InstallerDirectory | Out-Null

Copy-Item -LiteralPath $JarPath -Destination (Join-Path $InputDirectory $JarName)
Convert-PngToWindowsIcon -PngPath $SourceIcon -IconPath $WindowsIcon

$JPackageArguments = @(
    "--type", $Type,
    "--name", $AppName,
    "--dest", $InstallerDirectory,
    "--input", $InputDirectory,
    "--main-jar", $JarName,
    "--icon", $WindowsIcon,
    "--app-version", $AppVersion,
    "--vendor", $Vendor,
    "--description", $Description,
    "--java-options", "-Dfile.encoding=UTF-8"
)

if ($Type -ne "app-image") {
    $JPackageArguments += @(
        "--win-menu",
        "--win-menu-group", $AppName,
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install"
    )
}

& jpackage @JPackageArguments

Write-Host "Package created in: $InstallerDirectory"
