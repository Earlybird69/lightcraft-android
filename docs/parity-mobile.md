# Phone parity tracker (Android)

How the Android app (`apps/lightcraft-mobile/`) compares with the reference phone app's layout and
behaviour. The engine's features (sliders, profiles, presets, export…) are tracked in
[`parity.md`](parity.md); this file covers only the phone's screens, controls and gestures. Whoever
changes the phone UI updates its rows here in the same commit.

- **Method**: the same as the desktop's. The reference app was observed running on a phone, view-only
  (its library was empty and stayed empty; nothing was imported, edited, rated or deleted).
  Measurements come from screenshots and screen-reader layout dumps. The editor blocks screenshots,
  so its sizes come from the layout dumps alone. These captures and notes are private (the
  gitignored `plan/lightroom-mobile/`) and are never committed. No Adobe text, images or assets
  belong here or in the app.
- **Status**: ✅ matches (measured on the device) · 🟡 built to the measurements, not yet compared
  side by side on the device, or partly done · ⬜ missing · 🚫 left out on purpose.
- Sizes are in dp, measured on a 1080 × 2340 phone at 329 dpi (1 dp = 2.056 px).

## What we do differently, on purpose

| Area | Reference app | LightCraft |
|---|---|---|
| Icons | Google's Material icons in many places, its own elsewhere | the same Material icons where it uses them (`ui/MaterialIcons.kt`, Apache-2.0), at its sizes; where its icons are its own, LightCraft's, drawn in code to the same sizes and weight (`ui/LcIcons.kt`); the LightCraft tab's icon is our own (the reference's is its logo) |
| Font | its own | Inter (OFL), LightCraft's UI font |
| Name, logo, words | its own | LightCraft and its lynx; labels and help text in our own words |
| Bottom navigation | device gallery, library, community | the phone's gallery (Device) and the library (LightCraft), in the places of its first two; the third place stays empty (its community is online) |
| Its own name | names itself in the title menu's heading, the empty library's title and the about page | the same places name LightCraft; the app never says Lightroom |
| A gallery photo opened | edited where it is; it joins the library once edited | a copy joins the library when it opens (LightCraft edits what's in its library) and leaves again if it was left untouched |
| Add button | add photos · its own camera | add photos · the phone's camera app (the photo goes straight into the library); raws, which the photo picker often doesn't list, come in from Files: the folder at the end of more › add photos |
| Top bar | premium, search, notifications, cloud status, more | search and more (LightCraft is local and free) |
| Menu drawer | account, premium, early access, what's new, rate, social links | preferences, local storage, about, help |
| Actions | auto, enhance, subject, background, fix angle | auto, fix angle, then rate (stars, flags and the photo's facts); the others need AI models the phone doesn't have |
| Remove's switches | detect objects, generative AI, refine | LightCraft's feather and opacity sliders, find dust and delete spot |
| Crop | generative expand (a tile, also named in the bar's title) | none: the bar says Crop |
| Edit sections | auto, light, color, blur, effects, detail, optics, profiles | auto, light, color, effects, detail, optics, calibration, profiles (the engine has no lens blur yet; calibration is the desktop's) |
| Crop | the photo turns under a level frame | the crop is drawn as a turned frame over the whole photo, as on LightCraft's desktop |
| New masks | AI (subject, sky, people, objects…), brush, linear, radial, colour and luminance range | brush, linear, radial, colour range, luminance range (the AI masks need a model the phone doesn't have) |
| Optics | two switches | the two switches, then LightCraft's manual distortion, vignetting, defringe and lateral CA sliders |
| Preset tabs | recommended, premium, yours | LightCraft (built in), yours |
| Slider values | exposure shown as `0.0` at rest | LightCraft's precision, as on the desktop (exposure `0.00`) |
| Detail tabs | (labels not observable) | sharpening, noise, colour noise |
| Curve, grading and the export options | (their views weren't observable) | LightCraft's desktop editors, made for touch: the curve over the photo with five channels and presets; one grading wheel at a time; an export sheet with the desktop's formats and metadata choices |

## Library

| Item | Status | Notes / evidence |
|---|---|---|
| Colours: page black; status bar, bars and navigation bar `#1d1d1d`; text `#d0d0d0`; dividers `#4b4b4b`; primary blue `#066ce7`; a sheet's current choice `#4096f3`, its check `#2680eb`; the chosen tab and a switch that's on `#5eaaf7`; picker counts `#6a6a6a` | ✅ | `ui/Tokens.kt`; the same at every sampled point |
| Icons: Google's Material icons where the reference uses them (menu, search, more, the title's chevron, filter, star, camera, people, location, keyword, chevrons, check, grid, close, add, back; the drawer's settings, phone, help), the rest drawn by LightCraft; each at the reference's size and place | ✅ | `ui/MaterialIcons.kt`, `ui/LcIcons.kt`; every icon of the home screen, the filter, sort and more sheets, the title's menu, the drawer and the add pill compared crop by crop with the reference's captures: ink boxes within 2 px (1 dp), centres within 1 dp. Ours where its are its own: sort, ≥, flags, film, pencil, import, A–Z, sliders, justified grid, segmentation, calendar, slideshow, add photo, select, trash, photo stack, the gallery tab |
| Motion: sheets and the title's menu fade in rising 20 dp (170 ms, decelerating) and fade out rising 10 dp (155 ms, accelerating); the dim fades in over 235 ms and out over 155 ms; a sheet pulled down follows the finger and closes | ✅ | `ui/Overlays.kt` (`LcMotion`); measured from screen recordings of the reference (each frame's time and where its lines were), ours recorded the same way and compared |
| Switches and radio buttons as the reference's: a 36 × 18 dp pill with an 18 dp ring the colour behind it (on: light blue, the ring at the end); an 18 dp ring, chosen a light blue disc with a dot | ✅ | `LcSwitch` in `ui/EditPanels.kt`, `RadioRow`; same size, place and colours as the reference's, off and on |
| Top bar: 56 dp under the status bar; menu at left, search and more at right (48 dp buttons that press as circles) | ✅ | `ui/LibraryScreen.kt` `LibraryTopBar`; buttons at the same bounds upright and sideways |
| Header row (52 dp, on black): the view's title with a dropdown at left; filter and sort at right | ✅ | `LibraryHeader`; the filter button turns blue while a filter is on |
| The title's menu: a panel over a dimmed screen; "LightCraft albums" (the library's views with counts: all photos, edited, unedited, recently added, deleted), then the albums with covers, a sort button, a new album row and a create pill | ✅ | `AlbumPicker` in `ui/LibrarySheets.kt`; side by side with the reference's capture: tiles every 67.6 dp, the open view on a 66.6 dp `#4b4b4b` highlight 12.5 dp in (corners 3.9 dp), headings and their buttons, all within 1 dp |
| Filter sheet: rating with at least / exactly / at most, flags, then pages: type, camera, people, location, keyword, edited; clear filters | ✅ | `FilterSheet`; side by side with the reference's capture, within 1 dp: 60 % of the screen tall, headings 50.6 dp, the rating and flag rows 45 dp, rows 48.2 dp (icon at 16 dp, label at 62.3 dp). Location is the photo's city, else its state, country or location line (`place` in `library.filter`, `Catalog::places`) |
| Sort sheet: capture date, import date, edit date, file name, rating; reverse order | ✅ | `SortSheet`; side by side with the reference's capture, every row within 1 dp |
| More sheet: batch edit › select; square or justified grid; grid segmentation (a switch) and segment by none, year, month, day or hour; slideshow; add photos | ✅ | `MoreSheet`; side by side with the reference's capture, every row within 1 dp. Add photos has a folder at its end for files (raws), so the rows stay the reference's; segment by's page (back, title, radio rows) within 1 dp |
| Justified grid (each photo at its own shape, rows about 125 dp tall) or square cells (125.5 × 121.6 dp, four across upright, 3.9 dp gaps, 7.8 dp at the right) | ✅ upright, 🟡 sideways | `justify`, `squareRows` (unit-tested); upright, the first square cell lands on the reference's bounds to 0.1 dp |
| Segment headings (36.5 dp: the date at left, the count at right) | ✅ | `segments` (unit-tested) |
| Search: typing in a bar over the dimmed screen (back, the field with its blue line, clear); sent with the keyboard's search key, the words show in a chip under the top bar (× takes them away, × at the right leaves the search) and the title gets the number of results under it; words matched against names, titles and keywords | ✅ | `SearchPanel`, `SearchChips`, `LibraryHeader` in `ui/LibraryScreen.kt`; side by side with the reference's captures (typing, typed, sent): the bar, its line, back and clear on its pixels; the chip, the results line, filter and sort within 1 dp. Kept with its words while a photo is open; Back leaves it |
| Select mode: long-press a photo or more › select; a count and select all at the top; rate and flag, add to album, paste settings, remove at the bottom (in Deleted: restore or delete for good) | 🟡 | `SelectBar`, `SelectActions`; works on the device; the reference's own select mode wasn't observable with its library empty. In an album, remove takes the photos out of the album (they stay in the library; `album.removePhotos`), elsewhere (and in a smart album, whose photos follow its rules) it moves them to Deleted; either way with an Undo. The rate sheet shows the rating and flag the selected photos share in blue. A new album made from the selection ends select mode, as adding to one does |
| An album's options: press and hold its row in the title's menu: rename, or delete the album (its photos stay; Undo brings it back); in the editor, more › use as album cover while the album is on show | 🟡 | `AlbumList` in `ui/LibrarySheets.kt` (`album.rename`, `album.delete`, `album.setCover`); checked on the device. The reference's library has no albums, and making one there isn't allowed, so its own way wasn't observable. Messages show over the menu while it's open |
| A message's Undo takes back what it reports, nothing done after it | ✅ | `MobileSession::undo_at` (a mark per step, unit-tested); before, it took back the newest step, whatever that was. The library's messages are put away when a photo opens, so their Undo stays the library's |
| Clean cells (ratings, flags and the edited mark only when asked for) | ✅ | menu › preferences; a raw's format always shows |
| Add pill at bottom right, 16 dp from the edges, 16 dp above the bottom navigation: two 55.9 dp buttons split by a hairline, add photos and the camera | ✅ | `AddButton`, on the library's tab; the camera button opens the phone's camera app and imports the photo (`MainActivity.openCamera`) |
| Bottom navigation (64.2 dp): a 24 dp icon over its label, 175.1 dp a tab, the chosen one blue; Device and LightCraft where the reference's first two tabs are | ✅ | `HomeNav` in `ui/DeviceTab.kt`; icons and labels (13 sp) on the reference's lines, the letters as high as its to 1 px; the tab is kept between launches |
| Device tab: "Photos on device" with a menu of the gallery's albums; the phone's photos by day in the same grid; a tap opens one | ✅ | `DeviceGallery` (MediaStore); checked on the device with access to one photo the app saved itself: it opened in the editor, left untouched its copy went again, edited it stayed |
| Device tab without access: why, and a button that asks (refused for good: one that opens the app's settings); with access to only some photos (Android 14+), a note and "pick more" | ✅ | `DevicePrompt`; the app asks for the photos only here (`READ_MEDIA_IMAGES`, or the photos the person picks) |
| Empty library: a large outline photo, a title and a hint, centred; each view says why it's empty | ✅ | `EmptyLibrary`; side by side with the reference's empty library: the picture (about 100 × 82 dp), the title and the hint on its lines to 1 px |
| Menu drawer: the app's tile, then preferences, local storage, about, help | ✅ | `DrawerContent`; side by side with the reference's: 280 dp, the tile and the line where its are, rows every 48.4 dp from 25.6 dp under the line; it opens from the menu button only (the left edge is the system's back gesture), over 295 ms as the reference's (recorded), and closes at once on Back or a choice, as its does |
| The drawer's pages (preferences, local storage, about): over the whole screen at once, a bar with back and the title, then groups of rows on black (preferences: switches with a note under each; local storage: the phone's space used, a bar, and what the library takes; about: the version, the licence, links) | ✅ | `ui/DrawerPages.kt`; laid out as the reference's pages (its bar, back button and title at 75.9 dp, 500 dp groups 7.8 dp apart, 16 sp rows and 13 sp notes in its colours); Back returns to the drawer, as its does. What they hold is LightCraft's own |

## Library, sideways

The reference lays its library out differently on its side, and so does LightCraft (`WideHome` in
`ui/LibraryScreen.kt`), measured from its landscape captures (2340 × 1080, the system bar at the left).

| Item | Status | Notes / evidence |
|---|---|---|
| A navigation rail at the left (80.3 dp after the system bar): the menu on the bar's line, the tabs (59.3 dp, every 67.1 dp) in the middle of the height | ✅ | `HomeRail` in `ui/DeviceTab.kt`; menu, icons and labels on the reference's pixels (within 1 dp) |
| The bar (64.2 dp under the status bar): search and more at the right, on its middle line | ✅ | more on its pixel |
| The views and albums in a pane beside the photos (255.8 dp; its list 214 dp, the create pill floating at its foot), the photos' pane 3.9 dp from it and from the edge; outer top corners rounded 9.7 dp; the panes run past the camera's cutout | ✅ | `AlbumPane`; heading, tiles, rows and pill within 1.5 dp |
| The photos' pane: the title without a menu, filter and sort at right; the add pill 57.8 dp from its right, 46.7 dp from the bottom | ✅ | title, filter, sort within 1 dp |
| Filter, sort and more as cards by their buttons (280.2, 240.3, 280.2 dp wide, 8 dp corners), no dim, there and gone at once (recorded); sort's and more's button a light disc while open | ✅ | `LcPopupCard` in `ui/Overlays.kt`; edges within 1 dp of the reference's |
| Search: the bar across the whole width on the bar's middle line; sent, the chip row at the top of the photos' pane | ✅ | the same layout as upright, measured again sideways |
| The drawer's pages (preferences, storage, about) as a 500 × 420.3 dp card in the middle, over the dimmed drawer | ✅ | `PageDialog` in `ui/DrawerPages.kt` |
| The drawer after the system bar, 280 dp | ✅ | `DrawerContent` |
| The Device tab sideways: the rail, then the photos' pane alone | 🟡 | the reference's gallery tab shows the owner's photos, so it was only dumped (its layout tree shows the rail alone) |

## Editor, upright

| Item | Status | Notes / evidence |
|---|---|---|
| Photo on black, fitted; top bar 56 dp: back at left; share, save a copy and more at right (40 dp buttons) | ✅ | `ui/EditorScreen.kt` `EditorTopBar`; the photo area spans the same 97.3–1012.2 dp; undo and redo appear once there is history |
| Tool row: actions, presets, crop, edit, masking, remove, centred; labelled (68 dp) until one is open | ✅ | `ToolRow`; labelled, each as wide as the reference's (Masking 55.9, Remove 55.4, the rest 53.5 dp: 100.2–425.6 dp); open, all 53.5 dp (102.1–423.2); both on the reference's bounds to 0.1 dp |
| Actions: a row where Edit's sections go: Auto and Fix angle apply at once (one step each), then, set apart, Rate opens its panel | ✅ | `ActionRow`; Fix angle is Upright's automatic correction with the crop constrained (`geometry.upright`), checked on the device |
| Edit's sections: 53.5 × 68 dp each (a 36 dp icon block over the label), centred, scrolls; Auto set apart | ✅ | `SectionRow`; longer labels (Calibration) widen their slot |
| The panel: no title, sized to its content up to about two and a half sliders; pull the list up for more | ✅ | `SheetLimit` in `ui/EditPanels.kt`; the size matches; pulling Light's list up raised the panel 180 dp on the device |
| Slider rows 48.6 dp: name and value on one line, the bar under them, 16 dp margins | ✅ | `ui/DevelopSlider.kt`; names within 0.3 dp of the reference's. Values keep LightCraft's precision (Exposure `0.00`; the reference showed `0.0` at rest) |
| Light: a Curve button (36 dp pill) at right, scrolling with the sliders | ✅ | |
| Light › Curve: the curve on a square over the photo; the panel picks the channel (parametric, point, red, green, blue) | ✅ | `ui/CurveTool.kt`; the line is the engine's own (`MobileSession.tone_curve`, `point_curve`); checked on the device |
| Point curves: drag a point (one undo step), drag the line elsewhere to add a point there, tap the line to add one, drag a point off the square (or double-tap it) to take it away; presets (linear, medium and strong contrast, yours) and reset | ✅ | `develop.curve`, `curve.applyPreset`, `curve.reset`; adding, removing and a preset checked on the device (double-tap can't be checked with adb) |
| The parametric curve: drag up or down over a region (its slider follows); its region and split sliders below | ✅ | checked on the device: one undo step, the curve redrawn as it moves |
| Targeted adjustment (Curve and Mix): a target button; then drag up or down on the photo over what to change: the curve's region (or a point curve's level) there, or the mixer's colours there (hue, saturation or luminance) | ✅ | `develop.targeted` (the desktop's); half a unit a dp; checked on the device: Darks +49 for a 100 dp drag, greens desaturated, one undo step each |
| Color: B&W at left, Grading and Mix at right; "White balance" with its mode and an eyedropper; Temp, Tint, Vibrance, Saturation | ✅ | eyedropper checked on the device: tap it, then a neutral spot on the photo (`develop.wbPick`), one undo step |
| Color › Grading: shadows, midtones, highlights and global stacked beside the open one's wheel (the hue around it, the saturation out from its grey middle); its luminance, hue and saturation, then blending and balance | ✅ | `ui/GradingWheel.kt` (the desktop's wheel: hue 0 at the right, counter-clockwise); a drag on the wheel is one undo step, checked on the device |
| Color › Mix: eight colour dots over hue, saturation and luminance (B&W: the eight grey mix sliders) | 🟡 | the reference's mix wasn't observable |
| Effects: tabs Effects · Vignette · Grain (40 dp, centred) | ✅ | tab centres match the reference's to 0.05 dp |
| Detail: tabs (sharpening, noise, colour noise); only the amount until it is set | ✅ | the reference's tab labels weren't readable; a tab's other sliders appear once its amount is set (also vignette, grain, defringe) |
| Optics: two 48 dp switch rows | ✅ | LightCraft's manual and defringe sliders follow them |
| Profiles is a mode: its own bottom bar (cancel · Profiles · apply, 63 dp), the top bar keeps only help | ✅ | the bar's buttons and help on the reference's bounds; cancel goes back to the profile it started from; a short sheet: the group's name and menu over a strip of previews |
| Presets: a 306 dp sheet, tabs from the left (59 dp) over a hairline, previews below | 🟡 | tabs match; the reference's cards weren't observable (they never loaded); ours: a grid of live previews on the photo, with an amount slider after applying one |
| More menu: the histogram (a view option), create preset, copy, paste, apply from previous, help; then reset all and remove from library | ✅ | on the reference's layout: 201.4 dp at the right edge, 48.2 dp rows from under the top bar, the words 38 dp in (the reference ends with sign in) |

## Crop

| Item | Status | Notes / evidence |
|---|---|---|
| Crop is a mode: the top bar keeps only help (485.4–525.3 dp); its bar at the bottom: cancel · Crop · apply (63.2 dp) | ✅ | `ModeBar` in `ui/EditorScreen.kt`; help on the reference's bounds; cancel takes every change made in the mode back |
| Tabs Aspect (123 dp) and Geometry (146 dp), 40 dp tall, right over the options | ✅ | `CropSheet` in `ui/CropTool.kt`; Aspect's sheet starts at 877.0 dp, as the reference's |
| Aspect: a row of 67.6 dp tiles (an icon over a name) 16 dp under the tabs: original, free, 1:1, 4:5, 5:7, 2:3, 4:3, 16:9, then turn and reset | ✅ | `crop.aspect`, `crop.rotateAspect`, `crop.reset`; the tiles on the reference's row (943.1–1010.7 dp); the reference groups its ratios (ratios, social sizes) where ours are one row |
| Geometry: Upright's 50 dp tiles (auto, level, vertical, full; tap the one on to turn it off), constrain crop, then turn, flip and straighten, and the manual transform sliders | ✅ | `geometry.upright`, `constrain_crop`, `photo.rotate*`, `photo.flip*`, `crop.straighten`; the reference also has guided Upright; its turn and flip weren't in its layout tree |
| On the photo: the whole photo with the outside dimmed, corner and edge handles, thirds while dragging; drag inside to move it; an aspect holds while dragging | ✅ | `CropOverlay`, `draggedCrop`; one undo step per drag |

## Masking

| Item | Status | Notes / evidence |
|---|---|---|
| All through Masking a mask button joins the top bar (share moves left: share 346, mask 394, save 441.6 dp): show or hide the mask in red over the photo | ✅ | `ui/EditorScreen.kt`; on the reference's bounds; the desktop's overlay (`render_view` with a mask) |
| The section row is the mask's adjustments: Light, Color, Effects, Detail, Optics (53.5 dp each, centred), from the start | ✅ | `MaskSectionRow`; on the reference's bounds (128.9–396.4 dp); without a mask a section opens the new mask sheet; the sliders are the desktop's local ones |
| No sheet at first: a round 43.7 dp button over the photo's foot at the right (no masks: a + with a hint beside it); it opens the new mask sheet, or with masks their list | ✅ | `MaskButton` in `ui/MaskTool.kt`, on the reference's bounds (465.5–509.2 × 863.3–907.0 dp); the list (`MasksSheet`) is ours, the reference's wasn't observable without making a mask |
| Create new mask: a 500 dp sheet, its heading with help, 47.7 dp rows every 55.5 dp in groups 17.5 dp apart: brush, linear gradient, radial gradient; colour range, luminance range | ✅ | `NewMaskSheet`; labels at 73.9 dp (the reference's 74.4); its first group (select subject, sky, background) is AI, left out |
| Add to a mask or subtract from it (the reference's add and subtract): the panel lists the mask's parts (− for one taken away), the handles edit the one chosen; Add and Subtract open the new mask sheet | ✅ | `mask.addComponent`, `mask.update` with the part; subtracting a radial from a radial checked on the device, one undo step each |
| The edited mask's panel: its own settings (a brush's, a range's, a radial's feather), invert, duplicate, delete; or a section's sliders | 🟡 | `MaskSheet`; the reference's wasn't observable without making a mask |
| On the photo: paint or erase with the brush (size, feather, flow); drag a radial's centre and edge or a linear's lines; tap a colour to pick it | ✅ | `MaskOverlay`; a brush stroke, a radial's edge and a linear's dashed line checked on the device, one undo step each (the colour range's tap not yet) |
| Invert, duplicate, delete; undo and redo keep the mask list right | ✅ | `mask.invert`, `mask.duplicate`, `mask.delete`; undo checked on the device |

## Remove

| Item | Status | Notes / evidence |
|---|---|---|
| Remove is a tool like Edit: the tool row stays, each spot is a step of its own | ✅ | as the reference's (no cancel or apply) |
| Its sheet (184 dp): the way it removes (a 40 dp button, its name under it; a menu of remove, heal and clone) beside the size slider; then feather, opacity, find dust and delete the chosen spot | ✅ | `RemoveSheet` in `ui/RemoveTool.kt` (`spot.*`); the button and the slider on the reference's bounds |
| Paint over a blemish (or tap for a round spot); its source is shown; tap a spot to choose it | ✅ | `RemoveOverlay`; a painted spot checked on the device |

## Editor, sideways

| Item | Status | Notes / evidence |
|---|---|---|
| The photo at left with its own top bar | ✅ | `EditorScreen` (wide) |
| At right: the panel (320 dp, full height), a 2 dp gap, the sections as a column (Auto shorter, set apart), then the tools as a column | ✅ | `SideArea`; sections and panel text on the reference's bounds |
| Crop, masking, remove and the curve sideways | ✅ | the same panel at the right (a mode's bar at its foot); checked on the device |

## Gestures and behaviour

| Item | Status | Notes / evidence |
|---|---|---|
| Slideshow (more › slideshow): the view's photos full screen, the system bars hidden, one every 4 s, wrapping; tap to pause, swipe to step, back to end | ✅ | `ui/Slideshow.kt`, the desktop's (`view.slideshow`) timing; checked on the device |
| Swipe sideways for the next or previous photo | ✅ | neighbours are rendered ahead (`prefetchView`) |
| Press and hold the photo to see it before its edits | ✅ | |
| A slider drag is one undo step | ✅ | `beginAdjustment` / `endAdjustment`; one undo takes the whole drag back |
| The editor's undo reaches back only to when the photo was opened: a delete, restore or album step from the library isn't in its history | ✅ | `MobileSession::open_photo` marks the history (`Session::undo_dropped` counts the steps the 1000-step history has dropped); unit-tested and checked on the device. A mode's Cancel (Crop, Profiles) takes back every step since it opened, for good: nothing is left on Redo, and nothing from before it is touched, a drag that changed nothing or a finger still down included (`MobileSession::discard_to`, from a mark taken as the mode opens; unit-tested and checked on the device). Discarding an untouched gallery copy is housekeeping: no undo step, and the photo in the editor stays the one being edited (`discard_photo`) |
| Auto applies at once | ✅ | `develop.auto` |
| Back leaves a mode (as cancel) or a sub-panel before it leaves the editor | ✅ | `AppViewModel.back`; checked on the device: a sub-panel, then Crop (cancelled), then the editor |
| Pinch to zoom, drag when zoomed, double-tap to zoom in or out | 🟡 | built; adb can't pinch or tap twice fast enough to check it |
| Double-tap a slider's name to reset it | 🟡 | built; as above |
| Save a copy, or share one: a sheet with the file type (JPEG, PNG, TIFF, AVIF; DNG for raws), the size (2048 px or full), the quality and the metadata (all, all but camera info, copyright only, none; remove location); kept for next time | ✅ | `ui/ExportSheet.kt` (`export` with the desktop's options); checked on the device: a full-size JPEG saved to Pictures/LightCraft, the same as the desktop's export to the pixel; a share goes from the app's cache (`PhotoFiles.shareable`), so it leaves no copy in the phone's photos. A long edge of 2048 px is 2048 px (it was a pixel short when the crop was a hair off 3:2; `export::render_box`, tested); full size shows the photo's size as cropped (`MobileSession::export_size`) |
| Full-size renders over 16.7 MP (2^24 pixels) on the CPU | ✅ | the Adreno 830's GPU wrote every pixel from index 2^24 on back over the start of the image (a 24 MP export came back with its last rows over its first and the rest black); `Gpu::max_pixels` keeps Android GPU renders and the sources they sample under it (`crates/gpu`, fault-tested). Editor views stay on the GPU. Checked on the device at the cap (a 4000 × 4000 crop) with three masks, colour and luminance noise reduction, sharpening, texture, clarity, dehaze, vignette, grain and a healed spot: the GPU's export matches the CPU's (mean difference 0.003 levels) |

## Not on the phone yet

| Item | Status | Notes |
|---|---|---|
| Blur (lens blur) | ⬜ | the engine doesn't render it yet (`parity.md` LR-EDIT-LENSBLUR) |
| AI masks (subject, sky, people, objects) | ⬜ | the engine has them; the phone needs their model |
| Community, cloud sync | 🚫 | LightCraft is a local app |
