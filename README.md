# ProjectorLayout-v4-open

ProjectorLayout is a WPF desktop application targeting .NET 8.

## Current status

This repository is currently at **Phase 2 (image load + display)**.

Implemented so far:
- Visual Studio solution file
- WPF project targeting `net8.0-windows`
- `App.xaml` / `App.xaml.cs`
- `MainWindow.xaml` / `MainWindow.xaml.cs`
- Scaffold layout regions for:
  - control panel
  - image area
  - overlay canvas
- Image loading through a file dialog (`Load Image...` button)
- Display of the selected image in the editor surface with aspect-ratio preserving scaling

Not implemented yet: transforms, calibration, projection, DXF import, or persistence.

## Build and run (Windows)

```bash
dotnet restore ProjectorLayout.sln
dotnet build ProjectorLayout.sln
dotnet run --project src/ProjectorLayout/ProjectorLayout.csproj
```
