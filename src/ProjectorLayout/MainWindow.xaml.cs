namespace ProjectorLayout;

/// <summary>
/// Main application window for ProjectorLayout.
/// Phase 2 adds background image loading and display on the editor surface.
/// </summary>
public partial class MainWindow : System.Windows.Window
{
    /// <summary>
    /// Initializes the main window and loads the scaffold layout.
    /// </summary>
    public MainWindow()
    {
        InitializeComponent();
    }

    /// <summary>
    /// Opens a file dialog, loads an image into memory, and displays it in the editor area.
    /// </summary>
    private void LoadImageButton_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        // Use the Windows open file dialog to let the operator choose an image file.
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Title = "Open Background Image",
            Filter = "Image Files|*.png;*.jpg;*.jpeg;*.bmp;*.gif;*.tif;*.tiff|All Files|*.*",
            CheckFileExists = true,
            Multiselect = false
        };

        if (dialog.ShowDialog(this) != true)
        {
            return;
        }

        try
        {
            // Load the image with OnLoad so the file handle is released immediately after reading.
            var bitmap = new System.Windows.Media.Imaging.BitmapImage();
            bitmap.BeginInit();
            bitmap.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
            bitmap.UriSource = new System.Uri(dialog.FileName, System.UriKind.Absolute);
            bitmap.EndInit();
            bitmap.Freeze();

            // Update the image layer and path label to reflect the selected file.
            BackgroundImage.Source = bitmap;
            LoadedImagePathTextBlock.Text = dialog.FileName;
            EmptyImageHintTextBlock.Visibility = System.Windows.Visibility.Collapsed;
        }
        catch (System.Exception ex)
        {
            // Show a clear error message if the file cannot be parsed as an image.
            System.Windows.MessageBox.Show(
                this,
                $"Unable to load image file.\n\n{ex.Message}",
                "Image Load Error",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Error);
        }
    }
}
