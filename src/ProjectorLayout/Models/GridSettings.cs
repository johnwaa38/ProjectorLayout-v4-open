namespace ProjectorLayout.Models;

/// <summary>
/// Runtime and persisted settings controlling operator guide overlays.
/// </summary>
public sealed class GridSettings
{
    public bool ShowGrid { get; set; } = true;

    public bool ShowCenterCrosshair { get; set; } = true;
}
