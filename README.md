[![Latest build](https://github.com/element-hq/element-x-android/actions/workflows/build.yml/badge.svg?query=branch%3Adevelop)](https://github.com/element-hq/element-x-android/actions/workflows/build.yml?query=branch%3Adevelop)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=bugs)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![codecov](https://codecov.io/github/element-hq/element-x-android/branch/develop/graph/badge.svg?token=ecwvia7amV)](https://codecov.io/github/element-hq/element-x-android)
[![Element X Android Matrix room #element-x-android:matrix.org](https://img.shields.io/matrix/element-x-android:matrix.org.svg?label=%23element-x-android:matrix.org&logo=matrix&server_fqdn=matrix.org)](https://matrix.to/#/#element-x-android:matrix.org)
[![Localazy](https://img.shields.io/endpoint?url=https%3A%2F%2Fconnect.localazy.com%2Fstatus%2Felement%2Fdata%3Fcontent%3Dall%26title%3Dlocalazy%26logo%3Dtrue)](https://localazy.com/p/element)

# Element X Android

Element X Android is the next-generation [Matrix](https://matrix.org/) client provided by [Element](https://element.io/).

Compared to the previous-generation [Element Classic](https://github.com/element-hq/element-android), the application is a total rewrite, using the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) underneath and targeting devices running Android 7+. The UI layer is written using [Jetpack Compose](https://developer.android.com/jetpack/compose), and the navigation is managed using [Appyx](https://github.com/bumble-tech/appyx).

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=io.element.android.x)[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/io.element.android.x)

## What's changed in this fork

This fork keeps tracking upstream Element X Android, but adds several user-facing changes focused on chat UX, visual customization, and richer sticker support.

### 1. Reworked attachment menu

The attachment flow was redesigned to make common actions faster and easier to reach from the composer.

- a custom bottom-sheet attachment menu replaces the more limited default layout
- stickers are available directly from the composer, next to the voice message control
- the sticker shortcut hides automatically when text input is not empty
- the attachment action bar was cleaned up for a more even and compact layout
- media and sticker actions were reorganized to reduce extra taps while composing messages

<table>
  <tr>
    <th>Upstream</th>
    <th>This fork</th>
  </tr>
  <tr>
    <td><img src="./docs/fork-images/photo_1_2026-04-05_19-43-34.jpg" width="280" /></td>
    <td><img src="./docs/fork-images/photo_2_2026-04-05_19-43-34.jpg" width="280" /></td>
  </tr>
</table>

### 2. Theme customization

This fork adds extra hooks for visual customization beyond the upstream theme setup.

- additional theme extension points were introduced in the Compound and design system layers
- custom theme options can be plugged into the app without rewriting the whole UI stack
- preferences-related wiring was extended so theme behavior can be evolved further from settings
- the groundwork is in place for more opinionated branding and fork-specific visual variants

<table>
  <tr>
    <th>Upstream</th>
    <th>This fork</th>
  </tr>
  <tr>
    <td><img src="./docs/fork-images/photo_3_2026-04-05_19-43-34.jpg" width="280" /></td>
    <td><img src="./docs/fork-images/photo_4_2026-04-05_19-43-34.jpg" width="280" /></td>
  </tr>
</table>

### 3. Sticker packs and sticker UX

Sticker support was expanded substantially, both in the picker and in the room timeline.

- sticker packs can be imported directly from archive files
- imported sticker packs can be removed from inside the picker
- the sticker picker shows pack previews using the first sticker instead of plain text tabs
- the active pack name is shown below the pack strip for better context
- the active pack is visually highlighted
- the sticker grid was adjusted to show more stickers per row
- sticker preview loading was reworked to behave better for large packs and animated assets
- stickers in the timeline were restyled to appear larger and cleaner
- sticker message bubbles were removed for a more natural sticker presentation
- transparent PNG stickers render without being filled by an opaque message background
- very wide stickers are constrained so they do not take over the chat width
- animated sticker handling was extended with custom rendering and caching paths in this fork

Composer shortcut:

<table>
  <tr>
    <th>Upstream</th>
    <th>This fork</th>
  </tr>
  <tr>
    <td><img src="./docs/fork-images/photo_5_2026-04-05_19-43-34.jpg" width="280" /></td>
    <td><img src="./docs/fork-images/photo_6_2026-04-05_19-43-34.jpg" width="280" /></td>
  </tr>
</table>

Sticker picker:

<img src="./docs/fork-images/photo_7_2026-04-05_19-43-34.jpg" width="280" />

Sticker rendering in timeline:

<img src="./docs/fork-images/photo_8_2026-04-05_19-43-34.jpg" width="280" />

## Table of contents

<!--- TOC -->

* [Screenshots](#screenshots)
* [What's changed in this fork](#whats-changed-in-this-fork)
* [Translations](#translations)
* [Rust SDK](#rust-sdk)
* [Status](#status)
* [Minimum SDK version](#minimum-sdk-version)
* [Contributing](#contributing)
* [Build instructions](#build-instructions)
* [Support](#support)
* [Copyright and License](#copyright-and-license)

<!--- END -->

## Screenshots

Here are some screenshots of the application:

<!--
Commands run before taking the screenshots:
adb shell settings put system time_12_24 24
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1337
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100

And to exit demo mode:
adb shell am broadcast -a com.android.systemui.demo -e command exit
-->

|<img src="./docs/images-lfs/screen_1_light.png" width="280" />|<img src="./docs/images-lfs/screen_2_light.png" width="280" />|<img src="./docs/images-lfs/screen_3_light.png" width="280" />|<img src="./docs/images-lfs/screen_4_light.png" width="280" />|
|-|-|-|-|
|<img src="./docs/images-lfs/screen_1_dark.png" width="280" />|<img src="./docs/images-lfs/screen_2_dark.png" width="280" />|<img src="./docs/images-lfs/screen_3_dark.png" width="280" />|<img src="./docs/images-lfs/screen_4_dark.png" width="280" />|

## Translations

Element X Android supports many languages. You can help us to translate the app in your language by joining our [Localazy project](https://localazy.com/p/element). You can also help us to improve the existing translations.

Note that for now, we keep control on the French and German translations.

Translations can be checked screen per screen using our tool Element X Android Gallery, available at https://element-hq.github.io/element-x-android/. Note that this page is updated every Tuesday.

More instructions about translating the application can be found at [CONTRIBUTING.md](CONTRIBUTING.md#strings).

## Rust SDK

Element X leverages the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) through an FFI layer that the final client can directly import and use.

We're doing this as a way to share code between platforms and while we've seen promising results it's still in the experimental stage and bound to change.

## Status

This project is actively developed and supported. New users are recommended to use Element X instead of the previous-generation app.

## Minimum SDK version

Element X Android requires a minimum SDK version of 24 (Android 7.0, Nougat). We aim to support devices running Android 7.0 and above, which covers a wide range of devices still in use today.

Element Android Enterprise requires a minimum SDK version of 33 (Android 13, Tiramisu). For Element Enterprise, we support only devices that still receive security updates, which means devices running Android 13 and above. Android does not have a documented support policy, but some information can be found at [https://endoflife.date/android](https://endoflife.date/android).

## Contributing

Want to get actively involved in the project? You're more than welcome! A good way to start is to check the issues that are labelled with the [good first issue](https://github.com/element-hq/element-x-android/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) label. Let us know by commenting the issue that you're starting working on it.

But first make sure to read our [contribution guide](CONTRIBUTING.md) first.

You can also come chat with the community in the Matrix [room](https://matrix.to/#/#element-x-android:matrix.org) dedicated to the project.

## Build instructions

Just clone the project and open it in Android Studio. Make sure to select the
`app` configuration when building (as we also have sample apps in the project).

To build against a local copy of the Rust SDK, see the [Developer
onboarding](docs/_developer_onboarding.md#building-the-sdk-locally) instructions.

## Support

When you are experiencing an issue on Element X Android, please first search in [GitHub issues](https://github.com/element-hq/element-x-android/issues)
and then in [#element-x-android:matrix.org](https://matrix.to/#/#element-x-android:matrix.org).
If after your research you still have a question, ask at [#element-x-android:matrix.org](https://matrix.to/#/#element-x-android:matrix.org). Otherwise feel free to create a GitHub issue if you encounter a bug or a crash, by explaining clearly in detail what happened. You can also perform bug reporting from the application settings. This is especially recommended when you encounter a crash.

## Copyright and License

Copyright (c) 2025 Element Creations Ltd.
Copyright (c) 2022 - 2025 New Vector Ltd.

This software is dual licensed by Element Creations Ltd (Element). It can be used either:

(1) for free under the terms of the GNU Affero General Public License (as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version); OR

(2) under the terms of a paid-for Element Commercial License agreement between you and Element (the terms of which may vary depending on what you and Element have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licenses for the specific language governing permissions and limitations under the Licenses.
