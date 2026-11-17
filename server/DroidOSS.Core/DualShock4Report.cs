namespace DroidOSS.Core;

/// <summary>A driver-independent conversion of the wire state into a DualShock 4 report.</summary>
public readonly record struct DualShock4Report(
    ushort Buttons, byte SpecialButtons, byte LeftTrigger, byte RightTrigger,
    byte LeftX, byte LeftY, byte RightX, byte RightY)
{
    public static DualShock4Report From(in PadState state)
    {
        var buttons = (ushort)Direction(state.Buttons);
        if (state.IsPressed(GamepadButtons.X)) buttons |= 0x0010; // Square
        if (state.IsPressed(GamepadButtons.A)) buttons |= 0x0020; // Cross
        if (state.IsPressed(GamepadButtons.B)) buttons |= 0x0040; // Circle
        if (state.IsPressed(GamepadButtons.Y)) buttons |= 0x0080; // Triangle
        if (state.IsPressed(GamepadButtons.LeftShoulder)) buttons |= 0x0100;
        if (state.IsPressed(GamepadButtons.RightShoulder)) buttons |= 0x0200;
        if (state.LeftTrigger > 0) buttons |= 0x0400;
        if (state.RightTrigger > 0) buttons |= 0x0800;
        if (state.IsPressed(GamepadButtons.Back)) buttons |= 0x1000; // Share
        if (state.IsPressed(GamepadButtons.Start)) buttons |= 0x2000; // Options
        if (state.IsPressed(GamepadButtons.LeftThumb)) buttons |= 0x4000;
        if (state.IsPressed(GamepadButtons.RightThumb)) buttons |= 0x8000;
        return new(buttons, state.IsPressed(GamepadButtons.Guide) ? (byte)1 : (byte)0,
            state.LeftTrigger, state.RightTrigger,
            Axis(state.ThumbLX), VerticalAxis(state.ThumbLY), Axis(state.ThumbRX), VerticalAxis(state.ThumbRY));
    }

    private static byte Axis(short value) => (byte)(((int)value + 32768) >> 8);
    // Wire Y is positive up; DS4 Y is positive down. Preserve an exact neutral of 128.
    private static byte VerticalAxis(short value) => (byte)Math.Clamp((32768 - (int)value) >> 8, 0, 255);

    private static int Direction(ushort buttons)
    {
        var up = (buttons & (ushort)GamepadButtons.DPadUp) != 0;
        var down = (buttons & (ushort)GamepadButtons.DPadDown) != 0;
        var left = (buttons & (ushort)GamepadButtons.DPadLeft) != 0;
        var right = (buttons & (ushort)GamepadButtons.DPadRight) != 0;
        // Conflicting pairs cancel on their axis, instead of generating an invalid hat value.
        var x = (right ? 1 : 0) - (left ? 1 : 0);
        var y = (down ? 1 : 0) - (up ? 1 : 0);
        return (x, y) switch
        {
            (0, -1) => 0, (1, -1) => 1, (1, 0) => 2, (1, 1) => 3,
            (0, 1) => 4, (-1, 1) => 5, (-1, 0) => 6, (-1, -1) => 7,
            _ => 8,
        };
    }
}
