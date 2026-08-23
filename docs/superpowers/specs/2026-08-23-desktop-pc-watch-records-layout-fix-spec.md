# Desktop PC Watch, Records, and Profile Layout Restoration Spec

## 1. Background & Problem Statement
During the recent mobile UI classical minimalist refactoring, several shared rendering functions in `app.js` (`renderWatchPage`, `renderWatchRooms`, `renderWatchGames`, `renderProfile`, `renderProfileGameCard`, `renderLearnItemCard`) were converted to mobile-specific HTML classes (`.clWatchPage`, `.clProfilePage`, `.clRecentList`, `.clRecentItem`, etc.).

Because styles for `.clWatchPage`, `.clRecentItem`, etc., are scoped strictly inside mobile media queries in `mobile.css`, viewing the following pages on a PC desktop browser caused them to render as broken, unstyled raw HTML without proper grid systems, cards, or sidebars:
1. **Watch Page (`#watch`)**: Displayed unstyled mobile stream containers without the desktop hero, filter row, and two-column split panels (`公开房间` / `可观战归档对局`).
2. **Profile & Records (`#me`, `#me/records`)**: Displayed unstyled mobile dossier / list cards without the desktop 2-column sidebar (`.profileSidebar` + `.profileMain`) and `.moves` card containers.
3. **Learn Cards (`#learn`)**: Rendered unstyled buttons and misaligned headers due to missing desktop classes for `.learnCardTop`, `.learnAction`, etc.

## 2. Design & Architecture

### 2.1 Clean Dual-Mode Dispatching
We strictly separate desktop and mobile rendering via `isMobileLayout()` in `app.js`:

1. **Watch Route (`#watch`)**:
   - `if (isMobileLayout()) return renderMobileWatchPage();`
   - `else return renderDesktopWatchPage();`
   - `renderDesktopWatchPage()` uses `.hero`, `.panel`, `.split`, `.moves`, and desktop `renderDesktopWatchRooms()` / `renderDesktopWatchGames()`.
   - `renderMobileWatchPage()` uses `.clWatchPage`, `.clWatchFilterBar`, `.clWatchSection`, `.clWatchList`, `.clWatchItem`.

2. **Profile Route (`#me`, `#me/*`)**:
   - `if (isMobileLayout()) return renderMobileProfile();`
   - `else return renderDesktopProfile();`
   - `renderDesktopProfile()` uses `.profileLayout`, `.profileSidebar`, `.profileMain`, and `renderDesktopProfileGameCard()`.
   - `renderMobileProfile()` uses `.clProfilePage`, `.clProfileHeader`, `.clProfileMenu`, `.clRecentItem`.

3. **Learn Route (`#learn`)**:
   - Add desktop styles for `.learnCardTop`, `.learnCardBadge`, `.learnCardInfo`, `.learnAction`, `.learnActionLeft`, `.learnActionRight`, `.learnSummaryOneLine`, `.learnGhostBtn` in `app.css` so both desktop and mobile render crisply.

### 2.2 CSS Isolation Guarantees
- `app.css`: Contains complete styling for all desktop components (`.hero`, `.panel`, `.split`, `.profileLayout`, `.profileSidebar`, `.profileMain`, `.moves`, `.move`, `.boardDesk`, `.boardRail`, `.boardStage`, `.recordPane`, `.playbackControls`).
- `mobile.css`: Scoped for mobile touch viewports with zero interference on desktop browsers.

## 3. Verification Strategy
1. Automated unit test suite (`mvn test`, all 64 tests passing).
2. Desktop headless Chrome rendering verification across `#watch`, `#me`, `#me/records`, `#learn`, `#analysis/{id}`.
3. Real Android device live verification (`adb screencap`) ensuring mobile view remains 100% intact.
