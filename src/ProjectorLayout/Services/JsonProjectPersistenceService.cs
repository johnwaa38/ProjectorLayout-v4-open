using System.IO;
using System.Text.Json;
using ProjectorLayout.Models;

namespace ProjectorLayout.Services;

/// <summary>
/// Handles reading and writing project state as JSON with tolerant defaults.
/// </summary>
public sealed class JsonProjectPersistenceService
{
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    /// <summary>
    /// Saves the complete project state to disk.
    /// </summary>
    public void Save(string projectFilePath, ProjectState state)
    {
        string json = JsonSerializer.Serialize(state, _jsonOptions);
        File.WriteAllText(projectFilePath, json);
    }

    /// <summary>
    /// Loads project state from disk and falls back to safe defaults when fields are missing.
    /// </summary>
    public ProjectState Load(string projectFilePath)
    {
        string json = File.ReadAllText(projectFilePath);
        ProjectState? state = JsonSerializer.Deserialize<ProjectState>(json, _jsonOptions);
        return state ?? new ProjectState();
    }
}
