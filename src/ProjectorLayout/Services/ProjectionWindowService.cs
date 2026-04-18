using System.Windows;

namespace ProjectorLayout.Services;

/// <summary>
/// Starts and stops a dedicated fullscreen projection window.
/// </summary>
public sealed class ProjectionWindowService
{
    private Window? _projectionWindow;

    /// <summary>
    /// Opens a minimal fullscreen window on the primary monitor.
    /// </summary>
    public void StartProjection()
    {
        if (_projectionWindow is not null)
        {
            _projectionWindow.Activate();
            return;
        }

        _projectionWindow = new Window
        {
            WindowStyle = WindowStyle.None,
            ResizeMode = ResizeMode.NoResize,
            WindowState = WindowState.Maximized,
            Topmost = true,
            Background = System.Windows.Media.Brushes.Black,
            Content = new System.Windows.Controls.TextBlock
            {
                Text = "Projection Preview",
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 36,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        };

        _projectionWindow.KeyDown += (_, args) =>
        {
            if (args.Key == System.Windows.Input.Key.Escape)
            {
                StopProjection();
            }
        };

        _projectionWindow.Closed += (_, _) => _projectionWindow = null;
        _projectionWindow.Show();
    }

    /// <summary>
    /// Closes the active projection window, if one is open.
    /// </summary>
    public void StopProjection()
    {
        _projectionWindow?.Close();
        _projectionWindow = null;
    }
}
