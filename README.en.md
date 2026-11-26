# Dark App Tools

Installation requires Android 17 (API 37) or later.

[日本語](README.md) · English

A Vector module that adds dark appearance to Sesame and povo, with separate theme choices for each app.

## Automatic startup after boot

Automatic startup after device boot can be switched on or off in settings. The default is on when no choice has been saved; an existing off choice is preserved. Startup only restores configured behavior and does not override feature switches, permissions, or lock-state restrictions. The switch is in the About tab. Turning it off keeps on-demand setup available when a supported app opens. Settings preparation waits for the first unlock and adds no resident service or polling.

## First unlock after boot

Hook initialization that needs settings or cached discovery results waits for the first user unlock after boot. A later ordinary screen lock does not repeat initialization. Existing screen-off power-saving and security behavior is preserved. Unlock delivery is event-driven, with no polling or new resident service.

## Features

- Enable or disable changes independently for each app.
- Choose **Follow system**, **Always dark**, or **Original light appearance**.
- Adjust backgrounds, text, monochrome icons, and verified bundled UI icons for dark backgrounds.
- Preserve meaningful colors, including Sesame's red/teal device states and povo's yellow accents.
- Apply dark colors to Sesame's search field and its surrounding area.
- Give povo's profile an opaque dark background, distinguishable item surfaces, white arrows, and readable address/payment edit-pencil icons.
- Recolor newly visible povo notification rows before they are displayed while scrolling.
- Dark web content, Japanese/English UI, light/dark settings, and a license list.
- Per-target crash protection.

The module does not indiscriminately invert photos, videos, ad banners, or QR codes. It does not add or change device controls, purchases, or contract-management functions.

## Requirements

Android 17 and Vector API 102 are required. Changes apply only to verified target versions.

| App | Version |
| --- | --- |
| Sesame | 3.0.266 |
| povo | 1.71.0-JP |

Unverified versions retain their original appearance. Physical-device validation on Android 17 covered both home screens, povo's topping list and profile, scrolling its notification list, and restoring original colors when disabled. Web appearance also depends on Android System WebView and the page itself.

## Installation and updates

1. Install the signed release APK from [Releases](https://github.com/OneUI-Mods/DarkAppTools/releases).
2. Enable Dark App Tools in Vector and confirm that Sesame and povo appear as targets.
3. Select a theme from each target's tab in Dark App Tools.
4. Force-stop the target through App info and reopen it. A device or SystemUI restart is not required.

Ordinary updates do not require toggling the module off and on. Force-stop and reopen the target after changing its settings or the device theme as well.

Select OFF or **Original light appearance** and reopen the target to restore its original colors. If crash protection stops a target, reset protection from that app's tab.

## Migration from 0.2.x / Sesame UI Tools

Version 0.3.0 and later use package `dev.ryuya.darkapps`. It is a separate app from `dev.ryuya.sesameui` used through 0.2.x, so settings must be migrated rather than installing directly over the old package.

1. Install the new signed release APK. Keep the old version installed for now.
2. Transfer each app's enabled state, theme, and the module's language. With root and ADB, the helper in the [migration guide (Japanese)](docs/migration.md) can back up, migrate, and verify settings. You can also reproduce the settings manually.
3. Disable the old package's module in Vector and enable the new Dark App Tools package.
4. Force-stop and reopen Sesame and povo; confirm their appearance and module status.
5. Uninstall only the old module package after confirming the new one works. The original Sesame and povo apps are still required.

Normal updates within 0.3.x and later install in place and preserve settings. Current downloads are in this repository's [Releases](https://github.com/OneUI-Mods/DarkAppTools/releases).

## Limitations and privacy

Not every screen or custom drawing implementation is covered. Photos and web content may retain light regions. External browsers and other apps are outside the module's scope.

The settings app has no network permission and saves only appearance preferences. It has no usage reporting or ads. This is an unofficial project, unaffiliated with CANDY HOUSE or KDDI.

Usage is documented here; [architecture and validation (Japanese)](docs/development.md) and [release procedures (Japanese)](docs/releasing.md) are separate. The app uses the MIT License. Dependency notices are available under **Licenses** in the app.

## String compatibility

The previous handling of certain whitespace in license search and version display has been restored.

## Compatibility after updates

After a target-app update, open this module once to complete preparation, then reopen the target. Discovery results remain in this module's own internal app data and are not rescanned when unchanged. No other OneUI Mods app is required. Features stop applying if a unique compatible target cannot be confirmed. Compatibility with every OS or app update is not guaranteed.

[Build environment / ビルド環境](docs/build-environment.md)
