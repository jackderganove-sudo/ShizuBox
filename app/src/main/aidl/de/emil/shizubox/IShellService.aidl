package de.emil.shizubox;

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
}
