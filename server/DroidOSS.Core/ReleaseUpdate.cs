using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace DroidOSS.Core;

public sealed record UpdateAsset(string Name, string Sha256, long Size);
public sealed record UpdateManifest(int Schema, string Repository, string Version, long AndroidVersionCode, Dictionary<string, UpdateAsset> Assets);
public sealed record GithubAsset(string Name, [property: JsonPropertyName("browser_download_url")] string Url);
public sealed record GithubRelease([property: JsonPropertyName("tag_name")] string Tag, bool Draft, bool Prerelease, GithubAsset[] Assets);
public sealed record AvailableUpdate(UpdateManifest Manifest, UpdateAsset Asset, string Url);

/// <summary>Shared, platform-independent validation for stable release metadata and downloads.</summary>
public static class UpdatePolicy
{
    public const long MaxDownload = 512L * 1024 * 1024;
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    public static Version StableVersion(string value)
    {
        if (!Regex.IsMatch(value, @"\A(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\z")) throw new InvalidDataException("Invalid stable version");
        return Version.Parse(value);
    }
    public static string Repository(string value)
    {
        if (!Regex.IsMatch(value, @"\A[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+\z") || value.Split('/')[1] is "." or "..") throw new InvalidDataException("Invalid release repository");
        return value;
    }
    public static string AssetUrl(string repo, string tag, string name)
    {
        Repository(repo);
        if (!tag.StartsWith('v')) throw new InvalidDataException("Invalid release tag");
        StableVersion(tag[1..]);
        if (!Regex.IsMatch(name, @"\A[A-Za-z0-9._-]+\z") || name is "." or "..") throw new InvalidDataException("Invalid asset name");
        return $"https://github.com/{repo}/releases/download/{tag}/{name}";
    }
    public static GithubRelease Release(string raw, string repo)
    {
        Repository(repo);
        var release = JsonSerializer.Deserialize<GithubRelease>(raw, Json) ?? throw new InvalidDataException("Invalid GitHub release");
        if (release.Draft || release.Prerelease || release.Tag is null || !release.Tag.StartsWith('v')) throw new InvalidDataException("Only stable releases can update Kizuna");
        StableVersion(release.Tag[1..]);
        var expected = AssetUrl(repo, release.Tag, "update.json");
        if (release.Assets is null || release.Assets.Count(a => a.Name == "update.json") != 1 || release.Assets.Single(a => a.Name == "update.json").Url != expected) throw new InvalidDataException("Missing or untrusted update manifest");
        return release;
    }
    public static AvailableUpdate? Select(string raw, GithubRelease release, string repo, string currentVersion)
    {
        var manifest = JsonSerializer.Deserialize<UpdateManifest>(raw, Json) ?? throw new InvalidDataException("Invalid update manifest");
        if (manifest.Schema != 1 || manifest.Repository != Repository(repo) || release.Tag != $"v{manifest.Version}" || release.Draft || release.Prerelease) throw new InvalidDataException("Update repository/version mismatch");
        if (StableVersion(manifest.Version) <= StableVersion(currentVersion)) return null;
        if (manifest.Assets is null || !manifest.Assets.TryGetValue("windows", out var asset)) throw new InvalidDataException("No Windows installer in this release");
        if (asset.Name != $"Kizuna-v{manifest.Version}-Setup.exe" || asset.Size <= 0 || asset.Size > MaxDownload || asset.Sha256 is null || !Regex.IsMatch(asset.Sha256, @"\A[a-f0-9]{64}\z")) throw new InvalidDataException("Invalid Windows installer integrity data");
        var url = AssetUrl(repo, release.Tag, asset.Name);
        if (release.Assets.Count(a => a.Name == asset.Name && a.Url == url) != 1) throw new InvalidDataException("Installer does not belong to this release");
        return new(manifest, asset, url);
    }
    public static async Task VerifyAsync(string path, UpdateAsset asset, CancellationToken token = default)
    {
        if (new FileInfo(path).Length != asset.Size) throw new InvalidDataException("Incomplete update download");
        await using var stream = File.OpenRead(path);
        var hash = Convert.ToHexStringLower(await SHA256.HashDataAsync(stream, token));
        if (hash != asset.Sha256) throw new InvalidDataException("Update checksum does not match");
    }
}
