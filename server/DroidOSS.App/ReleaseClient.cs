using System.IO;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Text;
using DroidOSS.Core;

namespace DroidOSS.App;

internal sealed class ReleaseClient
{
    public static string Repository => UpdatePolicy.Repository(typeof(ReleaseClient).Assembly.GetCustomAttributes<AssemblyMetadataAttribute>()
        .FirstOrDefault(a => a.Key == "ReleaseRepository")?.Value ?? "Chowdhury-Siam/Kizuna");
    public static string RepositoryUrl => $"https://github.com/{Repository}";
    private static readonly HttpClient Client = new(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = TimeSpan.FromMinutes(10) };
    private static async Task<HttpResponseMessage> OpenAsync(string url, CancellationToken token)
    {
        for (var count = 0; count < 6; count++)
        {
            var uri = new Uri(url);
            if (uri.Scheme != "https" || uri.Port != 443 || uri.UserInfo.Length != 0 || uri.Host is not ("api.github.com" or "github.com" or "release-assets.githubusercontent.com" or "objects.githubusercontent.com")) throw new InvalidDataException("Untrusted update host");
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.UserAgent.ParseAdd("Kizuna-Updater");
            var response = await Client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token);
            if ((int)response.StatusCode is 301 or 302 or 303 or 307 or 308)
            {
                var next = response.Headers.Location;
                response.Dispose();
                url = new Uri(uri, next ?? throw new InvalidDataException("Invalid download redirect")).AbsoluteUri;
                continue;
            }
            if (!response.IsSuccessStatusCode)
            {
                var code = response.StatusCode;
                response.Dispose();
                throw new IOException(code == HttpStatusCode.NotFound ? $"No stable release is available yet for {Repository}." : $"GitHub returned HTTP {(int)code}. Try again later.");
            }
            return response;
        }
        throw new IOException("Too many update redirects");
    }
    private static async Task<string> TextAsync(string url, int limit, CancellationToken token)
    {
        using var response = await OpenAsync(url, token);
        await using var stream = await response.Content.ReadAsStreamAsync(token);
        using var output = new MemoryStream();
        var buffer = new byte[8192];
        while (true)
        {
            var n = await stream.ReadAsync(buffer, token);
            if (n == 0) break;
            if (output.Length + n > limit) throw new InvalidDataException("Update metadata is too large");
            output.Write(buffer, 0, n);
        }
        return Encoding.UTF8.GetString(output.ToArray());
    }
    public static async Task<AvailableUpdate?> CheckAsync(string version, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromMinutes(2)); token = timeout.Token;
        var release = UpdatePolicy.Release(await TextAsync($"https://api.github.com/repos/{Repository}/releases/latest", 1024 * 1024, token), Repository);
        return UpdatePolicy.Select(await TextAsync(UpdatePolicy.AssetUrl(Repository, release.Tag, "update.json"), 65536, token), release, Repository, version);
    }
    public static async Task<string> DownloadAsync(AvailableUpdate update, IProgress<int> progress, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromMinutes(10)); token = timeout.Token;
        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Kizuna", "Updates");
        Directory.CreateDirectory(directory);
        var final = Path.Combine(directory, update.Asset.Name);
        var part = final + ".part";
        try
        {
            using var response = await OpenAsync(update.Url, token);
            await using (var input = await response.Content.ReadAsStreamAsync(token))
            await using (var output = File.Create(part))
            {
                var buffer = new byte[65536]; long total = 0; var last = -1;
                while (true)
                {
                    var n = await input.ReadAsync(buffer, token); if (n == 0) break;
                    total += n;
                    if (total > update.Asset.Size || total > UpdatePolicy.MaxDownload) throw new InvalidDataException("Update exceeds expected size");
                    await output.WriteAsync(buffer.AsMemory(0, n), token);
                    var percent = (int)(total * 100 / update.Asset.Size);
                    if (percent != last) { progress.Report(percent); last = percent; }
                }
            }
            await UpdatePolicy.VerifyAsync(part, update.Asset, token);
            File.Move(part, final, overwrite: true);
            return final;
        }
        finally { if (File.Exists(part)) File.Delete(part); }
    }
}
