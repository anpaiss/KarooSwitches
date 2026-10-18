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
implementation("com.github.anpaiss:KarooSwitches:0.2.0")

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

## Build

```
./gradlew :lib:publishToMavenLocal
```

Published to the local Maven repository as `com.github.anpaiss:KarooSwitches`,
the coordinates JitPack will give the public repository; hosts add
`mavenLocal()` until then.
