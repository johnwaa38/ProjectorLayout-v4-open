using System;
using System.IO;
using System.Windows;
using System.Windows.Media;
using Microsoft.Win32;
using ProjectorLayout.Models;
using ProjectorLayout.Services;

namespace ProjectorLayout.ViewModels;

/// <summary>
/// Central view model coordinating project lifecycle, asset loading, and operator controls.
/// </summary>
public sealed class MainViewModel : ObservableObject
{
    private readonly JsonProjectPersistenceService _persistenceService = new();
    private readonly ImageLoaderService _imageLoaderService = new();
    private readonly ProjectionWindowService _projectionWindowService = new();
    private readonly DxfImportService _dxfImportService = new();

    private ProjectState _currentState = new();
    private string? _currentProjectFilePath;
    private ImageSource? _backgroundImage;
    private string _calibrationStatus = "Calibration pending: choose Upper Left first.";

    public MainViewModel()
    {
        OverlayTransform = new OverlayTransformViewModel(_currentState.OverlayTransform);
    }

    /// <summary>
    /// Background image shown on the editor surface.
    /// </summary>
    public ImageSource? BackgroundImage
    {
        get => _backgroundImage;
        private set => SetProperty(ref _backgroundImage, value);
    }

    /// <summary>
    /// Manual transform values exposed for direct operator editing.
    /// </summary>
    public OverlayTransformViewModel OverlayTransform { get; }

    public string CalibrationStatus
    {
        get => _calibrationStatus;
        set => SetProperty(ref _calibrationStatus, value);
    }

    public bool ShowGrid
    {
        get => _currentState.GridSettings.ShowGrid;
        set
        {
            if (_currentState.GridSettings.ShowGrid == value)
            {
                return;
            }

            _currentState.GridSettings.ShowGrid = value;
            RaisePropertyChanged();
        }
    }

    public bool ShowCenterCrosshair
    {
        get => _currentState.GridSettings.ShowCenterCrosshair;
        set
        {
            if (_currentState.GridSettings.ShowCenterCrosshair == value)
            {
                return;
            }

            _currentState.GridSettings.ShowCenterCrosshair = value;
            RaisePropertyChanged();
        }
    }

    public double OverlayOpacity
    {
        get => _currentState.ProjectionSettings.OverlayOpacity;
        set
        {
            if (Math.Abs(_currentState.ProjectionSettings.OverlayOpacity - value) < 0.0001)
            {
                return;
            }

            _currentState.ProjectionSettings.OverlayOpacity = value;
            RaisePropertyChanged();
        }
    }

    public double BrightnessMultiplier
    {
        get => _currentState.ProjectionSettings.BrightnessMultiplier;
        set
        {
            if (Math.Abs(_currentState.ProjectionSettings.BrightnessMultiplier - value) < 0.0001)
            {
                return;
            }

            _currentState.ProjectionSettings.BrightnessMultiplier = value;
            RaisePropertyChanged();
        }
    }

    /// <summary>
    /// Creates a clean in-memory project and resets visible editor state.
    /// </summary>
    public void NewProject()
    {
        _currentState = new ProjectState();
        _currentProjectFilePath = null;
        BackgroundImage = null;
        CalibrationStatus = "Calibration pending: choose Upper Left first.";
        OverlayTransform.ApplyFrom(_currentState.OverlayTransform);
        RaisePropertyChanged(nameof(ShowGrid));
        RaisePropertyChanged(nameof(ShowCenterCrosshair));
        RaisePropertyChanged(nameof(OverlayOpacity));
        RaisePropertyChanged(nameof(BrightnessMultiplier));
    }

