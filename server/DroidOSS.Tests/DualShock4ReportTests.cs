using DroidOSS.Core;

namespace DroidOSS.Tests;

public sealed class DualShock4ReportTests
{
    [Fact]
    public void NeutralHasReleasedHatAndCenteredUnsignedAxes()
    {
        var report = DualShock4Report.From(PadState.Neutral);
        Assert.Equal((ushort)8, report.Buttons);
        Assert.Equal((byte)0, report.SpecialButtons);
        Assert.Equal((byte)128, report.LeftX);
        Assert.Equal((byte)128, report.LeftY);
        Assert.Equal((byte)128, report.RightX);
        Assert.Equal((byte)128, report.RightY);
        Assert.Equal((byte)0, report.LeftTrigger);
        Assert.Equal((byte)0, report.RightTrigger);
    }

    [Theory]
    [InlineData(GamepadButtons.A, 0x0028)]
    [InlineData(GamepadButtons.B, 0x0048)]
    [InlineData(GamepadButtons.X, 0x0018)]
    [InlineData(GamepadButtons.Y, 0x0088)]
    [InlineData(GamepadButtons.LeftShoulder, 0x0108)]
    [InlineData(GamepadButtons.RightShoulder, 0x0208)]
    [InlineData(GamepadButtons.Back, 0x1008)]
    [InlineData(GamepadButtons.Start, 0x2008)]
    [InlineData(GamepadButtons.LeftThumb, 0x4008)]
    [InlineData(GamepadButtons.RightThumb, 0x8008)]
    public void ButtonsMapByPositionInsteadOfCopyingXboxBits(GamepadButtons button, int expected)
    {
        var state = new PadState { Buttons = (ushort)button };
        Assert.Equal((ushort)expected, DualShock4Report.From(state).Buttons);
    }

    [Theory]
    [InlineData(0, 8)] [InlineData(1, 0)] [InlineData(2, 4)] [InlineData(4, 6)] [InlineData(8, 2)]
    [InlineData(9, 1)] [InlineData(10, 3)] [InlineData(6, 5)] [InlineData(5, 7)]
    [InlineData(3, 8)] [InlineData(12, 8)] [InlineData(15, 8)]
    [InlineData(7, 6)] [InlineData(11, 2)] [InlineData(13, 0)] [InlineData(14, 4)]
    public void AllDirectionCombinationsResolveToAValidHat(int buttons, int expected)
    {
        Assert.Equal((ushort)expected, DualShock4Report.From(new PadState { Buttons = (ushort)buttons }).Buttons);
    }

    [Fact]
    public void TriggersGuideAndAxesKeepTheirTravelAndReleaseOnTheNextReport()
    {
        var held = new PadState {
            Buttons = (ushort)(GamepadButtons.Guide | GamepadButtons.A),
            LeftTrigger = 128, RightTrigger = 255,
            ThumbLX = short.MinValue, ThumbLY = short.MaxValue,
            ThumbRX = short.MaxValue, ThumbRY = short.MinValue,
        };
        var report = DualShock4Report.From(held);
        Assert.Equal((ushort)0x0c28, report.Buttons);
        Assert.Equal((byte)1, report.SpecialButtons);
        Assert.Equal((byte)128, report.LeftTrigger);
        Assert.Equal((byte)255, report.RightTrigger);
        Assert.Equal((byte)0, report.LeftX);
        Assert.Equal((byte)0, report.LeftY);
        Assert.Equal((byte)255, report.RightX);
        Assert.Equal((byte)255, report.RightY);
        var released = DualShock4Report.From(PadState.Neutral);
        Assert.Equal((ushort)8, released.Buttons);
        Assert.Equal((byte)0, released.SpecialButtons);
        Assert.Equal((byte)0, released.LeftTrigger);
        Assert.Equal((byte)0, released.RightTrigger);
    }
}
