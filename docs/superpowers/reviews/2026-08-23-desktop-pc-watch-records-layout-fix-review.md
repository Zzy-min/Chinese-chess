# Desktop PC Watch, Records, and Profile Layout Restoration Review

## 1. Summary of Changes
- **Isolated Desktop vs Mobile Dispatching in `app.js`**:
  - `renderWatchPage()`: Separated into `renderDesktopWatchPage()` (restoring hero header, filter bar, 2-column split panels for public rooms & archived games) and `renderMobileWatchPage()`.
  - `renderProfile()`: Separated into `renderDesktopProfile()` (restoring 2-column sidebar, profile panels, stats, records list, and sub-pages) and `renderMobileProfile()`.
  - `renderLearnItemCard()`: Added conditional rendering branch for desktop vs mobile layouts.
  - `renderProfileGameCard()`: Separated into desktop `.move` record cards and mobile `.clRecentItem` continuous list items.
- **Cache Busting**:
  - Bumped asset version to `20260823v2` across `index.html` and `OnlineSiteResourceContractTest.java`.
- **Android Native Cache Handling**:
  - Added `clearCache(true)` during debug initialization in `MainActivity.kt`.

## 2. Verification Evidence
1. **Automated Testing**: `mvn test` ran 64 unit and contract tests with 0 failures, 0 errors.
2. **Desktop Browser Verification (Headless Chrome 1400x900)**:
   - `desk_01_watch.png`: PC Desktop 观战页 shows clean 2-column split with hero header and styled filter row.
   - `desk_02_learn.png`: PC Desktop 棋谱库 shows search bar, category pill filters, and horizontal cards.
   - `desk_05_analysis.png`: PC Desktop 复盘分析 shows full 3-column workbench with left overview, center board with step controls, and right move analysis panel.
3. **Real Device Verification (`10AF530FSX002KA`)**:
   - `cl_prod_v2_home.png`: Mobile home page renders classical minimalist UI with vermilion seals and perfect navigation alignment.
