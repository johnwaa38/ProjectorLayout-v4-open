namespace ProjectorLayout.Services;

/// <summary>
/// Placeholder import service surface for minimum DXF support added in later phase.
/// </summary>
public sealed class DxfImportService
{
    /// <summary>
    /// Stores the last selected DXF path until full parsing is implemented.
    /// </summary>
    public string? LastLoadedDxfPath { get; private set; }

    public void SetSelectedPath(string path)
    {
        LastLoadedDxfPath = path;
    }
}
