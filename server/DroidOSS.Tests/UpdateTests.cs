using System.Text.Json;
using DroidOSS.Core;

namespace DroidOSS.Tests;

public class UpdateTests
{
    private const string Repo = "Chowdhury-Siam/Kizuna";
    private static string Fixture(string name) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", name));
    private static GithubRelease Release() => UpdatePolicy.Release(Fixture("github-release.json"), Repo);
    [Fact]
    public void SharedManifestSelectsOnlyNewerStableInstaller()
    {
        var update = UpdatePolicy.Select(Fixture("update.json"), Release(), Repo, "1.0.0");
        Assert.NotNull(update);
        Assert.Equal("Kizuna-v1.1.0-Setup.exe", update.Asset.Name);
        Assert.Null(UpdatePolicy.Select(Fixture("update.json"), Release(), Repo, "1.1.0"));
        Assert.Null(UpdatePolicy.Select(Fixture("update.json"), Release(), Repo, "2.0.0"));
        Assert.True(UpdatePolicy.StableVersion("1.10.0") > UpdatePolicy.StableVersion("1.9.9"));
    }
    [Theory]
    [InlineData("1.2.3-beta")]
    [InlineData("v1.2.3")]
    [InlineData("01.2.3")]
    [InlineData("1.2")]
    [InlineData("1.2.3.4")]
    public void NonStableVersionsAreRejected(string version) => Assert.Throws<InvalidDataException>(() => UpdatePolicy.StableVersion(version));
    [Fact]
    public void DraftPrereleaseAndExternalUrlsAreRejected()
    {
        var raw = Fixture("github-release.json");
        foreach (var modified in new[] { raw.Replace("\"draft\": false", "\"draft\": true"), raw.Replace("\"prerelease\": false", "\"prerelease\": true"), raw.Replace("https://github.com", "https://evil.example") })
            Assert.Throws<InvalidDataException>(() => UpdatePolicy.Release(modified, Repo));
    }
    [Fact]
    public void WrongRepositoryVersionSchemaSizeAndFilenameAreRejected()
    {
        var raw = Fixture("update.json");
        foreach (var modified in new[] { raw.Replace("Chowdhury-Siam/Kizuna", "Other/Repo"), raw.Replace("1.1.0", "1.2.0"), raw.Replace("\"schema\": 1", "\"schema\": 2"), raw.Replace("\"size\": 3", "\"size\": -1"), raw.Replace("Setup.exe", "../Setup.exe") })
            Assert.Throws<InvalidDataException>(() => UpdatePolicy.Select(modified, Release(), Repo, "1.0.0"));
    }
    [Fact]
    public async Task TruncatedAndTamperedDownloadsAreRejected()
    {
        var asset = UpdatePolicy.Select(Fixture("update.json"), Release(), Repo, "1.0.0")!.Asset;
        var path = Path.GetTempFileName();
        try
        {
            await File.WriteAllTextAsync(path, "EXE"); await UpdatePolicy.VerifyAsync(path, asset);
            await File.WriteAllTextAsync(path, "EX"); await Assert.ThrowsAsync<InvalidDataException>(() => UpdatePolicy.VerifyAsync(path, asset));
            await File.WriteAllTextAsync(path, "BAD"); await Assert.ThrowsAsync<InvalidDataException>(() => UpdatePolicy.VerifyAsync(path, asset));
        }
        finally { File.Delete(path); }
    }
    [Theory]
    [InlineData("owner/..")]
    [InlineData("owner/repo?x")]
    [InlineData("https://github.com/a/b")]
    public void RepositoryCannotEscapeGithub(string value) => Assert.Throws<InvalidDataException>(() => UpdatePolicy.Repository(value));
}
