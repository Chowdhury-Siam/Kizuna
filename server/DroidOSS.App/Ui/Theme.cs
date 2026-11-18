using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using Microsoft.Win32;

namespace DroidOSS.App.Ui;

/// <summary>
/// The window's colours, in light and dark, chosen to match Windows' own app
/// setting — and the phone app, whose dark navy and light blue these echo.
/// </summary>
/// <remarks>
/// Brushes are put into the window's resources under the keys below and used
/// through <c>DynamicResource</c>, so the XAML never names a colour itself.
/// </remarks>
internal static class Theme
{
    private static readonly (string Key, Color Dark, Color Light)[] Palette =
    [
        ("Bg", Rgb(0x0B1728), Rgb(0xE7EFFB)),
        ("Surface", Argb(0xC8, 0x233A56), Argb(0xCD, 0xFFFFFF)),
        ("SurfaceAlt", Argb(0xB0, 0x344E6E), Argb(0xA8, 0xF6FAFF)),
        ("Border", Argb(0x3D, 0xDCEBFF), Argb(0x5C, 0x8AA3C4)),
        ("Text", Rgb(0xF2F7FF), Rgb(0x10233E)),
        ("TextMuted", Rgb(0xB3C6DC), Rgb(0x4B627C)),
        ("Accent", Rgb(0x8BC1FF), Rgb(0x0066E8)),
        ("AccentText", Rgb(0x062345), Rgb(0xFFFFFF)),
        ("Good", Rgb(0x64DBA1), Rgb(0x178657)),
        ("Warn", Rgb(0xFFD08B), Rgb(0x93600D)),
        ("Bad", Rgb(0xFFA5A0), Rgb(0xBD302D)),
        ("PadA", Rgb(0x75DCA0), Rgb(0x288553)),
        ("PadB", Rgb(0xFFA5A0), Rgb(0xBD302D)),
        ("PadX", Rgb(0x8BC1FF), Rgb(0x0066E8)),
        ("PadY", Rgb(0xFFD68A), Rgb(0x997014)),
    ];

    /// <summary>
    /// True when Windows is set to dark mode for apps. <c>DROIDOSS_THEME</c> set
    /// to <c>light</c> or <c>dark</c> overrides it, for screenshots and testing.
    /// </summary>
    public static bool IsDark()
    {
        switch ((Environment.GetEnvironmentVariable("KIZUNA_THEME") ?? Environment.GetEnvironmentVariable("DROIDOSS_THEME"))?.ToLowerInvariant())
        {
            case "light": return false;
            case "dark": return true;
        }

        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return key?.GetValue("AppsUseLightTheme") is int light && light == 0;
        }
        catch (Exception ex) when (ex is System.Security.SecurityException or UnauthorizedAccessException)
        {
            return true;
        }
    }

    /// <summary>Fill <paramref name="resources"/> with the palette for the current mode.</summary>
    public static void Apply(ResourceDictionary resources, bool dark)
    {
        foreach (var (key, darkColour, lightColour) in Palette)
        {
            var brush = new SolidColorBrush(dark ? darkColour : lightColour);
            brush.Freeze();
            resources[key] = brush;
        }
        resources["Bg"] = Gradient(dark ? Rgb(0x0A1526) : Rgb(0xDAE8FC),
            dark ? Rgb(0x213653) : Rgb(0xF2EFFC), dark ? Rgb(0x102A3A) : Rgb(0xD9EFF3));
        resources["Surface"] = Gradient(dark ? Argb(0xCE, 0x415D7B) : Argb(0xF0, 0xFFFFFF),
            dark ? Argb(0xCB, 0x20334D) : Argb(0xAD, 0xF6FAFF), dark ? Argb(0xC0, 0x243C59) : Argb(0xB8, 0xE6F0FF));
        resources["SurfaceAlt"] = Gradient(dark ? Argb(0xB0, 0x4F6D8D) : Argb(0xED, 0xFFFFFF),
            dark ? Argb(0x85, 0x304966) : Argb(0xB5, 0xF2F7FF), dark ? Argb(0xA0, 0x2B4566) : Argb(0x9C, 0xD8E8FC));
        resources["AccentFill"] = Gradient(dark ? Rgb(0xABD4FF) : Rgb(0x338DFF),
            dark ? Rgb(0x8BC1FF) : Rgb(0x0876F5), dark ? Rgb(0x71AAEF) : Rgb(0x005DD7));
        var glow = new RadialGradientBrush(new GradientStopCollection {
            new(dark ? Argb(0x53, 0x70AFFF) : Argb(0x70, 0xFFFFFF), 0), new(Colors.Transparent, 1)
        });
        glow.Freeze();
        resources["AmbientGlow"] = glow;
        resources["GlassRim"] = Gradient(dark ? Argb(0x9A, 0xFFFFFF) : Argb(0xFF, 0xFFFFFF),
            dark ? Argb(0x22, 0xFFFFFF) : Argb(0x55, 0xFFFFFF), dark ? Argb(0x49, 0xB4D5FF) : Argb(0x55, 0x9DB9DB));
    }

    /// <summary>
    /// Dark title bar to match a dark window. Without it Windows draws a white
    /// bar over a dark app, which looks broken.
    /// </summary>
    public static void ApplyTitleBar(Window window, bool dark)
    {
        var handle = new WindowInteropHelper(window).EnsureHandle();
        var value = dark ? 1 : 0;
        // 20 on Windows 11 and recent 10; 19 on older Windows 10 builds.
        if (DwmSetWindowAttribute(handle, 20, ref value, sizeof(int)) != 0)
            DwmSetWindowAttribute(handle, 19, ref value, sizeof(int));
        var rounded = 2; // Windows 11 rounds the frame; older Windows versions ignore this attribute.
        DwmSetWindowAttribute(handle, 33, ref rounded, sizeof(int));
    }

    private static Brush Gradient(Color top, Color middle, Color bottom)
    {
        var brush = new LinearGradientBrush(new GradientStopCollection {
            new(top, 0), new(middle, .48), new(bottom, 1)
        }, new Point(0, 0), new Point(1, 1));
        brush.Freeze();
        return brush;
    }

    private static Color Argb(byte alpha, int rgb) =>
        Color.FromArgb(alpha, (byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);

    private static Color Rgb(int rgb) =>
        Color.FromRgb((byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
}
