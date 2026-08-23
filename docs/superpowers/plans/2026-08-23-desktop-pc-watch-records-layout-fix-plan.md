# Desktop PC Watch, Records, and Profile Layout Restoration Plan

## 1. Goal
Restore 100% of PC desktop layouts for Watch (`#watch`), Profile/Records (`#me`, `#me/records`), Learn (`#learn`), and Analysis (`#analysis/*`) without degrading mobile classical UI.

## 2. Proposed Changes

### `src/main/resources/online/app.js`
- **Watch Page Separation**:
  - `renderWatchPage()`: Dispatches to `renderMobileWatchPage()` on mobile, and `renderDesktopWatchPage()` on desktop.
  - `renderDesktopWatchPage()`: Restores `.hero`, `.panel`, `.split`, `.moves`, `renderDesktopWatchRooms()`, `renderDesktopWatchGames()`.
  - `renderMobileWatchPage()`: Keeps classical minimalist mobile stream.
- **Profile Page Separation**:
  - `renderProfile()`: Dispatches to `renderMobileProfile()` on mobile, and `renderDesktopProfile()` on desktop.
  - `renderDesktopProfile()`: Restores `.profileLayout`, `.profileSidebar`, `.profileMain`, `renderDesktopProfileGameCard()`.
  - `renderMobileProfile()`: Keeps classical minimalist scholar dossier & continuous menu.
- **Learn Cards CSS Support in `app.css`**:
  - Add standard desktop layout rules for `.learnCard`, `.learnCardTop`, `.learnCardBadge`, `.learnCardInfo`, `.learnAction`, `.learnActionLeft`, `.learnActionRight`, `.learnSummaryOneLine`, `.learnGhostBtn` in `app.css`.

## 3. Verification Plan
- Run `mvn test` (all 64 unit tests).
- Start local Undertow and capture PC desktop browser screenshots for `#watch`, `#me/records`, `#learn`, `#analysis/*`.
- Run real Android device verification via ADB.
