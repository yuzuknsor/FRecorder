> 本项目 fork 自 [vdo/FRecorder](https://github.com/vdo/FRecorder)（其上游为 [Dimowner/AudioRecorder](https://github.com/Dimowner/AudioRecorder)）。英文说明见 [README.en.md](./README.en.md)

<p align="center">
  <img src="logo.svg" width="128" alt="FRecorder logo"/>
</p>

# FRecorder

**FRecorder** 是一款快速、直接的 Android 外景录音应用——为真实场景而生。无论你在片场通过 USB 声卡收录现场声、做采访，还是只需要一个可靠的随身录音机，FRecorder 都不会挡你的路：打开，按下录音。

<img src="shot.jpg" width="300" alt="FRecorder 界面截图"/>

## 本 fork 的改动（KNSOR 构建）

- **内置麦克风显式选择**——音频输入列表现在包含系统上报的全部输入，内置麦克风排在前面（框架能提供物理地址时一并显示），而不再只列 USB / 有线设备。选中内置麦克风时会显式调用 `AudioRecord.setPreferredDevice()`，覆盖系统的源路由。这正是"插着 USB-C 耳机或转接头时，仍能用手机自带麦克风录音"的关键——否则录音会悄悄落到转接头那个（常常并不存在的）耳麦上。
- **内置麦克风快捷录音**——独立入口（设置 → *使用内置麦克风快捷录音*），忽略已保存的音频输入偏好，始终用手机麦克风录音。实现上使用一次性设备覆盖（`RecordingDeviceOverride`），由 `RecordingService` 消费后立即丢弃，因此普通录音仍按配置的设备走。
- **完整简体中文本地化**——所有字符串均已翻译，包括本 fork 新增的音频效果、监听与降噪设置。
- **修复显式选中内置麦克风时的啸叫风险判定**（此前只要设备非空就被当作"外部设备、无风险"）。

## 功能

- **USB 音频输入**——从外接 USB 麦克风、声卡等设备录音。自动检测已连接设备并可选为录音源。可配 Rode AI-Micro 之类的 USB 声卡，或 BOYA BY-K4 之类的 TRS 转 USB-C 转接头，接入接触式麦克风、领夹麦或任何标准 3.5mm 音源。
- **实时监听**——通过蓝牙耳机或内置扬声器实时监听录音内容，录音前或录音中均可开关。
- **增益提升**——可调输入增益（+6 dB / +12 dB），实时生效并带削波保护。
- **降噪**——保存时可选频域降噪（仅 WAV），参数可调。
- **高通 / 低通滤波**——可配置 HPF（80/120 Hz）与 LPF（9.5/15 kHz），用于清理录音。
- **噪声门**——静音段自动压低底噪。
- **保存格式**——WAV（16/24 bit）、MP3（LAME，320 kbps）、FLAC（无损）。录音内部一律以 WAV 进行以保证质量，保存时再转换。
- **位深**——可选 16 bit 或 24 bit WAV 输出。
- **音频参数可配**——采样率（8–48 kHz）、单声道/立体声、输入设备选择。
- **首次设置向导**——引导完成输出格式、位深、采样率、声道、输入设备选择与录音权限申请。
- **快速启动**——启动经过优化，不让你错过任何一条素材。
- **波形显示**——录音与回放时实时显示波形。
- **文件管理**——重命名、分享、导入、收藏、移入回收站/恢复，内置文件浏览器。
- **主题**——多种配色主题。

音频效果、监听与降噪的完整说明与推荐配置见 **[AUDIO FX 与降噪](AUDIO_FX.md)**。

## 相对上游的格式变更

移除了 **3GP** 与 **M4A** 录音格式——这两种是低质量的遗留格式，不适合外景录音。现在录音内部统一为 WAV，由用户在保存时选择输出格式（WAV / MP3 / FLAC）。

### 为什么不做 32-bit float？

- **硬件限制**——几乎所有手机麦克风、以及手机上使用的绝大多数 USB 声卡，ADC 都是 16 bit 或 24 bit。硬件根本给不出 32-bit 数据，32-bit 文件只是把 16-bit 采样补零，白白浪费磁盘。
- **文件体积**——32-bit 比 16-bit 体积翻倍，而录制端听不出任何差别。

## 免责声明

这不是专业级录音机——别指望手机录出零底噪的素材。不过开启保存时降噪、或在监听时启用噪声门，效果已经相当可接受。

## 致谢

FRecorder 基于 Dmytro Ponomarenko 的 [Audio Recorder](https://github.com/Dimowner/AudioRecorder)。

---

> *"最好的麦克风，是你随身带着的那一支。"*
> —— 改编自 Chase Jarvis

---

## 许可证

```
Copyright 2019 Dmytro Ponomarenko
Copyright 2026 vdo
Copyright 2026 KNSOR（本 fork 的修改部分）

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
