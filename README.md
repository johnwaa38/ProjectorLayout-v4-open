# ProjectorLayout-v4-open

Open version of **ProjectorLayout**, a WPF desktop application targeting **.NET 8** for projector alignment workflows.

## Project structure

- `ProjectorLayout.sln` — Visual Studio solution file.
- `src/ProjectorLayout/ProjectorLayout.csproj` — WPF app project.
- `src/ProjectorLayout/MainWindow.xaml` — main operator UI.
- `src/ProjectorLayout/ViewModels` — state and command logic.
- `src/ProjectorLayout/Models` — persistence and geometry models.
- `src/ProjectorLayout/Services` — core I/O and projection services.

## Build

On Windows with .NET 8 SDK installed:

```bash
dotnet restore ProjectorLayout.sln
dotnet build ProjectorLayout.sln
```
