using ProjectorLayout.Models;

namespace ProjectorLayout.ViewModels;

/// <summary>
/// Bindable wrapper around overlay transform model values.
/// </summary>
public sealed class OverlayTransformViewModel : ObservableObject
{
    private double _translateX;
    private double _translateY;
    private double _scale = 1.0;
    private double _rotationDegrees;

    public OverlayTransformViewModel(OverlayTransform source)
    {
        ApplyFrom(source);
    }

    public double TranslateX
    {
        get => _translateX;
        set => SetProperty(ref _translateX, value);
    }

    public double TranslateY
    {
        get => _translateY;
        set => SetProperty(ref _translateY, value);
    }

    public double Scale
    {
        get => _scale;
        set => SetProperty(ref _scale, value);
    }

    public double RotationDegrees
    {
        get => _rotationDegrees;
        set => SetProperty(ref _rotationDegrees, value);
    }

    public OverlayTransform ToModel()
    {
        return new OverlayTransform
        {
            TranslateX = TranslateX,
            TranslateY = TranslateY,
            Scale = Scale,
            RotationDegrees = RotationDegrees
        };
    }

    public void ApplyFrom(OverlayTransform source)
    {
        TranslateX = source.TranslateX;
        TranslateY = source.TranslateY;
        Scale = source.Scale;
        RotationDegrees = source.RotationDegrees;
    }
}
