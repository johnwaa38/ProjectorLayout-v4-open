namespace ProjectorLayout.Models;

/// <summary>
/// Root persisted project model for v1 state restoration.
/// </summary>
public sealed class ProjectState
{
    public int SchemaVersion { get; set; } = 1;

    public string? BackgroundImagePath { get; set; }

    public string? DxfPath { get; set; }

    public OverlayTransform OverlayTransform { get; set; } = new();

    public CalibrationQuad SourceQuad { get; set; } = new();

    public CalibrationQuad DestinationQuad { get; set; } = new();

    public GridSettings GridSettings { get; set; } = new();

    public ProjectionSettings ProjectionSettings { get; set; } = new();
}
