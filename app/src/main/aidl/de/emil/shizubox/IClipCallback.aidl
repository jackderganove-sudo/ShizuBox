package de.emil.shizubox;

// Called by the Shizuku shell service whenever the clipboard changes.
oneway interface IClipCallback {
    void onClip(String text, String sourcePackage, boolean sensitive, long time);
}
