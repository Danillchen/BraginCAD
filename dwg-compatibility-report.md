# BraginCAD DWG compatibility audit

Generated: 2026-08-10 12:25:28

Corpus: 11 DWG files. Encountered graphical/annotation entity types: 45. Fully supported types: 13; partial: 3. Entity type coverage: 28.89%.

## Entity ranking

| Entity | Total | Files | Visible importance | Current status | Recommended priority |
|---|---:|---:|---|---|---|
| LINE | 61389 | 11 | Geometry/graphical | Supported | — |
| LWPOLYLINE | 28209 | 11 | Geometry/graphical | Supported | — |
| MTEXT | 18127 | 11 | Annotation | Supported | — |
| POINT | 26089 | 11 | State-dependent (PDMODE/layer) | Supported | — |
| INSERT | 20155 | 6 | Geometry/graphical | Supported | — |
| TEXT | 6576 | 11 | Annotation | Supported | — |
| ARC | 5771 | 11 | Geometry/graphical | Supported | — |
| DIMENSION_LINEAR | 5785 | 6 | Annotation | Supported | — |
| CIRCLE | 3308 | 11 | Geometry/graphical | Supported | — |
| HATCH | 1761 | 8 | Geometry/graphical | Partial | High |
| DIMENSION_ALIGNED | 1858 | 5 | Annotation | Supported | — |
| SPLINE | 522 | 11 | Geometry/graphical | Partial | High |
| POLYLINE_3D | 2646 | 2 | Geometry/graphical | Supported | — |
| ATTRIB | 602 | 4 | Annotation | Partial | High |
| ATTDEF | 214 | 4 | Annotation | Unsupported | High |
| PS_MARK | 287 | 4 | Geometry/graphical | Unsupported | High |
| ELLIPSE | 208 | 5 | Geometry/graphical | Supported | — |
| VIEWPORT | 189 | 5 | Geometry/graphical | Unsupported | High |
| DIMENSION_ANG2LN | 275 | 2 | Annotation | Unsupported | High |
| MULTILEADER | 94 | 5 | Annotation | Unsupported | High |
| PS_BREAKLINE | 228 | 3 | Geometry/graphical | Unsupported | High |
| WIPEOUT | 225 | 3 | Geometry/graphical | Unsupported | High |
| ARPSUNIVERMARK | 302 | 2 | Geometry/graphical | Unsupported | High |
| SOLID | 73 | 6 | Geometry/graphical | Supported | — |
| PS_GRID | 181 | 2 | Geometry/graphical | Unsupported | Medium |
| ARPSLevelNote | 177 | 2 | Geometry/graphical | Unsupported | Medium |
| ARPSUPDIRPOINTER | 74 | 2 | Geometry/graphical | Unsupported | Medium |
| MLINE | 60 | 1 | Geometry/graphical | Unsupported | Medium |
| PS_RSEC | 30 | 2 | Geometry/graphical | Unsupported | Medium |
| PS_MARKNOTEMLAY | 18 | 2 | Geometry/graphical | Unsupported | Low |
| ARPSPlanNote | 8 | 2 | Geometry/graphical | Unsupported | Low |
| ARPSSECTION | 7 | 2 | Geometry/graphical | Unsupported | Low |
| DIMENSION_RADIUS | 8 | 1 | Annotation | Unsupported | Low |
| ARPSMarkSection | 9 | 1 | Geometry/graphical | Unsupported | Low |
| ACAD_TABLE | 6 | 1 | Geometry/graphical | Unsupported | Low |
| PS_PROXYPLINE | 3 | 1 | Geometry/graphical | Unsupported | Low |
| ARC_DIMENSION | 2 | 1 | Geometry/graphical | Unsupported | Low |
| DBCOLOR | 2 | 1 | Geometry/graphical | Unsupported | Low |
| VERTEX_2D | 2 | 1 | Geometry/graphical | Unsupported | Low |
| OLE2FRAME | 1 | 1 | Geometry/graphical | Unsupported | Low |
| IMAGEDEF | 1 | 1 | Geometry/graphical | Unsupported | Low |
| POLYLINE_2D | 1 | 1 | Geometry/graphical | Unsupported | Low |
| PDFUNDERLAY | 1 | 1 | Geometry/graphical | Unsupported | Low |
| IMAGE | 1 | 1 | Geometry/graphical | Unsupported | Low |
| LEADER | 1 | 1 | Geometry/graphical | Unsupported | Low |
| XRECORD | 50021 | 11 | Service/structural | Unsupported | — |
| DICTIONARY | 12195 | 11 | Service/structural | Unsupported | — |
| ENDBLK | 8561 | 11 | Service/structural | Unsupported | — |
| BLOCK | 8561 | 11 | Service/structural | Unsupported | — |
| BLOCK_HEADER | 8561 | 11 | Service/structural | Unsupported | — |
| VERTEX_3D | 7875 | 2 | Service/structural | Unsupported | — |
| SEQEND | 3160 | 6 | Service/structural | Unsupported | — |
| BLOCKREPRESENTATION | 1514 | 5 | Service/structural | Unsupported | — |
| UNKNOWN_OBJ | 585 | 5 | Service/structural | Unsupported | — |
| LAYER | 470 | 11 | Service/structural | Unsupported | — |
| VISUALSTYLE | 268 | 11 | Service/structural | Unsupported | — |
| SORTENTSTABLE | 255 | 6 | Service/structural | Unsupported | — |
| FIELD | 244 | 1 | Service/structural | Unsupported | — |
| APPID | 206 | 11 | Service/structural | Unsupported | — |
| DICTIONARYVAR | 151 | 11 | Service/structural | Unsupported | — |
| SCALE | 118 | 11 | Service/structural | Unsupported | — |
| STYLE | 117 | 11 | Service/structural | Unsupported | — |
| LTYPE | 117 | 11 | Service/structural | Unsupported | — |
| BLOCKGRIPLOCATIONCOMPONENT | 116 | 4 | Service/structural | Unsupported | — |
| DIMSTYLE | 55 | 11 | Service/structural | Unsupported | — |
| ALDIMOBJECTCONTEXTDATA | 39 | 1 | Service/structural | Unsupported | — |
| BLOCKLINEARGRIP | 35 | 4 | Service/structural | Unsupported | — |
| LAYOUT | 33 | 11 | Service/structural | Unsupported | — |
| MATERIAL | 33 | 11 | Service/structural | Unsupported | — |
| BLOCKSTRETCHACTION | 28 | 4 | Service/structural | Unsupported | — |
| BLOCKLINEARPARAMETER | 25 | 4 | Service/structural | Unsupported | — |
| MLEADERSTYLE | 21 | 11 | Service/structural | Unsupported | — |
| EVALUATION_GRAPH | 20 | 5 | Service/structural | Unsupported | — |
| DYNAMICBLOCKPURGEPREVENTER | 20 | 5 | Service/structural | Unsupported | — |
| DICTIONARYWDFLT | 16 | 11 | Service/structural | Unsupported | — |
| PLACEHOLDER | 14 | 11 | Service/structural | Unsupported | — |
| TABLESTYLE | 14 | 11 | Service/structural | Unsupported | — |
| MLINESTYLE | 12 | 11 | Service/structural | Unsupported | — |
| VIEW_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| VPORT | 11 | 11 | Service/structural | Unsupported | — |
| STYLE_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| DIMSTYLE_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| LAYER_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| VPORT_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| UCS_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| BLOCK_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| APPID_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| LTYPE_CONTROL | 11 | 11 | Service/structural | Unsupported | — |
| BLOCKMOVEACTION | 8 | 3 | Service/structural | Unsupported | — |
| DETAILVIEWSTYLE | 8 | 7 | Service/structural | Unsupported | — |
| SECTIONVIEWSTYLE | 8 | 7 | Service/structural | Unsupported | — |
| BLKREFOBJECTCONTEXTDATA | 8 | 1 | Service/structural | Unsupported | — |
| BLOCKROTATIONGRIP | 7 | 4 | Service/structural | Unsupported | — |
| BLOCKROTATEACTION | 7 | 4 | Service/structural | Unsupported | — |
| ASSOCDEPENDENCY | 7 | 1 | Service/structural | Unsupported | — |
| BLOCKROTATIONPARAMETER | 7 | 4 | Service/structural | Unsupported | — |
| BLOCKVISIBILITYGRIP | 6 | 4 | Service/structural | Unsupported | — |
| TABLEGEOMETRY | 6 | 1 | Service/structural | Unsupported | — |
| BLOCKVISIBILITYPARAMETER | 6 | 4 | Service/structural | Unsupported | — |
| ASSOCVERTEXACTIONPARAM | 6 | 1 | Service/structural | Unsupported | — |
| SUN | 6 | 2 | Service/structural | Unsupported | — |
| CELLSTYLEMAP | 6 | 6 | Service/structural | Unsupported | — |
| PDFDEFINITION | 5 | 2 | Service/structural | Unsupported | — |
| PROXY_OBJECT | 5 | 1 | Service/structural | Unsupported | — |
| VX_CONTROL | 5 | 5 | Service/structural | Unsupported | — |
| BLOCKBASEPOINTPARAMETER | 5 | 4 | Service/structural | Unsupported | — |
| PLOTSETTINGS | 4 | 1 | Service/structural | Unsupported | — |
| BLOCKXYGRIP | 4 | 3 | Service/structural | Unsupported | — |
| BLOCKPOINTPARAMETER | 4 | 3 | Service/structural | Unsupported | — |
| FIELDLIST | 4 | 4 | Service/structural | Unsupported | — |
| WIPEOUTVARIABLES | 3 | 3 | Service/structural | Unsupported | — |
| BLOCKFLIPGRIP | 2 | 1 | Service/structural | Unsupported | — |
| BLOCKPOLARPARAMETER | 2 | 1 | Service/structural | Unsupported | — |
| ASSOCACTION | 2 | 1 | Service/structural | Unsupported | — |
| ASSOCNETWORK | 2 | 1 | Service/structural | Unsupported | — |
| ASSOCARRAYACTIONBODY | 2 | 1 | Service/structural | Unsupported | — |
| BLOCKFLIPPARAMETER | 2 | 1 | Service/structural | Unsupported | — |
| RASTERVARIABLES | 2 | 2 | Service/structural | Unsupported | — |
| BLOCKFLIPACTION | 2 | 1 | Service/structural | Unsupported | — |
| BLOCKPOLARGRIP | 2 | 1 | Service/structural | Unsupported | — |
| BLOCKALIGNMENTGRIP | 1 | 1 | Service/structural | Unsupported | — |
| BLOCKALIGNMENTPARAMETER | 1 | 1 | Service/structural | Unsupported | — |
| IMAGEDEF_REACTOR | 1 | 1 | Service/structural | Unsupported | — |

