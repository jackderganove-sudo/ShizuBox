package de.emil.shizubox;

import de.emil.shizubox.IClipCallback;

interface IShellService {
    // Reserved transaction code Shizuku uses to tear down the user service.
    void destroy() = 16777114;

    // Runs `sh -c cmd` as the Shizuku user (shell or root).
    // Result: "<exitCode>\0<stdout>\0<stderr>"
    String exec(String cmd) = 1;

    // Copies the file behind `fd` to `path` (writable by the Shizuku user, e.g. /data/local/tmp).
    // Used by the APK installer, because the shell user cannot read app-private files.
    // Returns the number of bytes written, or -1 on error.
    long writeFile(in ParcelFileDescriptor fd, String path) = 2;

    // Like exec, but kills the command and everything it started after `limitMs`.
    // Result: "<exitCode>\0<stdout>\0<stderr>", exit code 124 on timeout.
    String execLimited(String cmd, long limitMs) = 3;

    // Clipboard history: the shell user may read the clipboard in the background
    // (READ_CLIPBOARD_IN_BACKGROUND); the service listens for changes and reports them.
    void watchClipboard(in IClipCallback callback) = 4;
    void unwatchClipboard() = 5;

    // Volume per app: the shell user holds MODIFY_AUDIO_ROUTING, so it sees every player with its
    // app and may set each player's volume. Lines: "uid|piid|state|usage".
    String listPlayers() = 6;
    // 0..1; 1 removes the setting. Re-applied whenever playback changes (new players start at 1).
    void setAppVolume(int uid, float volume) = 7;
}
