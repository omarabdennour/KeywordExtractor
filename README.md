# Keyword Extractor

Desktop JavaFX application for extracting matching lines from multiple `.txt` and `.csv` files.

## Features

- Select multiple text or CSV files.
- Enter a keyword and press **Start lookup** to scan.
- Show matching lines in a results text area.
- Optional case-sensitive matching.
- Optional file name and line number prefixes.
- Copy all results to the clipboard.
- Windows packaging script using `jpackage`.

## Requirements

- JDK 21 with `jpackage` available on `PATH`
- Windows PowerShell

## Run From Source

```powershell
.\mvnw.cmd clean javafx:run
```

## Build The Jar

```powershell
.\mvnw.cmd -DskipTests package
```

The jar is created in:

```text
target\keyword-extractor-1.0-SNAPSHOT.jar
```

## Build Windows Package

Create an application image:

```powershell
.\scripts\package-windows.ps1 -Type app-image
```

Create an installer executable:

```powershell
.\scripts\package-windows.ps1 -Type exe
```

Create an MSI installer:

```powershell
.\scripts\package-windows.ps1 -Type msi
```

Packages are written to:

```text
target\installer
```

## Usage

1. Click **Select files**.
2. Choose one or more `.txt` or `.csv` files.
3. Type the keyword to search for.
4. Press **Start lookup**.
5. Review the matching lines in **Live Results**.
6. Press **Copy results** to copy the output.

## Notes

- Files are read as UTF-8.
- Matching is line-based.
- The app does not modify selected files.
