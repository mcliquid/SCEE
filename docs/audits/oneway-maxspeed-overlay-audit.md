# Oneway & Maxspeed Overlay – Technical and OSM Tagging Audit

Branch: `SCEE-mc` · Date: 2026-10-29 · Status: **audit only, nothing implemented**

Legend used throughout this document:

- **[V]** verified in the repository (file/line cited) or on the cited OSM Wiki page (fetched 2026-10-29)
- **[A]** assumption / not verified (e.g. runtime behaviour that would need a device test)
- **[R]** recommendation

---

## 1. Executive summary

| | Oneway overlay | Maxspeed overlay |
|---|---|---|
| Recommended architecture | **Hybrid (C)**: new built-in overlay + pure tag interpreter in `osm/oneway/` + *small* generic extension of `OverlayStyle.Polyline` with directional arrows | **Hybrid (C), almost pure (A)**: new built-in overlay + pure parser in `osm/maxspeed/`; rendering uses **only existing** primitives (center stroke, left/right side strokes, center label) |
| Can the custom overlay do it? | No – custom overlay colours by a hash of tag strings (`overlays/custom/CustomOverlay.kt`), has no arrows, no precedence logic | No – cannot bin numeric values, cannot parse units/codes, cannot split directional values |
| New rendering infrastructure | Yes: arrow `SymbolLayer` for overlay features, arrow property in GeoJSON, mechanism to hide background-map oneway arrows | None for MVP |
| Main risk | Misleading direction (motor-vehicle vs bicycle), ambiguous `oneway` on paths | Presenting implicit/default limits as mapped values |
| Complexity | Medium–high (semantics M, rendering M) | Medium (semantics M, rendering S) |
| Recommended order | **2nd** | **1st** |

Key findings:

1. The overlay pipeline is entirely tag/geometry → `OverlayStyle` → GeoJSON → a fixed set of MapLibre layers. Overlays cannot add layers themselves. Anything beyond lines/side-lines/fills/one icon/one label needs a change in `StyledElement.kt` + `StyleableOverlayLayers.kt` **[V]**.
2. Way geometry keeps OSM node order for linear ways (only areas are re-oriented) – so `forward/backward` can be rendered with line-placed symbols without preprocessing **[V]**.
3. Directional arrows already exist twice: background-map `oneway-arrows` layer (from vector tiles, boolean only) and focused-geometry arrows (`SHOW_WAY_DIRECTION` pref). Both use `map_oneway_arrow` and `SymbolPlacement.Line*` with `iconRotate = 90` **[V]**. They prove the approach works on the KMP bindings, but neither is bicycle-aware, and the tile one does not reflect local edits.
4. `osm/oneway/OnewayUtils.kt` and `osm/cycleway/CyclewayParser.kt` already encode part of the precedence (roundabout implication, `opposite*`, `cycleway:<side>:oneway`), but **no function returns a bicycle direction** and **`oneway:vehicle` / `oneway:motor_vehicle` / `oneway:conditional` are not interpreted anywhere** **[V]**.
5. There is **no maxspeed parser** in the repo; only filter fragments (`osm/maxspeed/MaxSpeedUtils.kt`), the writer (`quests/max_speed/MaxSpeedAnswer.kt`) and `Speed` **[V]**. No country default speed table exists – good, nothing would tempt silent inference.
6. Directional speed limits map naturally onto the existing `strokeLeft`/`strokeRight` side strokes (already used by cycleway/sidewalk/parking overlays) **[V]**.

---

## 2. Existing SCEE-mc overlay architecture

### 2.1 Components (verified)

| Concern | Location | Notes |
|---|---|---|
| Interface | `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/overlays/Overlay.kt` | `getStyledElements(MapDataWithGeometry): Sequence<Pair<Element, OverlayStyle>>`, `Form(...)`, `hidesQuestTypes`, `hiddenLabels`, `isCreateNodeEnabled`; extends `ElementEditType` (title, icon, changesetComment, wikiLink, achievements) |
| Registration | `overlays/OverlaysRegistry.kt` | ordinal → overlay; fork overlays use `EE_QUEST_OFFSET + n` (0 custom, 1 restriction, 2 traffic sign, 3 smoothness). Next free: `EE_QUEST_OFFSET + 4/5` |
| Style model | `data/overlays/OverlayStyle.kt` | `Polyline(stroke, strokeLeft, strokeRight, label, disabled)`, `Stroke(color, dashed)`, `Polygon`, `Point` |
| Palette | `data/overlays/OverlayColor.kt` | colour-blind-tuned palette; **Red** = missing/invalid, **Black** = no/does not exist, **Invisible** = not relevant (reserved meanings) |
| Data source | `screens/main/map/sources/StyleableOverlaySource.kt` | loads `MapDataWithEditsSource.getMapDataWithGeometry(bbox)` for tiles at z16 (≤ 32 tiles, only when zoom ≥ 14); incremental `Updated` events restyle only changed elements; `Reload` on overlay change / bbox replace |
| GeoJSON conversion | `screens/main/map/layers/StyledElement.kt` | `toGeoJsonFeatures()`: center line feature, left/right features with `offset`, label as **point feature at `geometry.center`**, plus fork-specific private-access dashed line; width mimics background style (`getLineWidth`) |
| Layers | `screens/main/map/layers/StyleableOverlayLayers.kt` | line casing, lines, dashed lines, fills, extrusions (min zoom 14); side lines (`StyleableOverlaySideLayer`, below roads); `StyleableOverlayLabelLayer` (points only, min zoom 17, `textOptional`, no overlap below z21) |
| Wiring | `screens/main/map/MainMapContent.kt:108–270` | overlay shown at zoom ≥ 14; side layers in `belowRoads*Content`, lines in `belowLabelsContent`, labels/icons in `aboveLabelsContent` |
| Background arrows | `screens/main/map/MapStyle.kt:505` `OnewayArrowsLayer` | tile `road` layer, `oneway == true`, z ≥ 17, `LineCenter` |
| Focus arrows | `screens/main/map/layers/FocusedGeometryLayers.kt:100` | `SymbolPlacement.Line`, `iconRotate 90`, only for selected way when `Prefs.SHOW_WAY_DIRECTION` |
| Hidden labels | `MapStyle.kt:46` `enum class MapLabel { COUNTRIES, LOCALITIES, HOUSE_NUMBERS, ROADS, RIVERS, STREAMS }` | no entry for oneway arrows |
| Legends | – | **No legend component exists** (grep for `legend`: none). Backlog F7 "Custom Overlay Legend" is OPEN, concept only |
| Overlay settings | `util/OverlayHelpers.kt` | only the custom overlay has settings; built-in overlays have none |
| Tests | `app/src/commonTest/.../overlays/{custom,restriction,smoothness,surface,traffic_sign}` | style functions are tested as pure functions (e.g. `RestrictionOverlayStyleTest`, `SmoothnessOverlayStyleTest`, `TrafficSignOverlayTest`); `osm/oneway/OnewayUtilsTest.kt`, `osm/maxspeed/MaxSpeedUtilsTest.kt` exist |

