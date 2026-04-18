namespace ProjectorLayout.Models;

/// <summary>
/// Stores monitor and visual settings used by the projection window.
/// </summary>
public sealed class ProjectionSettings
{
    public string? SelectedMonitorDeviceName { get; set; }

    public double OverlayOpacity { get; set; } = 1.0;

    public double BrightnessMultiplier { get; set; } = 1.0;
}
