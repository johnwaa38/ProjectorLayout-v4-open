namespace ProjectorLayout.Models;

/// <summary>
/// Represents manual editor transform values applied to overlay-space content.
/// </summary>
public sealed class OverlayTransform
{
    public double TranslateX { get; set; }

    public double TranslateY { get; set; }

    public double Scale { get; set; } = 1.0;

    public double RotationDegrees { get; set; }
}
