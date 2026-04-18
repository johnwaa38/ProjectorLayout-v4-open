namespace ProjectorLayout.Models;

/// <summary>
/// Stores four calibration corners in the required order: UL, UR, LR, LL.
/// </summary>
public sealed class CalibrationQuad
{
    public Point2D? UpperLeft { get; set; }

    public Point2D? UpperRight { get; set; }

    public Point2D? LowerRight { get; set; }

    public Point2D? LowerLeft { get; set; }

    /// <summary>
    /// Returns true only when all four corners have been assigned.
    /// </summary>
    public bool IsComplete =>
        UpperLeft is not null &&
        UpperRight is not null &&
        LowerRight is not null &&
        LowerLeft is not null;
}