### 2.2 Relevant existing overlays

- **Cycleway** (`overlays/cycleway/CyclewayOverlay.kt`): uses `parseCyclewaySides(tags, isLeftHandTraffic)` and `getCountryInfoByLocation(center)` per way; side strokes for road cycleways; colours separate ways. Direction of cycleways is parsed (`CyclewayAndDirection`) but **not rendered** – there are no arrows in any overlay **[V]**.
- **Traffic sign** (`overlays/traffic_sign/`): per-direction state (`TrafficSignDirection.FORWARD/BACKWARD`) collapsed into one stroke colour + dashing; Germany-only filter via `isWayInGermany`. Also already touches maxspeed tagging (`osm/traffic_sign/TrafficSignWayTagging.kt:887ff` writes `maxspeed`, `zone:maxspeed`, cleans `source:maxspeed`, `maxspeed:type`, `maxspeed:forward/backward`) – the Maxspeed overlay must stay consistent with that writer.
- **Surface / Smoothness**: pattern for "missing = Red", catalogue-driven colours, pure style functions with tests.
- **Restriction**: example of a fork overlay with way + node styles and multi-relation colour merging.

### 2.3 Direction handling in the repo (verified)

- `osm/oneway/OnewayUtils.kt`: `isForwardOneway` = `oneway ∈ {yes,1,true}` or (no `oneway` and `junction=roundabout`); `isReversedOneway` = `oneway=-1`; `isNotOnewayForCyclists` = `oneway:bicycle=no` or `cycleway=opposite*` or the contraflow-side `cycleway:<side>=opposite*`.
- `osm/oneway/Direction.kt`: `FORWARD, BACKWARD, BOTH` + `reverse()`, `getDefault(isRightSide, isLeftHandTraffic)`.
- `osm/cycleway/CyclewayParser.kt:165–192`: per-side direction from `cycleway:<side>:oneway` (`yes/-1/no`), fallbacks to oneway direction or traffic-side default.
- `ElementGeometryCreator.create(way, …)` reverses only **areas** (`isRingDefinedClockwise`) – linear ways, including closed roundabouts (not areas per `IS_AREA_EXPRESSION`), keep node order. `ElementPolylinesGeometry.toGeometry()` emits nodes in order **[V]**. Relations: multi-polylines are joined (`ElementGeometryCreator.kt:110ff`) – direction of relation members is not meaningful; overlays should only style ways.

### 2.4 MapLibre-Compose capabilities actually available (verified in `maplibre-compose 0.20.0` sources)

| Need | Available? | Evidence |
|---|---|---|
| Line-placed symbols | Yes | `SymbolLayer(placement = SymbolPlacement.Line / LineCenter, spacing, iconRotate, iconRotationAlignment)`; already used twice in repo |
| Data-driven icon colour | Only for SDF images | `image(painter, …, drawAsSdf: Boolean = false)` in `expressions/dsl/image.kt:100`; `iconColor` is SDF-only in the MapLibre spec. **[A]** `FocusedGeometryLayers` sets `iconColor` on a non-SDF image – whether it has any effect is unverified |
| Data-driven icon image / rotation | Yes | `iconImage: Expression<ImageValue?>`, `iconRotate: Expression<FloatValue>` |
| Line offset (split lines) | Yes, already used | `LineLayer(offset = …)` in `StyleableOverlaySideLayer` |
| Line pattern (arrow pattern alternative) | Yes | `LineLayer(pattern = …)` |
| Text along line | Yes | `textField` + `SymbolPlacement.Line`, `textKeepUpright` |
| Same API on Android / Desktop | Yes – all map code is in `commonMain`; Desktop uses Vulkan runtime (`maplibre-runtime-windows*`, `DesktopModule.kt:138`) **[V]**; identical *visual* output **[A]**, needs manual check |

### 2.5 Built-in vs custom overlay

The custom overlay (`CustomOverlay.getStyle`) derives colours from a hash of matching tag strings, supports one dash filter and a "missing" colour. It cannot express precedence, numeric binning, units, per-direction values, or arrows. **[R] Both overlays must be native built-in overlays.**

---

## 3. OSM tagging research and references

All pages fetched 2026-10-29.

