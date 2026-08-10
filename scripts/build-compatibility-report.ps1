param(
    [Parameter(Mandatory=$true)][string]$RegressionJson,
    [string]$Output = "build/dwg-compatibility-report.md"
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$jsonPath = if([IO.Path]::IsPathRooted($RegressionJson)){[IO.Path]::GetFullPath($RegressionJson)}else{[IO.Path]::GetFullPath((Join-Path $project $RegressionJson))}
$outputPath = if([IO.Path]::IsPathRooted($Output)){[IO.Path]::GetFullPath($Output)}else{[IO.Path]::GetFullPath((Join-Path $project $Output))}
$cases = @(Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json)
$supported = @("LINE","CIRCLE","ARC","LWPOLYLINE","POLYLINE_3D","ELLIPSE","TEXT","MTEXT","INSERT","DIMENSION_LINEAR","DIMENSION_ALIGNED","POINT","SOLID","TRACE","3DFACE")
$partial = @("HATCH","ATTRIB","POLYLINE","SPLINE")
$annotation = @("TEXT","MTEXT","ATTRIB","ATTDEF","DIMENSION_LINEAR","DIMENSION_ALIGNED","DIMENSION_ANG2LN","DIMENSION_RADIUS","DIMENSION_DIAMETER","DIMENSION_ORDINATE","MULTILEADER","MLEADER")
$serviceExact = @("DICTIONARY","XRECORD","VISUALSTYLE","LAYER","STYLE","LTYPE","APPID","BLOCK_HEADER","BLOCK","ENDBLK","LAYOUT","DIMSTYLE","MATERIAL","SCALE","SEQEND","VERTEX_3D","DICTIONARYVAR","DICTIONARYWDFLT","SORTENTSTABLE","FIELD","FIELDLIST","MLEADERSTYLE","MLINESTYLE","TABLESTYLE","DETAILVIEWSTYLE","SECTIONVIEWSTYLE","CELLSTYLEMAP","PLACEHOLDER","VPORT","PLOTSETTINGS","SUN","WIPEOUTVARIABLES","RASTERVARIABLES","PDFDEFINITION","IMAGEDEF_REACTOR","BLOCKREPRESENTATION","UNKNOWN_OBJ","PROXY_OBJECT")
$rows = @{}

foreach ($case in $cases) {
    foreach ($entry in @($case.entityBreakdown -split ', ')) {
        if ($entry -notmatch '^(.+)=([0-9]+)$') { continue }
        $type=$matches[1];$count=[int]$matches[2]
        if (-not $rows.ContainsKey($type)) {$rows[$type]=[pscustomobject]@{Entity=$type;Total=0;Files=0}}
        $rows[$type].Total += $count;$rows[$type].Files++
    }
}

$ranked = $rows.Values | ForEach-Object {
    $status = if ($supported -contains $_.Entity) { "Supported" } elseif ($partial -contains $_.Entity) { "Partial" } else { "Unsupported" }
    $dynamicMetadata=$_.Entity -match '^(BLOCK(?!REPRESENTATION)|DYNAMICBLOCK|EVALUATION_GRAPH|ASSOC|.*OBJECTCONTEXTDATA|TABLEGEOMETRY)'
    $visible = if ($serviceExact -contains $_.Entity -or $_.Entity.EndsWith("_CONTROL") -or $dynamicMetadata) { "Service/structural" } elseif ($_.Entity -eq "POINT") { "State-dependent (PDMODE/layer)" } elseif ($annotation -contains $_.Entity) { "Annotation" } else { "Geometry/graphical" }
    $weight = if ($visible -eq "Service/structural") { 0 } elseif ($visible -like "State-dependent*") { 1 } elseif ($visible -eq "Annotation") { 3 } else { 2 }
    $priority = if ($status -eq "Supported" -or $weight -eq 0) { "—" } elseif ($_.Total*$_.Files*$weight -ge 1000) { "High" } elseif ($_.Total*$_.Files*$weight -ge 100) { "Medium" } else { "Low" }
    [pscustomobject]@{Entity=$_.Entity;Total=$_.Total;Files=$_.Files;Importance=$visible;Status=$status;Priority=$priority;Score=$_.Total*$_.Files*$weight}
} | Sort-Object @{Expression={$_.Score};Descending=$true},@{Expression={$_.Total};Descending=$true}

$encountered = @($ranked | Where-Object Importance -ne "Service/structural")
$supportedTypes = @($encountered | Where-Object Status -eq "Supported").Count
$partialTypes = @($encountered | Where-Object Status -eq "Partial").Count
$typeCoverage = if($encountered.Count){100.0*$supportedTypes/$encountered.Count}else{100}
$table = $ranked | ForEach-Object { "| $($_.Entity) | $($_.Total) | $($_.Files) | $($_.Importance) | $($_.Status) | $($_.Priority) |" }
$caseRows = $cases | ForEach-Object { "| $($_.file) | $($_.graphicalEntities) | $($_.supportedEntities) | $($_.unsupportedVisibleEntities) | $($_.graphicalCoveragePercent)% | $($_.visibleGraphicalCoveragePercent)% |" }
@(
    "# BraginCAD DWG compatibility audit","",
    "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')","",
    "Corpus: $($cases.Count) DWG files. Encountered graphical/annotation entity types: $($encountered.Count). Fully supported types: $supportedTypes; partial: $partialTypes. Entity type coverage: $([math]::Round($typeCoverage,2))%.","",
    "## Entity ranking","","| Entity | Total | Files | Visible importance | Current status | Recommended priority |","|---|---:|---:|---|---|---|",$table,"",
    "## Per-file coverage","","| File | Graphical | Supported | Unsupported visible | Geometry coverage | Visible coverage |","|---|---:|---:|---:|---:|---:|",$caseRows,"",
    "Annotation/property coverage requires per-property native instrumentation and is reported as not yet measurable rather than inferred from primitive counts."
) | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Host "Compatibility report: $outputPath"