## Per-file coverage

| File | Graphical | Supported | Unsupported visible | Geometry coverage | Visible coverage |
|---|---:|---:|---:|---:|---:|
| исп колонны 3 этаж_20_07_26.dwg | 2459 | 2459 | 0 | 100% | 100% |
| Ошибка открытия_02.dwg | 2459 | 2459 | 0 | 100% | 100% |
| Ошибка открытия_03.dwg | 1916 | 1916 | 0 | 100% | 100% |
| Ошибка открытия_04.dwg | 1909 | 1909 | 0 | 100% | 100% |
| Ошибка открытия.dwg | 1275 | 1275 | 0 | 100% | 100% |
| test_01.dwg | 52369 | 43300 | 690 | 82.68% | 98.43% |
| test_02.dwg | 4252 | 3840 | 205 | 90.31% | 94.93% |
| test_03.dwg | 6841 | 6249 | 352 | 91.35% | 94.67% |
| test_04.dwg | 84363 | 68389 | 1030 | 81.07% | 98.52% |
| test_05.dwg | 7869 | 7262 | 50 | 92.29% | 99.32% |
| test_06.dwg | 19769 | 19168 | 202 | 96.96% | 98.96% |

Annotation/property coverage requires per-property native instrumentation and is reported as not yet measurable rather than inferred from primitive counts.
