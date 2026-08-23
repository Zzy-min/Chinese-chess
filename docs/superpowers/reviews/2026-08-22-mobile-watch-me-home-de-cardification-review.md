# Mobile Watch, Me, and Home De-cardification Review

- **Date**: 2026-08-22
- **Target**: Chinese Chess Mobile Arena (src/main/resources/online/app.js, src/main/resources/online/mobile.css)

## Summary of Changes
1. **Home Page Vertical Adaptation**:
   - Expanded recent matches display to 5 items with outcome seals.
   - Added classical chess maxim ribbon (.clMaximBar).
   - Verified that the vertical viewport on tall mobile displays (1260x2800) is gracefully filled without bottom whitespace gap.
2. **Watch Page De-cardification**:
   - Replaced multi-layer nested card panels with single-flow classical match streams (.clWatchItem).
   - Rebuilt top filter controls as a compact, single-line grid (.clWatchFilterBar).
3. **Me (Profile) Page De-cardification**:
   - Combined separate PC sidebar and main cards into a unified scholar profile dossier (.clProfileHeader, .clProfileStatsGrid).
   - Implemented an elegant continuous water-ink menu list (.clProfileMenu, .clProfileMenuItem) with crisp chevron indicators.
   - Built subpage views with responsive breadcrumb header navigation.

## Verification Evidence
- mvn test: 64/64 passed.
- Real device screenshots: cl_v4_01_home.png, cl_v4_02_watch.png, cl_v4_03_me.png, cl_v4_04_me_records.png, cl_v4_05_me_settings.png.
