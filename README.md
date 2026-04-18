# ProjectorLayout-v4-open

ProjectorLayout is a WPF desktop application targeting .NET 8.

## Current status

This repository is currently at **Phase 1 (solution + app scaffold)**.

Implemented in this phase:
- Visual Studio solution file
- WPF project targeting `net8.0-windows`
- `App.xaml` / `App.xaml.cs`
- `MainWindow.xaml` / `MainWindow.xaml.cs`
- Scaffold layout regions for:
  - control panel
  - image area
  - overlay canvas

No functional image loading, transforms, calibration, projection, DXF import, or persistence are included in Phase 1.

## Build and run (Windows)

```bash
dotnet restore ProjectorLayout.sln
dotnet build ProjectorLayout.sln
dotnet run --project src/ProjectorLayout/ProjectorLayout.csproj
```
