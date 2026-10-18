# FRecorder

> This fork is based on [vdo/FRecorder](https://github.com/vdo/FRecorder). 中文说明见 [README.md](./README.md).

<p align="center"> <img src="logo.svg" width="128" alt="FRecorder logo"/> </p>

**FRecorder** is a fast, straightforward Android field recorder.

<img src="shot.jpg" width="300" alt="FRecorder screenshot"/>

## Changes

- **Explicit built-in microphone selection** — the audio input list now includes every input reported by the system, instead of USB / wired devices only. Selecting the built-in mic explicitly calls `AudioRecord.setPreferredDevice()`, overriding the system's source routing.
- **Quick built-in-mic recording** — a standalone screen (Settings → *Quick record with built-in microphone*) that ignores the saved audio input preference and always records from the phone's own mic. Also reachable from the app-icon long-press shortcut.
- **Launcher shortcuts** — long-press the app icon to start recording, or to record with the built-in microphone directly. Registered at runtime, so they work in both debug and release builds.
- **Full Simplified Chinese localization** — every string translated.
- **Reworked the howling-risk check for an explicitly selected built-in microphone**

## Features

- Aside from the changes above, features are unchanged — see [vdo/FRecorder](https://github.com/vdo/FRecorder).

## Disclaimer

- The author of this fork is not a professional. The device-selection changes and the Chinese localization were made for personal use; no guarantee is given for anything else.

## Credits

- FRecorder is based on [Audio Recorder](https://github.com/Dimowner/AudioRecorder) by Dmytro Ponomarenko.
- This fork is based on [vdo/FRecorder](https://github.com/vdo/FRecorder).

## License

```txt
Copyright 2019 Dmytro Ponomarenko
Copyright 2026 vdo
Copyright 2026 KNSOR

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
