param(
    [string]$Serial = 'emulator-5580',
    [string]$ReportPath = (Join-Path $PSScriptRoot '..\output\device-tests.json')
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This test only supports a disposable Android emulator.' }
$runner = 'cn.lightanswer.app.test/cn.lightanswer.app.SmokeTestRunner'
$results = [System.Collections.Generic.List[object]]::new()
$pinSet = $false
function Invoke-Adb {
    param([string[]]$Arguments)
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return ($output -join "`n")
}
function Call-State {
    $dump = Invoke-Adb @('shell','dumpsys','telecom')
    $line = ($dump -split "`n" | Where-Object { $_ -match '^    \[Call id=' } | Select-Object -First 1)
    if ($line -match 'state=([A-Z_]+)') { return $Matches[1] }
    return 'IDLE'
}
function Wait-State([string]$Expected, [int]$Seconds = 8) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        $state = Call-State
        if ($state -eq $Expected) { return $watch.ElapsedMilliseconds }
        Start-Sleep -Milliseconds 150
    } while ($watch.Elapsed.TotalSeconds -lt $Seconds)
    throw "Expected $Expected; got $state"
}
function End-Call {
    Invoke-Adb @('shell','input','keyevent','6') | Out-Null
    Wait-State 'IDLE' | Out-Null
    # Telecom keeps its binding briefly after disconnect. Kill/configure only after it unbinds.
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        $services = Invoke-Adb @('shell','dumpsys','activity','services','cn.lightanswer.app')
        if ($services -notmatch 'ServiceRecord\{') { break }
        Start-Sleep -Milliseconds 300
    } while ($watch.Elapsed.TotalSeconds -lt 12)
    if ($services -match 'ServiceRecord\{') { throw 'Phone service did not unbind after disconnect.' }
}
function Set-Enabled([bool]$Enabled) {
    $value = $Enabled.ToString().ToLowerInvariant()
    $result = Invoke-Adb @('shell','am','instrument','-w','-e','enabled',$value,'-e','speaker','false',$runner)
    if ($result -notmatch "enabled=$value") { throw $result }
}
function Wait-Asleep {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        if ((Invoke-Adb @('shell','dumpsys','power')) -match 'mWakefulness=Asleep') { return }
        Start-Sleep -Milliseconds 200
    } while ($watch.Elapsed.TotalSeconds -lt 8)
    throw 'Screen did not finish going to sleep.'
}
function Test-Call([string]$Name, [string]$Number, [bool]$Auto = $true, [bool]$RequireAsleep = $false) {
    if ($RequireAsleep) { Wait-Asleep }
    Invoke-Adb @('logcat','-c') | Out-Null
    $powerBefore = Invoke-Adb @('shell','dumpsys','power')
    Invoke-Adb @('emu','gsm','call',$Number) | Out-Null
    try {
        $expected = if ($Auto) { 'ACTIVE' } else { 'RINGING' }
        $elapsed = Wait-State $expected
        if (!$Auto) { Start-Sleep -Milliseconds 1100; if ((Call-State) -ne 'RINGING') { throw 'Disabled mode unexpectedly answered.' } }
        $logs = Invoke-Adb @('logcat','-d','-v','time','-s','LightAnswer:I','AndroidRuntime:E')
        if ($logs -match 'FATAL EXCEPTION|Automatic answer failed|foreground notification unavailable') { throw $logs }
        if ($Auto -and $logs -notmatch 'AUTO_ANSWER_CONNECTED') { throw 'Call not confirmed by the app.' }
        if (!$Auto -and $logs -match 'AUTO_ANSWER_REQUEST') { throw 'Disabled mode sent an answer request.' }
        $powerAfter = Invoke-Adb @('shell','dumpsys','power')
        $beforeAsleep = $powerBefore -match 'mWakefulness=Asleep'
        $afterAsleep = $powerAfter -match 'mWakefulness=Asleep'
        if ($RequireAsleep -and (!$beforeAsleep -or !$afterAsleep)) { throw "Expected screen to stay asleep. Before=$beforeAsleep After=$afterAsleep" }
        $results.Add([pscustomobject]@{scenario=$Name;passed=$true;state=$expected;observedWithinMs=$elapsed;asleepBefore=$beforeAsleep;asleepAfter=$afterAsleep;appLog=$logs})
        Write-Output "PASS: $Name ($elapsed ms observation)"
    } finally { End-Call }
}
try {
    if ((Call-State) -ne 'IDLE') { End-Call }
    Set-Enabled $true
    Invoke-Adb @('shell','input','keyevent','224') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
    Invoke-Adb @('shell','am','start','-n','cn.lightanswer.app/.MainActivity') | Out-Null
    Test-Call 'screen-on' '13800138011'
    Invoke-Adb @('shell','input','keyevent','3') | Out-Null
    Invoke-Adb @('shell','input','keyevent','223') | Out-Null
    Start-Sleep -Milliseconds 350
    Test-Call 'screen-off' '13800138012' $true $true
    Invoke-Adb @('shell','am','kill','--user','0','cn.lightanswer.app') | Out-Null
    $remaining = (& adb -s $Serial shell pidof cn.lightanswer.app) -join ''
    if ($remaining -match '\d') { throw 'App process remained alive; cold-start test would be invalid.' }
    Test-Call 'cold-start-with-screen-off' '+8613800138013' $true $true
    Invoke-Adb @('shell','dumpsys','battery','unplug') | Out-Null
    $idle = Invoke-Adb @('shell','dumpsys','deviceidle','force-idle')
    if ($idle -notmatch 'deep idle') { throw $idle }
    Test-Call 'deep-idle-with-screen-off' '10010' $true $true
    Invoke-Adb @('shell','dumpsys','deviceidle','unforce') | Out-Null
    Invoke-Adb @('shell','dumpsys','battery','reset') | Out-Null
    $pinResult = Invoke-Adb @('shell','locksettings','set-pin','2580')
    if ($pinResult -notmatch 'PIN set') { throw $pinResult }
    $pinSet = $true
    Invoke-Adb @('shell','input','keyevent','223') | Out-Null
    Start-Sleep -Milliseconds 500
    Test-Call 'pin-locked-with-screen-off' '13800138015' $true $true
    Invoke-Adb @('shell','locksettings','clear','--old','2580') | Out-Null
    $pinSet = $false
    Set-Enabled $false
    Test-Call 'disabled-stays-ringing' '13800138014' $false
    Set-Enabled $true
    Invoke-Adb @('shell','input','keyevent','223') | Out-Null
    Start-Sleep -Milliseconds 350
    Test-Call 'reenabled-short-number' '10086' $true $true
} finally {
    if ($pinSet) { & adb -s $Serial shell locksettings clear --old 2580 | Out-Null }
    & adb -s $Serial shell dumpsys deviceidle unforce | Out-Null
    & adb -s $Serial shell dumpsys battery reset | Out-Null
    $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}
