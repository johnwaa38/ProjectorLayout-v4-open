using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace ProjectorLayout.ViewModels;

/// <summary>
/// Lightweight base class for property change notifications in MVVM bindings.
/// </summary>
public abstract class ObservableObject : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    /// <summary>
    /// Sets a field and raises PropertyChanged only when the value actually changes.
    /// </summary>
    protected bool SetProperty<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return false;
        }

        field = value;
        RaisePropertyChanged(propertyName);
        return true;
    }

    /// <summary>
    /// Triggers a property changed notification for the specified property name.
    /// </summary>
    protected void RaisePropertyChanged([CallerMemberName] string? propertyName = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
}
