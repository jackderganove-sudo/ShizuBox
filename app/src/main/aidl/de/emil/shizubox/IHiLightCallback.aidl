package de.emil.shizubox;

// Camera/microphone use seen by the shell service, for the HiLight privacy hint.
// json: [{"op":"camera"|"mic","pkg":"…","uid":123}, …] – the current set of active uses.
interface IHiLightCallback {
    oneway void onPrivacy(String json);
}
