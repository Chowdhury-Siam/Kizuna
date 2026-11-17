using DroidOSS.Core;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;

namespace DroidOSS.ViGEm;

/// <summary>Four virtual Xbox 360 or DualShock 4 pads, backed by the existing ViGEmBus driver.</summary>
public sealed class ViGEmPadBackend : IPadBackend
{
    private readonly ViGEmClient _client;
    private readonly VirtualControllerType _controllerType;
    private readonly IXbox360Controller?[] _xboxPads = new IXbox360Controller?[IPadBackend.MaxPads];
    private readonly IDualShock4Controller?[] _psPads = new IDualShock4Controller?[IPadBackend.MaxPads];
    private bool _disposed;

    public event EventHandler<RumbleEventArgs>? RumbleReceived;

    public ViGEmPadBackend(VirtualControllerType controllerType = VirtualControllerType.Xbox360)
    {
        if (!Enum.IsDefined(controllerType)) throw new ArgumentOutOfRangeException(nameof(controllerType));
        _controllerType = controllerType;
        try { _client = new ViGEmClient(); }
        catch (Exception ex)
        {
            throw new PadDriverUnavailableException(
                "Could not connect to the ViGEmBus driver. It is probably not installed.", ex);
        }
    }

    public void Connect(int slot)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
        ValidateSlot(slot);
        if (_xboxPads[slot] is not null || _psPads[slot] is not null) return;

        if (_controllerType == VirtualControllerType.PlayStation4)
        {
            var pad = _client.CreateDualShock4Controller();
            pad.AutoSubmitReport = false;
            // The pinned ViGEm SDK exposes motor feedback through this event for both device types.
            // DS4 feedback is best-effort; it does not provide DualSense adaptive haptics.
#pragma warning disable CS0618
            pad.FeedbackReceived += (_, e) =>
                RumbleReceived?.Invoke(this, new RumbleEventArgs(slot, e.LargeMotor, e.SmallMotor));
#pragma warning restore CS0618
            try
            {
                pad.Connect();
                _psPads[slot] = pad;
                Submit(slot, PadState.Neutral);
            }
            catch
            {
                _psPads[slot] = null;
                try { pad.Disconnect(); } catch { }
                (pad as IDisposable)?.Dispose();
                throw;
            }
        }
        else
        {
            var pad = _client.CreateXbox360Controller();
            pad.AutoSubmitReport = false;
            pad.FeedbackReceived += (_, e) =>
                RumbleReceived?.Invoke(this, new RumbleEventArgs(slot, e.LargeMotor, e.SmallMotor));
            try
            {
                pad.Connect();
                _xboxPads[slot] = pad;
                Submit(slot, PadState.Neutral);
            }
            catch
            {
                _xboxPads[slot] = null;
                try { pad.Disconnect(); } catch { }
                (pad as IDisposable)?.Dispose();
                throw;
            }
        }
    }

    public void Submit(int slot, in PadState state)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
        ValidateSlot(slot);
        if (_psPads[slot] is { } ps)
        {
            var report = DualShock4Report.From(in state);
            ps.SetButtonsFull(report.Buttons);
            ps.SetSpecialButtonsFull(report.SpecialButtons);
            ps.LeftTrigger = report.LeftTrigger;
            ps.RightTrigger = report.RightTrigger;
            ps.LeftThumbX = report.LeftX;
            ps.LeftThumbY = report.LeftY;
            ps.RightThumbX = report.RightX;
            ps.RightThumbY = report.RightY;
            ps.SubmitReport();
        }
        else if (_xboxPads[slot] is { } xbox)
        {
            xbox.SetButtonsFull(state.Buttons);
            xbox.LeftTrigger = state.LeftTrigger;
            xbox.RightTrigger = state.RightTrigger;
            xbox.LeftThumbX = state.ThumbLX;
            xbox.LeftThumbY = state.ThumbLY;
            xbox.RightThumbX = state.ThumbRX;
            xbox.RightThumbY = state.ThumbRY;
            xbox.SubmitReport();
        }
    }

    public void Disconnect(int slot)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
        ValidateSlot(slot);
        // Neutralize even when a caller disconnects directly instead of going through SessionManager.
        try { Submit(slot, PadState.Neutral); }
        finally
        {
            var ps = _psPads[slot];
            var xbox = _xboxPads[slot];
            _psPads[slot] = null;
            _xboxPads[slot] = null;
            try { ps?.Disconnect(); }
            finally
            {
                ps?.Dispose();
                try { xbox?.Disconnect(); }
                finally { (xbox as IDisposable)?.Dispose(); }
            }
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        for (var slot = 0; slot < IPadBackend.MaxPads; slot++)
            try { Disconnect(slot); } catch { /* Best-effort shutdown of every remaining slot. */ }
        _disposed = true;
        _client.Dispose();
    }

    private static void ValidateSlot(int slot)
    {
        ArgumentOutOfRangeException.ThrowIfNegative(slot);
        ArgumentOutOfRangeException.ThrowIfGreaterThanOrEqual(slot, IPadBackend.MaxPads);
    }
}
