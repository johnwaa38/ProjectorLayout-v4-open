using System.Windows;
using ProjectorLayout.ViewModels;

namespace ProjectorLayout;

/// <summary>
/// Main operator window that hosts editor controls and high-level commands.
/// </summary>
public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = new MainViewModel();
        DataContext = _viewModel;
    }

    private void OnNewProjectClick(object sender, RoutedEventArgs e) => _viewModel.NewProject();

    private void OnOpenProjectClick(object sender, RoutedEventArgs e) => _viewModel.OpenProject();

    private void OnSaveProjectClick(object sender, RoutedEventArgs e) => _viewModel.SaveProject();

    private void OnLoadImageClick(object sender, RoutedEventArgs e) => _viewModel.LoadBackgroundImage();

    private void OnLoadDxfClick(object sender, RoutedEventArgs e) => _viewModel.LoadDxf();

    private void OnStartProjectionClick(object sender, RoutedEventArgs e) => _viewModel.StartProjection();

    private void OnResetCalibrationClick(object sender, RoutedEventArgs e) => _viewModel.ResetCalibration();

    private void OnExitClick(object sender, RoutedEventArgs e) => Close();
}
