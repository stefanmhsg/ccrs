param()

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$scriptsDir = Join-Path $repoRoot "experiments\scripts"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("ccrs-bdi-report-tests-" + [guid]::NewGuid().ToString("N"))
$script:passed = 0
$script:failures = [System.Collections.Generic.List[string]]::new()

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)

    if ("$Expected" -ne "$Actual") {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-FileExists {
    param([string]$Path, [string]$Message)

    Assert-True -Condition (Test-Path -LiteralPath $Path -PathType Leaf) -Message $Message
}

function Invoke-Test {
    param([string]$Name, [scriptblock]$Body)

    try {
        & $Body | Out-Null
        $script:passed++
        Write-Host "PASS $Name"
    } catch {
        $script:failures.Add("$Name`: $($_.Exception.Message)")
        Write-Host "FAIL $Name"
        Write-Host "  $($_.Exception.Message)"
    }
}

function New-MaseEvent {
    param(
        [string]$Type,
        [string]$Agent,
        [string]$Cell,
        [int]$Sequence
    )

    $agentUri = "http://127.0.1.1:8080/agents/$Agent"
    $cellUri = "http://127.0.1.1:8080/$Cell"
    $timestamp = [int64]9000000000000 + ($Sequence * 1000)
    if ($Type -eq "TRANSACTION") {
        return [ordered]@{
            runId = "fixture-mase-run"
            type = $Type
            timestamp = $timestamp
            agent = $Agent
            graph = $cellUri
            transactionId = $Sequence
            event = [ordered]@{
                type = $Type
                agent = $Agent
                graph = $cellUri
                transactionId = $Sequence
                trigger = "POST"
                status = "COMMITTED"
                traceMode = "summary"
                ruleCount = 1
                startedAt = $timestamp - 10
                finishedAt = $timestamp
                timestamp = $timestamp
            }
            archiveId = $Sequence
        }
    }

    return [ordered]@{
        runId = "fixture-mase-run"
        type = $Type
        timestamp = $timestamp
        agent = $agentUri
        cell = $cellUri
        transactionId = -1
        event = [ordered]@{
            type = $Type
            agent = $agentUri
            cell = $cellUri
            timestamp = $timestamp
        }
        archiveId = $Sequence
    }
}

function Write-Ndjson {
    param([object[]]$Records, [string]$Path)

    $lines = @($Records | ForEach-Object { $_ | ConvertTo-Json -Depth 16 -Compress })
    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

function New-FixtureRun {
    param(
        [string]$RunRoot,
        [string]$RunId,
        [string]$Agent,
        [bool]$Ccrs
    )

    $runDir = Join-Path $RunRoot $RunId
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $logName = "mas-0.log"
    [ordered]@{
        batchId = "fixture-v1"
        runId = $RunId
        jcm = if ($Ccrs) { "dfs_ccrs.jcm" } else { "dfs_baseline.jcm" }
        agentType = "manual"
        ccrsMode = if ($Ccrs) { "both" } else { "none" }
        scenario = "ccrs"
        status = "manual_import"
        timeout = $false
        exitCode = $null
        logFiles = @($logName)
        agentNames = @($Agent)
        maseCaptureStatus = "completed"
        maseCaptureEventCount = 4
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runDir "run.json") -Encoding UTF8

    $logLines = [System.Collections.Generic.List[string]]::new()
    $logLines.Add("[$Agent] [METRIC] event=agent.cycle.location step=1 y=2026 m=1 d=1 h=12 min=0 sec=0 ms=0 t_ms=43200000 previous=http://127.0.1.1:8080/maze cell=http://127.0.1.1:8080/cells/0")
    $logLines.Add("[$Agent] Attempting move to: http://127.0.1.1:8080/cells/0")
    $logLines.Add("[$Agent] Access approved: http://127.0.1.1:8080/cells/0")
    if ($Ccrs) {
        $logLines.Add("[CcrsAgentArch] [CCRS-EVENT] event=ccrs.opportunistic.detected agent_id=$Agent source=http://127.0.1.1:8080/cells/0 target=http://127.0.1.1:8080/cells/999 type=signifier pattern_id=fixture utility=0.9")
        $logLines.Add("[prioritize] [CCRS-EVENT] event=ccrs.opportunistic.prioritize agent_id=$Agent options_count=2 matched_count=1 selected_uri=http://127.0.1.1:8080/cells/999 selected_original_index=1 selected_has_ccrs=true selected_reordered=true selected_origin=opportunistic-ccrs selected_type=signifier selected_utility=0.9 selected_strategy=null")
        $logLines.Add("[evaluate] [CCRS-EVENT] event=ccrs.contingency.evaluate.request agent_id=$Agent situation_type=FAILURE current_resource=http://127.0.1.1:8080/cells/0")
        $logLines.Add("[evaluate] [CCRS-EVENT] event=ccrs.contingency.strategy.evaluated agent_id=$Agent strategy_id=backtrack result_type=suggestion action_type=navigate action_target=http://127.0.1.1:8080/cells/999 confidence=0.8 rationale=fixture-recovery")
    }
    $cycleDuration = if ($Ccrs) { 400 } else { 200 }
    $cycleTimestamp = [int64]43200000 + $cycleDuration
    $logLines.Add("[$Agent] [METRIC] event=agent.cycle.location step=2 y=2026 m=1 d=1 h=12 min=0 sec=0 ms=$cycleDuration t_ms=$cycleTimestamp previous=http://127.0.1.1:8080/cells/0 cell=http://127.0.1.1:8080/cells/999")
    $logLines.Add("[$Agent] Attempting move to: http://127.0.1.1:8080/cells/999")
    $logLines.Add("[$Agent] Access approved: http://127.0.1.1:8080/cells/999")
    Set-Content -LiteralPath (Join-Path $runDir $logName) -Value $logLines -Encoding UTF8

    $events = @(
        (New-MaseEvent -Type "AGENT_MOVED" -Agent $Agent -Cell "cells/0" -Sequence 1),
        (New-MaseEvent -Type "TRANSACTION" -Agent $Agent -Cell "cells/0" -Sequence 2),
        (New-MaseEvent -Type "AGENT_MOVED" -Agent $Agent -Cell "cells/999" -Sequence 3),
        (New-MaseEvent -Type "AGENT_MOVED" -Agent "unrelated-agent" -Cell "cells/7" -Sequence 4)
    )
    Write-Ndjson -Records $events -Path (Join-Path $runDir "mase-events.jsonl")
}

New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
try {
    Invoke-Test -Name "import-manual-run normalizes and preserves sources" -Body {
        $caseRoot = Join-Path $testRoot "import"
        $sourceDir = Join-Path $caseRoot "source"
        $outputRoot = Join-Path $caseRoot "runs"
        New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $sourceDir "mas-0.log") -Value "fixture log" -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $sourceDir "notes.txt") -Value "fixture notes" -Encoding UTF8
        $exportPath = Join-Path $sourceDir "mase-viewer-export.json"
        $exportRecords = @(
            (New-MaseEvent -Type "AGENT_MOVED" -Agent "fixture-agent" -Cell "cells/0" -Sequence 1),
            (New-MaseEvent -Type "AGENT_MOVED" -Agent "fixture-agent" -Cell "cells/999" -Sequence 2)
        )
        $exportJson = ConvertTo-Json -InputObject $exportRecords -Depth 16
        Assert-Equal -Expected 2 -Actual $exportRecords.Count -Message "Fixture export record count is incorrect"
        Assert-True -Condition $exportJson.TrimStart().StartsWith("[") -Message "Fixture export was not serialized as a JSON array"
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($exportPath, $exportJson, $utf8NoBom)
        $ndjsonPath = Join-Path $sourceDir "mase-viewer-extra.ndjson"
        Write-Ndjson -Records @(
            (New-MaseEvent -Type "TRANSACTION" -Agent "fixture-agent" -Cell "cells/999" -Sequence 3)
        ) -Path $ndjsonPath

        & (Join-Path $scriptsDir "import-manual-run.ps1") `
            -SourceDir $sourceDir `
            -OutputRoot $outputRoot `
            -BatchId "fixture-batch" `
            -RunId "001-ccrs" `
            -Jcm "dfs_ccrs.jcm" `
            -AgentName "fixture-agent" `
            -KeepSource

        $runDir = Join-Path $outputRoot "fixture-batch\001-ccrs"
        $run = Get-Content -LiteralPath (Join-Path $runDir "run.json") -Raw | ConvertFrom-Json
        $manifest = Get-Content -LiteralPath (Join-Path $outputRoot "fixture-batch\manifest.json") -Raw | ConvertFrom-Json
        $normalizedEvents = @(Get-Content -LiteralPath (Join-Path $runDir "mase-events.jsonl"))

        Assert-Equal -Expected "both" -Actual $run.ccrsMode -Message "CCRS mode was not inferred from the JCM"
        Assert-Equal -Expected 3 -Actual $run.maseCaptureEventCount -Message "Unexpected normalized MASE event count"
        Assert-Equal -Expected "completed" -Actual $run.maseCaptureStatus -Message "Unexpected MASE capture status"
        Assert-Equal -Expected 1 -Actual @($manifest.runs).Count -Message "Manifest run count is incorrect"
        Assert-Equal -Expected 3 -Actual $normalizedEvents.Count -Message "Normalized NDJSON line count is incorrect"
        Assert-FileExists -Path (Join-Path $runDir "mas-0.log") -Message "Imported log is missing"
        Assert-True -Condition (Test-Path -LiteralPath $exportPath) -Message "-KeepSource removed the MASE export"
        Assert-True -Condition (Test-Path -LiteralPath $ndjsonPath) -Message "-KeepSource removed the NDJSON export"
        Assert-True -Condition (Test-Path -LiteralPath (Join-Path $sourceDir "notes.txt")) -Message "-KeepSource removed metadata"
    }

    Invoke-Test -Name "write-report parses fixtures and is idempotent" -Body {
        $caseRoot = Join-Path $testRoot "report"
        $runRoot = Join-Path $caseRoot "fixture-v1"
        $outputDir = Join-Path $caseRoot "output"
        New-FixtureRun -RunRoot $runRoot -RunId "001-baseline" -Agent "fixture-baseline" -Ccrs $false
        New-FixtureRun -RunRoot $runRoot -RunId "002-ccrs" -Agent "fixture-ccrs" -Ccrs $true

        $reportArgs = @{
            BatchId = "fixture-v1"
            RunRoot = $runRoot
            OutputDir = $outputDir
        }
        & (Join-Path $scriptsDir "write-report.ps1") @reportArgs
        $firstSummary = Get-Content -LiteralPath (Join-Path $outputDir "summary.json") -Raw | ConvertFrom-Json
        & (Join-Path $scriptsDir "write-report.ps1") @reportArgs
        $summary = Get-Content -LiteralPath (Join-Path $outputDir "summary.json") -Raw | ConvertFrom-Json

        Assert-Equal -Expected 2 -Actual $summary.runCount -Message "Unexpected run count"
        Assert-Equal -Expected 1 -Actual $summary.decisionCount -Message "Unexpected decision count"
        Assert-Equal -Expected 2 -Actual $summary.contingencyRecordCount -Message "Unexpected contingency count"
        Assert-Equal -Expected 8 -Actual $summary.actionRecordCount -Message "Unexpected action count"
        Assert-Equal -Expected 6 -Actual $summary.maseEventCount -Message "Agent filtering produced the wrong MASE count"
        Assert-Equal -Expected 4 -Actual $summary.maseAgentMovedCount -Message "Unexpected movement count"
        Assert-Equal -Expected 2 -Actual $summary.maseTransactionCount -Message "Unexpected transaction count"
        Assert-Equal -Expected 4 -Actual $summary.cycleDurationCount -Message "Unexpected cycle count"
        Assert-Equal -Expected 2 -Actual $summary.pathAnalysisInputCount -Message "Unexpected path input count"
        Assert-Equal -Expected $firstSummary.decisionCount -Actual $summary.decisionCount -Message "Repeated parsing changed the decision count"
        Assert-Equal -Expected $firstSummary.maseEventCount -Actual $summary.maseEventCount -Message "Repeated parsing changed the MASE count"

        $runs = @(Import-Csv -LiteralPath (Join-Path $outputDir "runs.csv"))
        $baseline = @($runs | Where-Object { $_.run_id -eq "001-baseline" })[0]
        $ccrs = @($runs | Where-Object { $_.run_id -eq "002-ccrs" })[0]
        Assert-Equal -Expected "success" -Actual $baseline.outcome -Message "Baseline outcome is incorrect"
        Assert-Equal -Expected "success" -Actual $ccrs.outcome -Message "CCRS outcome is incorrect"
        Assert-Equal -Expected 200 -Actual $baseline.average_agent_cycle_duration_ms -Message "Baseline cycle average is incorrect"
        Assert-Equal -Expected 400 -Actual $ccrs.average_agent_cycle_duration_ms -Message "CCRS cycle average is incorrect"
        Assert-Equal -Expected 1 -Actual $ccrs.ccrs_detected -Message "CCRS detection count is incorrect"
        Assert-Equal -Expected 1 -Actual $ccrs.contingency_invocations -Message "Contingency invocation count is incorrect"

        $decisions = @(Import-Csv -LiteralPath (Join-Path $outputDir "decisions.csv"))
        Assert-Equal -Expected "True" -Actual $decisions[0].selected_reordered -Message "Decision reordering was not parsed"
        Assert-Equal -Expected "signifier" -Actual $decisions[0].selected_type -Message "Decision type was not parsed"
        Assert-Equal -Expected 10 -Actual @(Import-Csv -LiteralPath (Join-Path $outputDir "zone-summary.csv")).Count -Message "Zone summary row count is incorrect"

        Assert-FileExists -Path (Join-Path $outputDir "summary.md") -Message "Markdown report is missing"
        Assert-FileExists -Path (Join-Path $outputDir "cycle-duration-comparison.svg") -Message "Cycle chart is missing"
        Assert-Equal -Expected 2 -Actual @(Get-ChildItem -LiteralPath (Join-Path $outputDir "path-analysis-inputs") -Filter "*.cells.txt" -File).Count -Message "Path analysis files are missing"
        $markdown = Get-Content -LiteralPath (Join-Path $outputDir "summary.md") -Raw
        Assert-True -Condition $markdown.Contains("# Experiment Summary: fixture-v1") -Message "Report title is missing"
        Assert-True -Condition $markdown.Contains("## Zone Metrics") -Message "Zone report section is missing"
    }

    if ($script:failures.Count -gt 0) {
        throw ("{0} test(s) failed:`n{1}" -f $script:failures.Count, ($script:failures -join "`n"))
    }

    Write-Host "All $script:passed BDI experiment script tests passed."
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
