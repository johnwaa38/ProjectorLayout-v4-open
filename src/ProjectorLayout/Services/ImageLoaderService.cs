using System;
using System.IO;
using System.Windows.Media.Imaging;

namespace ProjectorLayout.Services;

/// <summary>
/// Loads image assets from disk into WPF image sources with clear error behavior.
/// </summary>
public sealed class ImageLoaderService
{
    public BitmapImage LoadFromPath(string imagePath)
    {
        if (!File.Exists(imagePath))
        {
            throw new FileNotFoundException("Background image file was not found.", imagePath);
        }

        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.UriSource = new Uri(imagePath, UriKind.Absolute);
        bitmap.EndInit();
        bitmap.Freeze();
        return bitmap;
    }
}
