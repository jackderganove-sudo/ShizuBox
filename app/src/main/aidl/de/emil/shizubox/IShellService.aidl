package de.emil.shizubox;

interface IShellService {
    // Reserved transaction code Shizuku uses to tear down the user service.
    void destroy() = 16777114;

    // Runs `sh -c cmd` as the Shizuku user (shell or root).
    // Result: "<exitCode>\0<stdout>\0<stderr>"
    String exec(String cmd) = 1;
}
