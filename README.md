# RhythmCraft

A playable rhythm game client mod for Minecraft 1.16.5 Forge, featuring functional **Beatmania IIDX** and **Taiko no Tatsujin (太鼓之達人)** arcade cabinets.

[中文說明](#rhythmcraft-中文說明)

---

## Features

### 🕹️ Beatmania IIDX Arcade Cabinet
* **Gameplay**: Fully functional 7-key + 1-scratch layout.
* **Chart Formats**: Loads standard `.bms` and `.bme` chart formats.
* **Compatibility**: Custom fallbacks for Shift_JIS, GBK, and MS949 chart encodings to natively prevent mojibake.
* **Audio**: Decodes and plays synced Ogg Vorbis, MP3, and WAV files.
* **Customization**: Adjust scroll speed and audio delay offsets in real-time. Rebind keys to any keyboard keys.
* **Rules**: Real-time Groove Gauge scoring system, timing judgments (PGREAT / GREAT / GOOD / BAD / POOR), combo metrics, and clear/fail results.

### 🥁 Taiko no Tatsujin Arcade Cabinet
* **Aesthetics**: Premium 3D custom block models with slanted drums, CRT bezel screen, custom marquee, and side festival lanterns.
* **Gameplay**: Authentic 4-zone drum gameplay (Left Katsu, Left Don, Right Don, Right Katsu).
* **Chart Formats**: Parses standard `.osu` (osu!taiko) format files.
* **Resource Packs**: Automatically scans for charts inside both local directories and active **Minecraft Resource Packs** (under `assets/rhythmcraft/taiko/`).
* **Audio**: Decodes `.mp3`, `.ogg`, and `.wav` audios directly in memory for lag-free performance.
* **Visuals**: Full input-lightup feedback on the in-game display panel, synced to NoteBlock drum sound events.
* **Rules**: Soul Gauge (魂) health scoring (Clear threshold at 70%), timing judgments (Good/OK/Bad), 連打 (Drumrolls), and Balloon pop mechanics.

---

## Installation & Setup

1. Make sure you have **Minecraft 1.16.5** and **Minecraft Forge** installed.
2. Place `rhythmcraft-1.0.jar` into your `.minecraft/mods/` directory.
3. Launch the game. The mod will auto-create the following folders inside your game directory:
   * `.minecraft/rhythmcraft/beatmania/` (for Beatmania BMS charts)
   * `.minecraft/rhythmcraft/taiko/` (for Taiko osu!taiko charts)
   * `.minecraft/config/rhythmcraft_client.json` (stores your custom settings)

*(Note: Both cabinets will automatically generate a metronome demo song if their folders are empty.)*

---

## Adding Custom Songs

### 🎹 Beatmania IIDX (`rhythmcraft/beatmania/`)
Put each song inside its own subfolder.
* **BGM file names**: Looks for `bgm.ogg` first, then `bgm.mp3`, then `bgm.wav`.
* **Chart file names**: Looks for files ending in `.bms` or `.bme`.

```text
.minecraft/rhythmcraft/beatmania/
└── Nhelv/
    ├── chart.bme
    └── bgm.mp3
```

### 🥁 Taiko no Tatsujin (`rhythmcraft/taiko/` or Resource Packs)
You can load songs locally or via a Minecraft Resource Pack.
* **Local path**: `.minecraft/rhythmcraft/taiko/` (each song in its own subfolder).
* **Resource Pack path**: Place charts under `assets/rhythmcraft/taiko/` inside your pack.
* **Audio & Chart files**: Looks for `.osu` files and the corresponding `.mp3`/`.ogg`/`.wav` music files defined in the osu metadata.

```text
.minecraft/rhythmcraft/taiko/
└── Nine Point Eight/
    ├── Nine Point Eight.mp3
    ├── Kantan.osu
    └── Oni.osu
```

---

## How to Play & Controls

### 🕹️ Beatmania IIDX
* **Default Keys**: `S`, `D`, `F` (White keys 1,2,3), `Space` (Blue center 4), `J`, `K`, `L` (White keys 5,6,7), and `Left Shift` (Turntable Scratch).
* **Selection Menu**: Use `UP`/`DOWN` arrows to scroll through songs. Press `RIGHT` to focus difficulty charts, `LEFT` to return. Press `ENTER` to play.
* **Settings Panel**: Press `TAB` on the select screen to open settings. Use `1`-`8` keys to select lanes to bind, `UP`/`DOWN` arrows to adjust speed, and `LEFT`/`RIGHT` arrows to adjust delay. Press `TAB` to save and exit.
* **Exit**: Press `ESC` to exit gameplay or close the screen.

### 🥁 Taiko no Tatsujin
* **Default Keys**: 
  * `D` - Left Rim (Katsu / Blue)
  * `F` - Left Center (Don / Red)
  * `J` - Right Center (Don / Red)
  * `K` - Right Rim (Katsu / Blue)
* **Selection Menu**: Use `UP`/`DOWN` arrows to scroll. Press `RIGHT` to focus difficulties, `LEFT` to return. Press `ENTER` to play.
* **Settings Panel**: Press `TAB` on the selection screen. Use `1`-`4` keys to select drum zones to bind, `UP`/`DOWN` arrows to adjust speed, and `LEFT`/`RIGHT` arrows to adjust delay. Press `TAB` to save and exit.
* **Exit**: Press `ESC` to exit gameplay or close the screen.

---

# RhythmCraft 中文說明

一個在 Minecraft 1.16.5 Forge 中運作的音樂遊戲模組，包含功能完整的 **Beatmania IIDX** 鍵盤街機與 **太鼓之達人** 街機。

---

## 功能特點

### 🕹️ Beatmania IIDX 街機
* **遊戲模式**：支援標準的 7 鍵 + 1 轉盤操作。
* **譜面支援**：讀取並解析傳統 `.bms` 與 `.bme` 譜面。
* **編碼優化**：自動防亂碼機制，支援 Shift_JIS、GBK 與 MS949 編碼 fallback，原生防文字亂碼。
* **音訊同步**：支援 OGG Vorbis、MP3 與 WAV 檔案播放。
* **自定義**：可在遊戲中自由設定下落速度與延遲，並支援自定義按鍵綁定。
* **遊戲規則**：內置 Groove Gauge 血條、精確判定（PGREAT / GREAT / GOOD / BAD / POOR）、連擊數與結算畫面。

### 🥁 太鼓之達人街機
* **精美外觀**：3D 客製化方塊模型，包含倾斜鼓面、螢幕外框、慶典裝飾看板與兩側燈籠。
* **遊戲模式**：支援左藍（Katsu）、左紅（Don）、右紅（Don）、右藍（Katsu）四個鼓面判定區。
* **譜面支援**：解析 `.osu` (osu!taiko) 太鼓格式譜面。
* **資源包載入**：除本地掃描外，還支援直接讀取 **Minecraft 資源包 (Resource Packs)** 內的譜面（路徑：`assets/rhythmcraft/taiko/`）。
* **音訊同步**：支援 `.mp3`、`.ogg` 與 `.wav` 在記憶體中直接解碼播放，拒絕任何卡頓。
* **視覺與音效**：畫面指示器會即時反應按鍵點擊動作，並同步播放對應的 NoteBlock 音效。
* **遊戲規則**：標準的魂計量表（魂氣槽達 70% 判定為通關）、良/可/不可判定、黃色連打及氣球音符機制。

---

## 安裝與執行

1. 確保已安裝 **Minecraft 1.16.5** 與 **Forge**。
2. 將 `rhythmcraft-1.0.jar` 放入您的 `.minecraft/mods/` 目錄中。
3. 啟動遊戲後，模組將會在您的遊戲根目錄下自動創建以下目錄：
   * `.minecraft/rhythmcraft/beatmania/` (存放 Beatmania 譜面)
   * `.minecraft/rhythmcraft/taiko/` (存放太鼓之達人譜面)
   * `.minecraft/config/rhythmcraft_client.json` (儲存您的自定義按鍵與速度配置)

*(注意：若首次進入遊戲目錄為空，街機將會自動生成一個 Metronome 測試譜面。)*

---

## 導入譜面

### 🎹 Beatmania IIDX (`rhythmcraft/beatmania/`)
請為每首歌曲建立一個獨立的子資料夾。
* **背景音樂檔名**：優先找 `bgm.ogg`，其次為 `bgm.mp3`，最後為 `bgm.wav`。
* **譜面檔名**：後綴為 `.bms` 或 `.bme` 的檔案。

```text
.minecraft/rhythmcraft/beatmania/
└── Nhelv/
    ├── chart.bme
    └── bgm.mp3
```

### 🥁 太鼓之達人 (`rhythmcraft/taiko/` 或資源包)
您可以將譜面存放在本地資料夾，或者作為資源包打包。
* **本地存放**：`.minecraft/rhythmcraft/taiko/`（每首歌曲獨立的子資料夾）。
* **資源包存放**：放入資源包內的 `assets/rhythmcraft/taiko/` 目錄中。
* **譜面與音樂檔案**：導入 `.osu` 譜面，以及其在 osu 詮釋資料中指定的音訊檔案（如 `Nine Point Eight.mp3`）。

```text
.minecraft/rhythmcraft/taiko/
└── Nine Point Eight/
    ├── Nine Point Eight.mp3
    ├── Kantan.osu
    └── Oni.osu
```

---

## 操作與設定方法

### 🕹️ Beatmania IIDX
* **預設按鍵**：`S`, `D`, `F` (白鍵 1,2,3), `Space` (藍鍵 4), `J`, `K`, `L` (白鍵 5,6,7)，以及 `Left Shift` (轉盤)。
* **選歌單**：使用 `UP`/`DOWN` 箭頭鍵上下滾動；`RIGHT` 鍵切換到難度列選歌，`LEFT` 鍵返回歌單；按 `ENTER` 開始遊玩。
* **設定面板**：在選歌單按 `TAB` 鍵。按鍵盤 `1`-`8` 對應修改鍵位，使用 `UP`/`DOWN` 箭頭調整下落速度，`LEFT`/`RIGHT` 調整音訊延遲。按 `TAB` 存檔並返回選歌單。
* **退出**：在遊戲或選單中按 `ESC` 鍵退出。

### 🥁 太鼓之達人
* **預設按鍵**：
  * `D` - 左鼓邊 (Katsu / 藍)
  * `F` - 左鼓面 (Don / 紅)
  * `J` - 右鼓面 (Don / 紅)
  * `K` - 右鼓邊 (Katsu / 藍)
* **選歌單**：使用 `UP`/`DOWN` 箭頭滾動；`RIGHT` 鍵進入難度列，`LEFT` 返回歌單；按 `ENTER` 開始遊玩。
* **設定面板**：在選歌單按 `TAB` 鍵。鍵盤按 `1`-`4` 修改鼓面按鍵綁定，`UP`/`DOWN` 調整速度，`LEFT`/`RIGHT` 調整音訊延遲。按 `TAB` 儲存並返回。
* **退出**：在遊玩或選單中按 `ESC` 鍵退出。
