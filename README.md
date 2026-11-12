# KarooSwitches

Shared library for the Karoo extensions by anpaiss: a **Switches** data field
that opens a popup with one ON/OFF button per switch of every installed
extension (PoiAhead "Water alerts", AutoLoop "Auto Loop", ...). Each extension
ships the field, so whichever one you install first already gives you the popup;
install another one and its switch shows up in the same popup.

## Protocol

Fixed with the first release, additions only from here on: different versions
of the extensions live on the same device.

* A switch is an exported `BroadcastReceiver` extending `SwitchReceiver`, with
  an intent filter on `com.anpaiss.karooswitches.SWITCH`, protected by the
  signature-level permission `com.anpaiss.karooswitches.CONTROL` and a
  meta-data `com.anpaiss.karooswitches.label` for the button caption.
* The popup discovers switches with `PackageManager.queryBroadcastReceivers`
  and talks to them with ordered broadcasts: extra `op` = `get` or `toggle`,
  answer in the result extras as boolean `on`.
* All our extensions are signed with the same release key, so only they can see
  and flip the switches. A debug build and a release build do not see each other.
* 0.2.0: a receiver with meta-data `com.anpaiss.karooswitches.kind` = `time`
  extends `TimeReceiver`: an ON/OFF switch plus, while ON, an adjustable time
  (BetterETA's mandatory arrival time). The popup shows the label with the
  ON/OFF button and, while ON, the time as HH:mm with −15/−5/+5/+15 buttons
  under it; OFF leaves just the label and the button. Ops `get`, `toggle` (the
  switch) and `adjust` with the integer extra `delta` (minutes); the answer
  carries `on` (the switch) and the string `text` (the time, "" = not set).
  Older popups see it as a plain switch.

## Host integration

```kotlin
// build.gradle.kts
implementation("com.github.anpaiss:karooswitches:0.2.1")

// the switch
class WaterAlertsSwitch : SwitchReceiver() {
    override fun isOn(context: Context) = Prefs(context).popupEnabled
    override fun setOn(context: Context, on: Boolean) { Prefs(context).popupEnabled = on }
}

// KarooExtension.types
listOf(..., SwitchesDataType(extension))
```

```xml
<!-- AndroidManifest.xml -->
<receiver android:name=".WaterAlertsSwitch" android:exported="true"
    android:permission="com.anpaiss.karooswitches.CONTROL">
    <intent-filter><action android:name="com.anpaiss.karooswitches.SWITCH" /></intent-filter>
    <meta-data android:name="com.anpaiss.karooswitches.label" android:value="Water alerts" />
</receiver>

<!-- extension_info.xml -->
<DataType typeId="switches" displayName="Switches" description="..." graphical="true" icon="..." />
```

```kotlin
// a switch with an adjustable time (meta-data kind="time" next to the label)
class MtaSwitch : TimeReceiver() {
    override fun isOn(context: Context) = Prefs(context).mtaEnabled
    override fun setOn(context: Context, on: Boolean) { ... }      // OFF clears the time too
    override fun text(context: Context): String? = ...               // "18:30", null = not set
    override fun adjust(context: Context, deltaMin: Int) { ... }
}
```

The host needs the overlay permission (`SYSTEM_ALERT_WINDOW`, merged from the
library manifest) granted by the user: the field says ALLOW OVERLAY until then.
Call `Switches.notifyChanged()` when the switch changes from the host's own UI,
so the field redraws.

## Backup through RideStats (0.3.0)

RideStats keeps a copy of what an extension has learned in the rider's Google
Drive and puts it back after a reset of the Karoo. An extension takes part by
subclassing `backup.ExtensionBackupProvider` with the preferences and the text
files under `filesDir` to save:

```kotlin
class BackupProvider : ExtensionBackupProvider() {
    override val prefsNames = listOf("my_prefs")
    override val files = listOf("memory.txt", "folder")
    override fun restoreRefusal() = if (recording) "Not now: a ride is being recorded" else null
    override fun afterRestore() { /* reload what is in memory */ }
}
```

and declaring it:

```xml
<provider
    android:name=".BackupProvider"
    android:authorities="${applicationId}.backup"
    android:exported="true"
    android:permission="com.anpaiss.permission.EXTENSION_BACKUP" />
```

The signature-level permission comes from the library manifest. RideStats finds
every provider guarded by it, so a new extension appears there without a change
to RideStats; nothing is backed up until the rider turns it on.

## Build and publish

```
./gradlew :lib:publish            # GitHub Packages of this repo
./gradlew :lib:publishToMavenLocal   # quick local round trip, optional
```

Published as `com.github.anpaiss:karooswitches` (lowercase: the registry rejects
uppercase coordinates with a 422) to
`https://maven.pkg.github.com/anpaiss/KarooSwitches`. The hosts add that URL
to their `settings.gradle.kts` next to Hammerhead's, with the same
credentials: `gpr.user` / `gpr.key` (a token with `read:packages`; the
publisher's needs `write:packages`) from `local.properties`, the user's
`gradle.properties`, or `GPR_USER` / `GPR_TOKEN` in the CI. Bump `version`
here, publish, bump the dependency line in each host.
