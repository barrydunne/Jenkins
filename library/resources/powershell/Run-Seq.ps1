function Get-SeqData {
    param(
        [Parameter(Mandatory)]$dataType
    )

    $url = "$seqHost/api/$dataType/?ownerId=user-admin&shared=true"

    for ($attempt = 1; $attempt -le 12; $attempt++) {
        try {
            $data = Invoke-RestMethod -Uri $url
            if ($data.Count -ge 0) {
                Write-Host "Found $($data.Count) $dataType"
                return $data
            }
        }
        catch {
            Write-Host "Failed on attempt $attempt"
            Write-Host $_.Exception.Message
            if ($attempt -lt 12) {
                Start-Sleep -Seconds 5
            }
        }
    }
    return $null
}

function New-SeqData {
    param(
        [Parameter(Mandatory)]$dataType,
        [Parameter(Mandatory)]$existing,
        [Parameter(Mandatory)]$title,
        [Parameter(Mandatory)]$json
    )

    $known = $existing | Where-Object { $_.Title -eq $title }
    if ($null -ne $known) {
        Write-Output "'$title' already exists in $dataType"

        $updateUrl = "$seqHost/$($known.Links.Self)"
        try {
            # Need to maintain original Id and links
            $obj = $json | ConvertFrom-Json
            $obj.Id = $known.Id
            $obj.Links = $known.Links
            $json = $obj | ConvertTo-Json
            Write-Output $json

            Write-Output "Updating $dataType entity for '$title'"
            $response = Invoke-RestMethod -Uri $updateUrl -Method Put -Headers $headers -Body $json
            return $response.Id
        }
        catch {
            Write-Output "Failed to create $dataType entity"
            Write-Output $_.Exception.Message
        }
        return
    }

    Write-Output "Creating $dataType entity for '$title'"
    Write-Output $json
    $url = "$seqHost/api/$dataType/"
    $headers = @{ "Content-Type" = "application/json" }
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $json
        return $response.Id
    }
    catch {
        Write-Output "Failed to create $dataType entity"
        Write-Output $_.Exception.Message
    }
}

function New-SeqRetentionPeriod {
    param(
        [Parameter(Mandatory)]$time
    )

    $url = "$seqHost/api/retentionpolicies/"

    $existing = Get-SeqData -dataType 'retentionpolicies'
    foreach ($policy in $existing) {
$json = $policy | ConvertTo-Json
Write-Output 'JSON:'
Write-Output $json
Write-Output 'Policy:'
Write-Output $policy
        $id = $policy.Id
        $deleteUrl = "$url$id"
        Write-Output "Deleting existing retention policy at $deleteUrl"
        Invoke-RestMethod -Uri $deleteUrl -Method Delete
    }

    Write-Output "Setting retention period to $time"
    $json = '{"RetentionTime":"' + $time + '","RemovedSignalExpression":null,"Id":null,"Links":{"Create":"api/retentionpolicies/"}}'
    $headers = @{ "Content-Type" = "application/json" }
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $json
        return $response.Id
    }
    catch {
        Write-Output "Failed to set retention period"
        Write-Output $_.Exception.Message
    }
}


$containerId = docker ps -q -f name=_CONTAINER_NAME_
if (-not $containerId) {
    Write-Output 'Starting Seq'
    docker run -d --name _CONTAINER_NAME_ --restart unless-stopped -p _SEQ_PORT_:80 -p 5341:5341 -e ACCEPT_EULA=Y datalust/seq:_SEQ_VERSION_

    try {
        Write-Output "Resolving host.docker.internal..."
        $hostIp = [System.Net.Dns]::GetHostAddresses("host.docker.internal") | Where-Object { $_.AddressFamily -eq 'InterNetwork' } | Select-Object -First 1 -ExpandProperty IPAddressToString
        Write-Output "Resolved host.docker.internal to $hostIp."
    } 
    catch {
        Write-Output "host.docker.internal is unknown, using 127.0.0.1 as host IP."
        $hostIp = '127.0.0.1'
    }
    
    $seqHost = "http://${hostIp}:_SEQ_PORT_"
    Write-Output "Configuring Seq at $seqHost"
    
    $signals = Get-SeqData -dataType 'signals'
    if ($null -ne $signals) {

        $signalApplicationJson = '{"Title":"Show Application Column","Description":"Include Application as a column","Filters":[],"Columns":[{"Expression":"Application"}],"IsProtected":false,"IsWatched":true,"Grouping":"Explicit","ExplicitGroupName":"Application","OwnerId":null,"Id":null,"Links":{"Create":"api/signals/"}}'
        $signalDebugJson = '{"Title":"Debug","Description":"Filter debug level","Filters":[{"Filter":"@Level in [''debug''] ci","FilterNonStrict":"@Level in [''debug''] ci"}],"Columns":[],"IsProtected":false,"IsWatched":false,"Grouping":"Explicit","ExplicitGroupName":"@Level","OwnerId":null,"Id":null,"Links":{"Create":"api/signals/"}}'
        $signalInfoJson = '{"Title":"Info","Description":"Filter info level","Filters":[{"Filter":"@Level in [''info'',''i'',''information''] ci","FilterNonStrict":"@Level in [''info'',''i'',''information''] ci"}],"Columns":[],"IsProtected":false,"IsWatched":false,"Grouping":"Explicit","ExplicitGroupName":"@Level","OwnerId":null,"Id":null,"Links":{"Create":"api/signals/"}}'
        $signalTraceJson = '{"Title":"Trace","Description":"Filter trace level","Filters":[{"Filter":"@Level in [''trace''] ci","FilterNonStrict":"@Level in [''trace''] ci"}],"Columns":[],"IsProtected":false,"IsWatched":false,"Grouping":"Explicit","ExplicitGroupName":"@Level","OwnerId":null,"Id":null,"Links":{"Create":"api/signals/"}}'

        New-SeqData -dataType 'signals' -existing $signals -title 'Show Application Column' -json $signalApplicationJson
        New-SeqData -dataType 'signals' -existing $signals -title 'Debug' -json $signalDebugJson
        New-SeqData -dataType 'signals' -existing $signals -title 'Info' -json $signalInfoJson
        New-SeqData -dataType 'signals' -existing $signals -title 'Trace' -json $signalTraceJson

        $signals = Get-SeqData -dataType 'signals'
        $signalIds = $signals | ForEach-Object { "`"$($_.Id)`"" }
        $signalIdsString = $signalIds -join ', '

        $workspaces = Get-SeqData -dataType 'workspaces'
        if ($null -ne $workspaces) {
            $workspaceJson = '{"Title":"Personal","Description":null,"OwnerId":"user-admin","IsProtected":false,"DefaultSignalExpression":null,"Content":{"SignalIds":[' + $signalIdsString + '],"QueryIds":["sqlquery-2","sqlquery-3","sqlquery-4","sqlquery-5"],"DashboardIds":["dashboard-14"]},"Id":null,"Links":{"Create":"api/workspaces/"}}'
            New-SeqData -dataType 'workspaces' -existing $workspaces -title 'Personal' -json $workspaceJson
        }

        New-SeqRetentionPeriod -time '2.0:0:00'

        Write-Output 'Seq configuration complete'
    }
}