    /// <summary>
    /// Opens an existing .plproj JSON project and repopulates the view model.
    /// </summary>
    public void OpenProject()
    {
        var dialog = new OpenFileDialog
        {
            Filter = "Projector Layout Project (*.plproj)|*.plproj|JSON Files (*.json)|*.json|All Files (*.*)|*.*"
        };

        if (dialog.ShowDialog() != true)
        {
            return;
        }

        try
        {
            _currentState = _persistenceService.Load(dialog.FileName);
            _currentProjectFilePath = dialog.FileName;
            OverlayTransform.ApplyFrom(_currentState.OverlayTransform);
            TryLoadBackground(_currentState.BackgroundImagePath);
            RaisePropertyChanged(nameof(ShowGrid));
            RaisePropertyChanged(nameof(ShowCenterCrosshair));
            RaisePropertyChanged(nameof(OverlayOpacity));
            RaisePropertyChanged(nameof(BrightnessMultiplier));
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Failed to open project file.\n\n{ex.Message}", "Open Project", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    /// <summary>
    /// Saves project JSON to existing path or prompts for a new file path.
    /// </summary>
    public void SaveProject()
    {
        if (string.IsNullOrWhiteSpace(_currentProjectFilePath))
        {
            var dialog = new SaveFileDialog
            {
                Filter = "Projector Layout Project (*.plproj)|*.plproj|JSON Files (*.json)|*.json",
                DefaultExt = "plproj"
            };

            if (dialog.ShowDialog() != true)
            {
                return;
            }

            _currentProjectFilePath = dialog.FileName;
        }

        try
        {
            _currentState.OverlayTransform = OverlayTransform.ToModel();
            _persistenceService.Save(_currentProjectFilePath, _currentState);
            MessageBox.Show("Project saved successfully.", "Save Project", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Failed to save project file.\n\n{ex.Message}", "Save Project", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    /// <summary>
    /// Prompts for a background image and updates editor state.
    /// </summary>
    public void LoadBackgroundImage()
    {
        var dialog = new OpenFileDialog
        {
            Filter = "Image Files|*.png;*.jpg;*.jpeg;*.bmp;*.tif;*.tiff|All Files (*.*)|*.*"
        };

        if (dialog.ShowDialog() != true)
        {
            return;
        }

        TryLoadBackground(dialog.FileName);
    }

    /// <summary>
    /// Records selected DXF path until parser/render pipeline is added.
    /// </summary>
    public void LoadDxf()
    {
        var dialog = new OpenFileDialog
        {
            Filter = "DXF Files (*.dxf)|*.dxf|All Files (*.*)|*.*"
        };

        if (dialog.ShowDialog() != true)
        {
            return;
        }

        _dxfImportService.SetSelectedPath(dialog.FileName);
        _currentState.DxfPath = dialog.FileName;
        MessageBox.Show("DXF file selected and tracked in project state.", "DXF", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    /// <summary>
    /// Starts the fullscreen projection preview window.
    /// </summary>
    public void StartProjection()
    {
        _projectionWindowService.StartProjection();
    }

    /// <summary>
    /// Clears both calibration quads and resets the operator status message.
    /// </summary>
    public void ResetCalibration()
    {
        _currentState.SourceQuad = new CalibrationQuad();
        _currentState.DestinationQuad = new CalibrationQuad();
        CalibrationStatus = "Calibration reset: choose Upper Left first.";
    }

    private void TryLoadBackground(string? imagePath)
    {
        if (string.IsNullOrWhiteSpace(imagePath))
        {
            BackgroundImage = null;
            _currentState.BackgroundImagePath = null;
            return;
        }

        try
        {
            BackgroundImage = _imageLoaderService.LoadFromPath(imagePath);
            _currentState.BackgroundImagePath = imagePath;
        }
        catch (FileNotFoundException)
        {
            BackgroundImage = null;
            _currentState.BackgroundImagePath = null;
            MessageBox.Show("Background image file is missing.", "Background Image", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        catch (Exception ex)
        {
            BackgroundImage = null;
            MessageBox.Show($"Failed to load background image.\n\n{ex.Message}", "Background Image", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
}
