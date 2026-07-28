# App Graphics & Asset Dimensions Guide (Pixels & DP)

Use this reference guide when replacing any logo, icon, or banner image asset in the project:

---

### 1. App Launcher Logo / Main Icon (`docicon.png` / `docicon_512.png`)
* **Dimensions:** `512 x 512 px`
* **Format:** 32-bit PNG with transparent background or high-res square logo.
* **File Locations:**
  - `/app/src/main/res/drawable/docicon.png`
  - `/app/src/main/res/drawable/docicon_512.png`
* **Purpose:** Google Play Store icon & source for app launcher foreground.

---

### 2. Feature Banner / Promo Header Graphic (`docbanner.png` / `docbanner_1024x500.png`)
* **Dimensions:** `1024 x 500 px`
* **Format:** PNG or JPEG (~2:1 aspect ratio / 16:9 widescreen)
* **File Locations:**
  - `/app/src/main/res/drawable/docbanner.png`
  - `/app/src/main/res/drawable/docbanner_1024x500.png`
* **Purpose:** Feature graphic for Google Play & app header banner.

---

### 3. Adaptive Launcher Vector Safe Zone (`ic_launcher_foreground.xml`)
* **Total Canvas Size:** `108 x 108 dp` (432 x 432 px @ xxxhdpi)
* **Centered Safe Content Viewport:** `66 x 66 dp` (264 x 264 px @ xxxhdpi)
* **File Location:** `/app/src/main/res/drawable/ic_launcher_foreground.xml`

---

### 4. Mipmap Density Resolution Scale Chart
* **mdpi (1x):** `48 x 48 px`
* **hdpi (1.5x):** `72 x 72 px`
* **xhdpi (2x):** `96 x 96 px`
* **xxhdpi (3x):** `144 x 144 px`
* **xxxhdpi (4x):** `192 x 192 px`

---
