using System.Text;

namespace DroidOSS.Core;

/// <summary>Optional v1 extension. Legacy HELLO and the 20-byte INPUT stay unchanged.</summary>
public static class ClientHello
{
    public const byte Type = 7;
    public const byte StatusType = 8;
    public const int MaxNameBytes = 48;
    public static bool TryRead(ReadOnlySpan<byte> data, out byte preferred, out string name)
    {
        preferred = Protocol.NoPad; name = "";
        if (data.Length < 5 || data[0] != Protocol.MagicByte || data[1] != Protocol.Version || data[2] != Type) return false;
        if (data[3] != Protocol.NoPad && data[3] >= IPadBackend.MaxPads) return false;
        if (data[4] > MaxNameBytes || data.Length != 5 + data[4]) return false;
        try { name = new UTF8Encoding(false, true).GetString(data[5..]); }
        catch (DecoderFallbackException) { return false; }
        name = new string(name.Where(c => !char.IsControl(c)).ToArray()).Trim();
        preferred = data[3];
        return true;
    }
    public static byte[] Write(byte preferred, string name)
    {
        while (Encoding.UTF8.GetByteCount(name) > MaxNameBytes) name = name[..^1];
        if (name.Length > 0 && char.IsHighSurrogate(name[^1])) name = name[..^1];
        var bytes = Encoding.UTF8.GetBytes(name);
        return [Protocol.MagicByte, Protocol.Version, Type, preferred, (byte)bytes.Length, .. bytes];
    }
    public static int WriteStatus(Span<byte> reply, byte slot, byte occupied)
    {
        reply[0] = Protocol.MagicByte; reply[1] = Protocol.Version;
        reply[2] = StatusType; reply[3] = slot; reply[4] = occupied;
        return 5;
    }
}