| Page | Key statements (paraphrased; [V] on wiki) |
|---|---|
| [Key:oneway](https://wiki.openstreetmap.org/wiki/Key:oneway) | Values `yes`, `no`, `-1`, `reversible`, `alternating`; `1`/`0` deprecated, other values undefined. `junction=roundabout` and `highway=motorway` imply `oneway=yes`; otherwise "no general agreement" on implications. On ordinary streets it applies to vehicles only. On footway/path the meaning is ambiguous (2025 forum poll split for `path`). Exceptions must use `oneway:*` keys, **not** access tags (`oneway=yes`+`bicycle:backward=yes` is called wrong). Some countries extend oneway to horses. |
| [Key:oneway:bicycle](https://wiki.openstreetmap.org/wiki/Key:oneway:bicycle) | `no` = cyclists may use a oneway in both directions; `yes` usually unnecessary (useful on footways allowing bikes); `-1` = against way direction; absent ⇒ same as `oneway`. `opposite`, `opposite_lane`, `opposite_track`, `opposite_share_busway` → `oneway:bicycle=no` + `cycleway=no/lane/track/share_busway`. Use of `oneway:bicycle=yes` on non-oneway roads not addressed. |
| [Key:cycleway](https://wiki.openstreetmap.org/wiki/Key:cycleway) | left/right relative to way drawing direction. `cycleway:<side>:oneway` absent ⇒ customary direction for that side. Contraflow example: `oneway=yes`, `oneway:bicycle=no`, `cycleway:left=lane`, `cycleway:left:oneway=-1`. `opposite*` listed as deprecated/discouraged. |
| [Conditional restrictions](https://wiki.openstreetmap.org/wiki/Conditional_restrictions) (target of `Key:oneway:conditional`) | `oneway:conditional=-1 @ Mo-Fr 07:00-10:00`, `no @ destination`, multiple `;`-separated pairs. More specific mode keys (e.g. `oneway:bicycle=no`) override. `oneway:bicycle:conditional` not documented explicitly (follows general scheme). |
| Key:oneway:motor_vehicle | **No wiki page (HTTP 404)**. `oneway:vehicle` is not mentioned on Key:oneway. Both exist in data **[A]** (taginfo not checked). |
| [Tag:junction=circular](https://wiki.openstreetmap.org/wiki/Tag:junction%3Dcircular) | Does **not** imply oneway; circular junctions "generally" one-way, bidirectional ones should get `oneway=no`; `oneway` listed as required. |
| [Key:maxspeed](https://wiki.openstreetmap.org/wiki/Key:maxspeed) | km/h without unit; other units after a space (`50 mph`, `10 knots`), not converted. `none` = no fixed limit, **must not** be used for unknown. `walk` (AT/DE living streets). Codes like `RU:urban`. `signals`, `variable`, `default`, `unposted`, `unknown`, `national`, `0` listed as probable mistakes; use `maxspeed:variable`. `maxspeed:forward/backward`, `maxspeed:<vehicle>`, `maxspeed:lanes(:forward/backward)`, `maxspeed:conditional` (`30 @ (Mo-Fr 07:00-14:00)`, `60 @ wet`), `maxspeed:advisory`. Favours numeric `maxspeed` + code in separate key; no project-wide consensus `maxspeed:type` vs `source:maxspeed`. |
| [Key:maxspeed:type](https://wiki.openstreetmap.org/wiki/Key:maxspeed:type) | `sign`, `<CC>:urban/rural/motorway/…`, `<CC>:zone<n>`, `DE:living_street`, `DE:bicycle_road`. Should accompany a numeric `maxspeed`; some mappers omit it. `CA:urban` controversial (provincial). |
| [Key:source:maxspeed](https://wiki.openstreetmap.org/wiki/Key:source:maxspeed) | `sign`, `markings`, `<CC>:<context>`, `DE:zone30`/`DE:zone:30`. UK uses it for *data source* (survey) instead – semantic conflict. |
| [Key:zone:maxspeed](https://wiki.openstreetmap.org/wiki/Key:zone:maxspeed) | `<CC>:<type>`, e.g. `DE:30`; ways only; tag both `maxspeed` and `zone:maxspeed`; numeric meaning not guaranteed for every value (`CA-QC:40`, `US-NY:*_mph`). |
| [Key:access](https://wiki.openstreetmap.org/wiki/Key:access), [Key:bicycle](https://wiki.openstreetmap.org/wiki/Key:bicycle), [Key:traffic_sign](https://wiki.openstreetmap.org/wiki/Key:traffic_sign) | Not re-fetched; general knowledge **[A]**: `bicycle=no` = no legal access; `vehicle=no` includes bicycles; `access=no` overridable by more specific keys; DE signs relevant: `DE:220` (oneway), `DE:1000-32/1022-10` (bicycles excepted), `DE:274.1` (zone 30). |

### 3.1 Classification of knowledge

| Class | Examples |
|---|---|
| Explicit tagging | `oneway=yes/-1/no`, `oneway:bicycle=*`, `maxspeed=30`, `maxspeed:forward=30` |
| Established conventions | `junction=roundabout` ⇒ oneway; absent `oneway:bicycle` ⇒ bikes follow `oneway`; `cycleway:<side>:oneway` default by traffic side; numeric `maxspeed` + `maxspeed:type` |
| Country-dependent defaults | `DE:urban`=50, `DE:rural`=100, left/right-hand traffic side for contraflow defaults, horses bound by oneway (NL/PL/UK), mph countries |
| Conditional | `oneway:conditional`, `oneway:bicycle:conditional`, `maxspeed:conditional`, `oneway=reversible/alternating` |
| Ambiguous / contradictory | `oneway` on `path`/`footway` (pedestrians?), `junction=circular` without `oneway`, `source:maxspeed` meaning (type vs data source), legacy `opposite*` combined with `oneway:bicycle=yes`, `maxspeed=signals/variable` |

---

## 4. Oneway overlay semantics

### 4.1 Model **[R]**

Compute **two independent directions** per way, each from its own precedence chain, plus flags:

```kotlin
enum class TravelDirection { FORWARD, BACKWARD, BOTH, NO_ACCESS, UNKNOWN }

data class OnewayInterpretation(
    val bicycle: TravelDirection,
    val motorVehicle: TravelDirection,
    val bicycleSource: Source,     // EXPLICIT, LEGACY_OPPOSITE, CYCLEWAY_INFRA_INFERRED, VEHICLE, GENERAL, IMPLIED, DEFAULT
    val isConditional: Boolean,    // any oneway[:mode]:conditional present
    val issues: Set<OnewayIssue>,  // CONFLICT, UNKNOWN_VALUE, AMBIGUOUS_PATH, MISSING_ONEWAY_BICYCLE, REDUNDANT
)
```

`CONFLICT` is an issue flag, not a direction, so the overlay can still draw the most specific explicit direction while warning. `UNKNOWN` covers `reversible`, `alternating`, invalid values.

Value parsing for every `oneway*` key: `yes|1|true` → FORWARD, `-1|reverse` → BACKWARD (accept `reverse` only as UNKNOWN_VALUE-flagged legacy **[A]**), `no|0|false` → BOTH, anything else → UNKNOWN.

### 4.2 Bicycle precedence (highest first)

1. **Access**: `bicycle ∈ {no, use_sidepath?}` – `no` ⇒ `NO_ACCESS`. `use_sidepath` is *not* a direction statement ⇒ keep direction, flag nothing (render normally). Else `vehicle=no` or `access=no` without a positive `bicycle=*` ⇒ `NO_ACCESS`. `highway=motorway` / `motorroad=yes` ⇒ `NO_ACCESS` (country-dependent **[A]**, so only when explicitly `bicycle=no` or motorway – keep simple).
2. **`oneway:bicycle`** (explicit). Wins over everything below.
3. **Legacy `cycleway=opposite*`** or contraflow-side `cycleway:<side>=opposite*` ⇒ BOTH, source `LEGACY_OPPOSITE` (reuse `isNotOnewayForCyclists` logic).
4. **Directional cycleway infrastructure** on a oneway road without `oneway:bicycle`: a side cycleway whose parsed direction (`parseCyclewaySides`) is opposite to the oneway direction or `BOTH` ⇒ BOTH, source `CYCLEWAY_INFRA_INFERRED`, issue `MISSING_ONEWAY_BICYCLE` (wiki pairs it with `oneway:bicycle=no`). Do **not** infer from `cycleway:<side>=lane` alone without an explicit `:oneway`.
5. **`oneway:vehicle`** (bicycles are vehicles).
6. **`oneway`** (explicit) – on `highway=cycleway`, `path`, `footway`, roads alike (for vehicles it is unambiguous; the path ambiguity concerns pedestrians only).
7. **Implied**: `junction=roundabout` ⇒ FORWARD, source `IMPLIED`. `junction=circular` without `oneway` ⇒ BOTH with issue `AMBIGUOUS` (wiki: oneway should be explicit).
8. Otherwise BOTH, source `DEFAULT`.

`oneway:motor_vehicle` **never** affects bicycles. `oneway:conditional` / `oneway:bicycle:conditional` never change the base direction; they set `isConditional`. A plain `oneway:conditional` with `oneway:bicycle` present does not affect bikes (more specific key wins) – but still mark conditional only if the bicycle key itself has a conditional variant.

### 4.3 Motor-vehicle precedence

1. Access: `motor_vehicle|vehicle|access ∈ {no}` (without positive override) or `highway ∈ {cycleway, footway, path, pedestrian, steps, bridleway}` without `motor_vehicle/motorcar` permission ⇒ `NO_ACCESS`.
2. `oneway:motor_vehicle` → 3. `oneway:vehicle` → 4. `oneway` → 5. roundabout → 6. BOTH.

### 4.4 Conflict / issue rules

| Combination | Issue |
|---|---|
| `oneway:bicycle=yes` + `cycleway=opposite*` | CONFLICT |
| `oneway:bicycle=yes/-1` + side `cycleway:<side>:oneway` contraflow | CONFLICT |
| `oneway=yes` + `oneway:bicycle=-1` | CONFLICT-suspicious (legal but very unusual) |
| `oneway=yes` + `bicycle:backward=yes` (access-style exception) | flag UNKNOWN/"wrong tagging" per wiki, interpret as BOTH-uncertain |
| `oneway:bicycle=no` on non-oneway road | REDUNDANT (info, no warning colour) |
| unknown value in any `oneway*` key | UNKNOWN_VALUE |
| `oneway=reversible/alternating` | UNKNOWN (+ conditional-like marker) |

### 4.5 Edge cases (expected interpretation & rendering)

| Tags (plus `highway=residential` unless stated) | Bicycle | Motor vehicle | Render (bicycle mode) |
|---|---|---|---|
| `oneway=yes` | FORWARD (GENERAL) | FORWARD | arrows → , "oneway for all" colour |
| `oneway=yes`, `oneway:bicycle=no` | BOTH (EXPLICIT) | FORWARD | contraflow colour, bidirectional arrows |
| `oneway=no`, `oneway:bicycle=yes` | FORWARD (EXPLICIT) | BOTH | "bicycle-only oneway" colour, arrows → ; flag unusual |
| `oneway=-1`, `oneway:bicycle=no` | BOTH | BACKWARD | contraflow colour, bidirectional arrows |
| `oneway:bicycle=-1` (no `oneway`) | BACKWARD | BOTH | bicycle-only colour, arrows ← |
| `junction=roundabout` (no `oneway`) | FORWARD (IMPLIED) | FORWARD | arrows →, same colour as oneway; dashed optional to mark "implied" – **uncertain marker not required** (wiki: by definition) |
| `junction=circular` (no `oneway`) | BOTH + AMBIGUOUS | BOTH + AMBIGUOUS | Red dashed warning |
| `highway=cycleway`, `oneway=yes` | FORWARD | NO_ACCESS | oneway colour, arrows |
| `highway=cycleway` (nothing) | BOTH | NO_ACCESS | nothing (invisible) |
| `highway=footway`, `bicycle=yes`, `oneway=yes` | FORWARD | NO_ACCESS | arrows; bicycle interpretation is unambiguous (vehicles), pedestrian part not shown |
| `highway=footway` without bicycle access | NO_ACCESS | NO_ACCESS | not in scope (not rendered) |
| `highway=path`, `oneway=yes` | FORWARD | NO_ACCESS | arrows (bicycle reading unambiguous) |
| `highway=pedestrian`, `oneway=yes`, `bicycle=yes` | FORWARD | (destination…) | arrows |
| `highway=service`, `oneway=yes` | FORWARD | FORWARD | arrows |
| `bicycle=no`, `oneway=yes` | NO_ACCESS | FORWARD | Black, **no** bicycle arrows |
| `oneway=yes`, `oneway:motor_vehicle=no`… / `oneway:motor_vehicle=yes` only | BOTH | FORWARD | contraflow colour (bikes not bound), arrows bidirectional; mark source |
| `oneway:vehicle=yes` | FORWARD | FORWARD | as `oneway=yes` |
| `oneway=yes`, `cycleway=opposite_lane` | BOTH (LEGACY) | FORWARD | contraflow colour **dashed** (legacy) |
| `oneway=yes`, `cycleway:left=lane`, `cycleway:left:oneway=-1` (no `oneway:bicycle`) | BOTH (INFERRED) + MISSING_ONEWAY_BICYCLE | FORWARD | contraflow colour dashed + uncertain |
| `oneway=yes`, `oneway:conditional=no @ (Mo-Fr 06:00-09:00)` | FORWARD + conditional | FORWARD + conditional | arrows + conditional marker (label `⏱` or dashed casing) |
| `oneway=reversible` | UNKNOWN | UNKNOWN | Red dashed, no arrows |
| `oneway=yes`, `oneway:bicycle=yes`, `cycleway=opposite` | CONFLICT | FORWARD | Red, arrows suppressed |
| `oneway=maybe` | UNKNOWN_VALUE | UNKNOWN | Red |
| no relevant tags | BOTH (DEFAULT) | BOTH | nothing |

---

## 5. Oneway visual design proposals

Arrows are drawn by a new symbol layer on the existing overlay GeoJSON source, using `map_oneway_arrow` as SDF (`drawAsSdf = true`) so `iconColor` can be data-driven, `iconRotate = 90` (FORWARD) / `270` (BACKWARD) as in existing layers. BOTH uses a new double-headed arrow drawable (one symbol, no overlapping pairs).

### Proposal 1 – Combined bicycle-first view (single mode) **[R]**

Line colour encodes the **bicycle situation relative to general traffic**; arrows encode the **bicycle permitted direction**.

| Category | Line | Arrows |
|---|---|---|
| Oneway for all (incl. bikes) | Orange | → in Orange-dark |
| Oneway for motor vehicles, bicycles both ways (explicit) | Blue | ↔ |
| … inferred / legacy `opposite*` | Blue dashed | ↔ |
| Oneway for bicycles only (general traffic two-way) | Purple | → / ← |
| Bicycles not allowed | Black | none |
| Conflict / unknown / ambiguous | Red (dashed for "ambiguous") | none |
| No restriction | Invisible | none |
| Conditional present | add dashed casing or `⏱` label | as base |

Pros: one glance answers "can I cycle here in this direction?", consistent with the bicycle mapping goal; contraflow gaps (Orange streets in cities) are directly visible; never shows a motor-vehicle restriction as a bicycle one (Orange always means bikes are bound too). Cons: motor-vehicle direction on Blue roads is not drawn explicitly (implied by "contraflow" meaning; visible in the form).

### Proposal 2 – Two arrow tracks (split view)

Center line colour = bicycle category; **left/right side strokes** (existing `strokeLeft/strokeRight`) used as two directional "lanes": side stroke coloured if that travel direction (right-hand traffic: right side = forward) is allowed for bicycles, grey if allowed only for motor vehicles, absent if forbidden; motor-vehicle arrows drawn small on the center line.

Pros: shows both modes without a mode switch. Cons: side strokes already mean "cycleway on that side" in the cycleway overlay – reusing them with a different meaning is confusing; left/right-hand traffic dependency; much denser; harder to test visually.

### Proposal 3 – Mode switch (A: bicycle direction, B: general/motor vehicle)

Same rendering as Proposal 1 but arrows/colours computed from `bicycle` or `motorVehicle` interpretation depending on a mode (overlay setting or two registered overlays).

Pros: each mode simple and unambiguous. Cons: requires settings UI or a second registry entry; users may forget which mode is active ⇒ risk of reading motor-vehicle arrows as bicycle arrows (exactly the misleading case to avoid).

**[R]** Proposal 1 for MVP; Mode B deferred (could later be a second overlay "Oneway (motor vehicles)" rather than a hidden mode, so the active title always states the mode).

### 5.1 Arrow placement and zoom

- z14–16: lines only (arrows would be noise and collide).
- z16–17.5: one arrow per way, `SymbolPlacement.LineCenter` (guarantees an arrow on short segments).
- ≥ z17.5: `SymbolPlacement.Line`, `spacing` ~ 120–150 dp (byZoom), `iconAllowOverlap = true`, `iconIgnorePlacement = true` (so arrows don't suppress road labels and vice versa), `iconSize` byZoom.
- Very short ways (< icon length): `LineCenter` layer still draws one arrow **[A]** behaviour of MapLibre on extremely short lines; verify on device.
- Place arrow layer in `aboveLabelsContent`? **[R]** No: `belowLabelsContent` above overlay lines so street names stay readable.
- Hide background `oneway-arrows` while the overlay is active (they come from tiles, are boolean, and are stale after local edits). Needs a new hook (e.g. `MapLabel.ONEWAY_ARROWS` or a separate `hiddenMapLayers` set) – small change in `MapStyle.kt`.
- Arrow features need no extra GeoJSON features: add `arrows` = `forward|backward|both` and `arrow-color` properties to the existing center-line feature.

---

## 6. Maxspeed overlay semantics

### 6.1 Model **[R]**

```kotlin
sealed interface MaxSpeedValue {
    data class Numeric(val speed: Speed) : MaxSpeedValue   // Speed already has value+unit, toKilometersPerHour()
    data object None : MaxSpeedValue                        // "none"
    data object Walk : MaxSpeedValue                        // "walk"
    data class Code(val code: String) : MaxSpeedValue       // "DE:urban", "RU:rural", "DE:zone:30" in maxspeed itself
    data class Unsupported(val raw: String) : MaxSpeedValue // signals, variable, unknown, 0, garbage
}

data class MaxSpeedInterpretation(
    val forward: MaxSpeedValue?,    // maxspeed:forward ?: maxspeed
    val backward: MaxSpeedValue?,   // maxspeed:backward ?: maxspeed
    val type: MaxSpeedType?,        // from maxspeed:type / source:maxspeed / zone:maxspeed: SIGN, ZONE(n?), IMPLICIT(code), OTHER(raw)
    val hasConditional: Boolean,    // maxspeed:conditional, maxspeed:forward/backward:conditional
    val hasVehicleSpecific: Boolean,// maxspeed:hgv etc. (excluding :advisory, :type, :variable, :lanes, :conditional, :forward/backward)
    val hasLanes: Boolean,          // maxspeed:lanes*
    val advisory: MaxSpeedValue?,   // maxspeed:advisory (never used as legal limit)
    val variable: Boolean,          // maxspeed:variable present or maxspeed=variable
    val issues: Set<MaxSpeedIssue>,
)
```

Parsing rules: `^\d+$` → km/h; `^\d+ mph$` → mph; `knots` → Numeric(knots) only if `SpeedMeasurementUnit` gets it, otherwise Unsupported **[V]** `Speed` supports km/h and mph only. `^[A-Z]{2}(-[A-Z0-9]+)?:.+$` → Code. Whitespace / `50mph` / `50 km/h` → Unsupported (flag "non-standard unit format"), but optionally parse the number for colour.

### 6.2 Scope **[R]**: Option B

Explicit limits **plus** separately marked missing/implicit-only values. Option A hides exactly what QA needs (gaps). Scope filter (aligned with `AddMaxSpeed.elementFilter` road list): `motorway|trunk|primary|secondary|tertiary` (+ `_link`), `unclassified|residential|living_street|busway`; `area != yes`. `service` and `track` excluded in MVP (clutter, rarely signed); possible later.

Never infer a number from `highway=*`. Only exception allowed: a number **literally contained** in a zone code (`DE:zone30`, `DE:zone:30`, `zone:maxspeed=DE:30`), shown as *implicit* (dashed), not as mapped.

### 6.3 Classification and rendering

| Situation | Example | Colour / stroke | Label |
|---|---|---|---|
| Missing (no `maxspeed*`, no type key) | `highway=residential` | **Red** | – |
| Implicit only, no number | `maxspeed:type=DE:urban` | Grey (`0xff9e9e9e`, as `NoSignsColor`) dashed | `DE:urban` |
| Implicit only, number in zone code | `zone:maxspeed=DE:30` | bin colour **dashed** | `30?` / `(30)` |
| Code in `maxspeed` itself | `maxspeed=RU:urban` | Grey dashed | `RU:urban` |
| Numeric | `maxspeed=50` | bin colour solid | `50` |
| Numeric mph | `maxspeed=30 mph` | bin colour of 48 km/h | `30 mph` (original unit) |
| `none` | `maxspeed=none` | Orange **dashed** (never Black: Black = "no", and missing ≠ unrestricted) | `none` |
| `walk` | `maxspeed=walk` | Purple | `walk` |
| `signals` / `variable` | | Red dashed (unsupported) unless `maxspeed:variable` present → Teal dashed "variable" | `signals` |
| Invalid | `maxspeed=fast`, `0` | Red dashed | raw value |
| Directional differ | `maxspeed:forward=30` + `maxspeed:backward=50` | center stroke none; `strokeRight`=30 colour, `strokeLeft`=50 colour (right-hand traffic; swapped for left-hand) | `30 \| 50` ordered left/right… see 7.3 |
| Conditional | `maxspeed=50` + `maxspeed:conditional=30 @ (Mo-Fr 07:00-17:00)` | base colour | `50*` |
| Vehicle-specific | `maxspeed:hgv=60` | base colour | `50+` (MVP) |
| Advisory only | `maxspeed:advisory=40` | Red (legal limit missing) | `(adv. 40)` – **open question** |

Proposed bins (km/h after unit conversion; palette from `OverlayColor`):

| ≤ 20 / walk | 21–30 | 31–40 | 41–50 | 51–70 | 71–90 | 91–120 | > 120 | none |
|---|---|---|---|---|---|---|---|---|
| Purple | Blue | Sky | Aquamarine | Lime | Gold | Orange | Orange (+ thicker? no – same, label differs) | Orange dashed |

Rationale: Red/Black stay reserved; the ramp goes cool (slow) → warm (fast); the two most common urban values (30 / 50) get maximally distinct hues (Blue vs Aquamarine is weak for tritan – acceptable per palette note, labels disambiguate). Labels are mandatory for exact values; colour is only for scanning.

### 6.4 Directional limits

- Both values always visible: side strokes carry per-direction colour; the label shows both values.
- Side assignment: forward traffic drives on the right in right-hand traffic ⇒ forward value on `strokeRight`; left-hand traffic ⇒ `strokeLeft`. Needs `getCountryInfoByLocation` as in `CyclewayOverlay` **[V]** pattern exists.
- Label format **[R]**: `→30 ←50` is direction-of-drawing-relative and meaningless without arrows; instead show values in side order: `50 | 30` reads left-to-right only when map is north-up – also fragile. Pragmatic MVP: `30/50` + side strokes; in the form, show "in drawing direction: 30, against: 50". With the Oneway arrow infrastructure available later, add small arrows on side strokes.
- Only `maxspeed:forward` present + `maxspeed` ⇒ backward = `maxspeed`. Only `maxspeed:forward` without `maxspeed` or `:backward` ⇒ backward missing ⇒ that side Red.
- No separate directional mode needed.

### 6.5 QA signals

| Signal | Reliability | Representation |
|---|---|---|
| Missing `maxspeed` (no type key) | Reliable | Red |
| Unparseable / discouraged values (`signals`, `0`, `unknown`, `50 km/h`) | Reliable (wiki lists them) | Red dashed |
| Zone code number ≠ `maxspeed` (`maxspeed=50` + `maxspeed:type=DE:zone30`) | Reliable | Red dashed + form message |
| `maxspeed` contradicts both `:forward` and `:backward` | Reliable | issue |
| `maxspeed:forward == maxspeed:backward` | Reliable but harmless | info only (no colour) |
| Both `maxspeed:type` and `source:maxspeed` with identical values | Reliable, harmless (wiki discourages duplication) | info only |
| mph value in km/h-only country (via `CountryInfo.speedUnits`) | Fairly reliable | issue |
| Implicit code vs number (`DE:urban` + `maxspeed=30`) | Needs country table → **deferred** | – |
| Implausible values (> 130 off motorways, < 10) | Speculative | form hint only, no colour |
| Sudden changes between neighbours | Speculative; no auto-correction | deferred |
| Outdated data (`check_date:maxspeed` old) | Reliable signal, but policy question | optional enhancement |

No automatic tag changes based on neighbours.

---

## 7. Maxspeed visual design proposals

1. **Colour-coded segments only.** Cheap, readable at z14–16, scales in dense networks. Loses exact values; ambiguous for colour-blind users at adjacent bins; nonnumeric values need extra colours.
2. **Repeated numeric labels along lines** (`SymbolPlacement.Line`, text). Exact, accessible. Requires a new line-placed text layer (existing label layer is point-only at `geometry.center`); collides with street names (would need `hiddenLabels = ROADS`, losing orientation); cost grows with label count; unreadable below z16.
3. **Combined colours + one label per way** **[R]**. Uses existing center-point label (z ≥ 17, `textOptional`, collision-aware) and existing strokes. Colour for overview, label for exact value, zero infra changes. Long ways get only one label – acceptable for an editing overlay (tap shows details).

Legend: no legend component exists. **[R]** MVP: put the legend into the overlay form header/help text or the overlay's description string; a generic legend component (backlog F7) is a separate task.

---

## 8. Architecture options

### 8.1 Oneway

| Option | Advantages | Limitations / required changes |
|---|---|---|
| A – extend existing (custom overlay / cycleway overlay) | No new overlay | Custom overlay can't express precedence or arrows; adding arrows to cycleway overlay overloads its "one aspect" and its side-stroke semantics |
| B – fully dedicated (own source/layers) | Total freedom | Duplicates `StyleableOverlaySource`, click handling, form plumbing; violates "reuse, no parallel implementations" (AGENTS.md §1) |
| **C – hybrid** **[R]** | Reuses pipeline, forms, invalidation; arrows become a generic capability (later usable for cycleway direction, traffic signs) | Add `arrows: OverlayStyle.Arrows?` (direction + color) to `OverlayStyle.Polyline`; emit properties in `StyledElement.toGeoJsonFeatures`; add symbol layer(s) to `StyleableOverlayLayers`; add double-arrow drawable; hide-background-arrows hook in `MapStyle`; new `osm/oneway/OnewayInterpretation.kt` (pure) |

### 8.2 Maxspeed

| Option | Advantages | Limitations |
|---|---|---|
| A – existing infra only | Zero rendering changes | Still needs a new built-in overlay + parser (custom overlay insufficient) |
| B – dedicated | – | Same objection as above |
| **C – hybrid** **[R]** (≈ A + parser) | New `osm/maxspeed/MaxSpeedParser.kt` (pure, reusable by AddMaxSpeed/traffic-sign writer tests), `overlays/maxspeed/MaxSpeedOverlay.kt` with style function | Directional arrows optional later via Oneway's arrow capability |

### 8.3 Forms / interaction

| | Oneway | Maxspeed |
|---|---|---|
| MVP form | Read-only interpretation ("Bicycles: both directions (oneway:bicycle=no)", "Motor vehicles: in drawing direction") + Edit tags / Split way buttons, like `CustomOverlay` form | Read-only interpretation (values per direction, type, conditional, issues) + Edit tags |
| Later | Editing via existing answers (`OnewayAnswer`, contraflow survey of `AddCycleway` – D1) | Reuse `MaxSpeedForm` / `MaxSpeedAnswer.applyTo`; must stay consistent with `TrafficSignWayTagging` cleanup rules |
| `hidesQuestTypes` | `AddOneway`, `AddOnewayBicycle` | `AddMaxSpeed` |
| Settings | none (no mode switch) | none |
| Configurable colours | no | no |
| Zoom thresholds | fixed (see 5.1) | fixed (existing) |
| Clickable | yes (ways) | yes (ways) |
| `achievements` | `BICYCLIST` (as `AddOnewayBicycle`) | `CAR` (as `AddMaxSpeed`) **[V]** |

Note: `OVERLAY_GUIDELINES.md` "Not a quest" – both topics are also quests here; acceptable in a private fork, hiding the quests while the overlay is active avoids duplicate UI.

---

## 9. Performance considerations

- Data: same as other overlays (≤ 32 z16 tiles, incremental updates) **[V]**. Interpretation is O(tags) per way; `getCountryInfoByLocation` per way like `CyclewayOverlay` – acceptable **[A]**; could be cached per tile if profiling shows cost.
- Arrows: no extra features (properties on existing line features). Symbol placement cost scales with line length / spacing; with `iconIgnorePlacement=true` collision work is minimal. Limit by min zoom 16 and spacing ≥ 120 dp. SDF image added once.
- Labels: one point feature per way (existing); collision detection by MapLibre; `textOptional`. In dense grids many labels drop – acceptable.
- Frequent refreshes: `Updated` events already restyle only changed elements; `overlayData` recomputed for the whole collection in `MainMapContent` on every change **[V]** (existing behaviour, not specific to these overlays; backlog G7-like concern).
- Continuous downloads: `onReplacedForBBox` triggers full `Reload` – existing behaviour.
- Mitigations: no per-arrow point features; avoid line-placed text in MVP; keep style functions allocation-light (reuse `Stroke` instances per category).

---

## 10. Test strategy

Pure-function tests in `app/src/commonTest` (pattern of `RestrictionOverlayStyleTest`, `OnewayUtilsTest`):

**Oneway** – `OnewayInterpretationTest` (table-driven, one test per row of §4.5) covering: precedence (`oneway:bicycle` > legacy > infra > `oneway:vehicle` > `oneway` > roundabout), `oneway:motor_vehicle` not affecting bikes, `oneway=-1` variants, roundabout vs circular, `bicycle=no`/`vehicle=no`, conflicts, unknown values, conditional flags, left-hand traffic for inferred contraflow. `OnewayOverlayStyleTest`: category → colour/dash/arrow direction. `StyledElementArrowTest`: `toGeoJsonFeatures` emits `arrows=forward/backward/both` on the center feature and nothing for `null`.

**Maxspeed** – `MaxSpeedParserTest`: `30`, `30 mph`, `10 knots`, `none`, `walk`, `signals`, `variable`, `DE:urban`, `DE:zone:30`, `50 km/h`, `50mph`, `0`, empty. `MaxSpeedInterpretationTest`: forward/backward fallback, partial directional tags, conditional/vehicle/lanes flags, advisory never used as limit, zone-code number extraction and mismatch, type key precedence (`maxspeed:type` vs `source:maxspeed` vs `zone:maxspeed`). `MaxSpeedOverlayStyleTest`: bins incl. mph conversion, missing = Red, implicit = dashed, directional side strokes swapped for left-hand traffic. Consistency test: tags written by `MaxSpeedAnswer.applyTo` and `TrafficSignWayTagging` parse to a non-issue result.

**Validation**: `:app` host/common tests, `assembleDebug`, `desktopApp:compileKotlin`; desktopTest currently blocked (backlog DT-MOK) – document. Manual: Android device + Desktop at z15/17/19 in a dense city (arrow orientation on reversed ways, roundabouts, short segments, label collisions, SDF colouring on both OpenGL and Vulkan runtimes, background arrows hidden).

---

## 11. Recommended MVP for each overlay

### Maxspeed MVP
- Parser + interpretation (pure, tested).
- Overlay with scope from §6.2, colours/labels from §6.3, side strokes for directional limits, read-only form + Edit tags, hides `AddMaxSpeed`.
- **Optional**: `check_date:maxspeed` age dimming, `maxspeed:variable` handling, service roads, editing form.
- **Deferred**: country default tables (would only ever be shown as "implicit"), neighbour-jump warnings, lane-specific display, vehicle-specific mode, line-placed repeated labels, legend component.

### Oneway MVP
- Interpretation (pure, tested) with bicycle + motor-vehicle directions.
- `OverlayStyle.Polyline.arrows` + symbol layer + SDF arrow + double arrow drawable + hide background arrows.
- Proposal 1 rendering, read-only form, hides `AddOneway`/`AddOnewayBicycle`.
- **Optional**: conditional marker refinement, editing form reusing quest answers, arrows on separate cycleways' `cycleway:oneway`.
- **Deferred**: motor-vehicle mode/second overlay, per-side cycleway direction arrows in road overlay, evaluating conditional times, horse/pedestrian oneway.

---

## 12. Prioritized implementation plan

| Step | Content | Size |
|---|---|---|
| 1 | `MaxSpeedParser` + tests | S–M |
| 2 | `MaxSpeedOverlay` (style, registry `EE_QUEST_OFFSET + 4`, strings EN/DE, read-only form) + style tests | M |
| 3 | Device/desktop check, backlog row | S |
| 4 | `OnewayInterpretation` + tests (no UI) | M |
| 5 | Generic arrow capability in `OverlayStyle`/`StyledElement`/`StyleableOverlayLayers` + hide background arrows + tests | M |
| 6 | `OnewayOverlay` (`EE_QUEST_OFFSET + 5`) + style tests | M |
| 7 | Device/desktop check (orientation, SDF, short ways) | S |
| 8 | Optional: maxspeed directional arrows using step 5; editing forms | M |

Rationale for Maxspeed first: higher reuse (no rendering infra), lower risk of misleading output, easily testable, immediately useful for QA. Oneway is more valuable for bicycle mapping but bundles semantics + new rendering; doing step 4 (pure logic) can start in parallel. If bicycle priority dominates, swap the order – see open questions.

Effort (rough): Maxspeed ≈ 1.5–2.5 dev-days; Oneway ≈ 3–5 dev-days (incl. rendering infra and device testing) **[A]**.

---

## 13. Open questions and risks

1. **Order**: Maxspeed first (recommended) or Oneway first because of bicycle focus?
2. **Oneway default roads**: should streets with no oneway at all stay invisible (recommended) or be drawn thin grey so the user sees which roads were considered?
3. **`oneway=yes` without `oneway:bicycle`**: render as normal "oneway for all" (wiki-correct, recommended) or flag as "contraflow not surveyed" (useful for D1 work, but would contradict wiki semantics)?
4. **Inferred contraflow** (`cycleway:<side>:oneway` contraflow without `oneway:bicycle`): show as BOTH-uncertain (recommended) or as conflict?
5. **Advisory-only roads**: Red (legal limit missing) with `(adv. 40)` label (recommended), or own colour?
6. **`maxspeed=none`**: Orange dashed acceptable, or reserve a distinct hue?
7. **Scope**: include `highway=service` in maxspeed overlay?
8. **Hiding background oneway arrows**: acceptable to add a `MapLabel`-like entry (small `MapStyle.kt` change)?
9. **Mode B (motor vehicles)**: later as separate overlay (recommended) or never?

Risks / unresolved ambiguities:
- `oneway:vehicle` / `oneway:motor_vehicle` have no wiki page; semantics derived from access hierarchy **[A]**.
- `oneway` on `path`/`footway` ambiguous for pedestrians (irrelevant for bicycle reading, relevant if a pedestrian mode is ever added).
- `source:maxspeed` used as data source in UK – a value like `survey` must be treated as "no type info", not as invalid.
- `iconColor` on non-SDF image in `FocusedGeometryLayers` – unverified; the new layer must use `drawAsSdf = true` and be checked on Android (OpenGL) and Desktop (Vulkan).
- MapLibre placement on extremely short ways and at tile boundaries (GeoJSON source, so no tile clipping issue expected **[A]**).
- Background tile `oneway` attribute semantics (Jawg schema: direction normalisation unknown) **[A]** – another reason to hide it.
- Interaction with `TrafficSignWayTagging` maxspeed writer: the overlay must classify its outputs as valid (add consistency test).
