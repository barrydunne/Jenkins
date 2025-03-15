Write-Output 'Checking if _CONTAINER_NAME_ container is already running...'
$containerId = docker ps -q -f name=_CONTAINER_NAME_
if (-not $containerId) {

    Write-Output 'Start LocalStack'
    if ($env:LOCALSTACK_AUTH_TOKEN) {
        docker run -d --name _CONTAINER_NAME_ -p 4566:4566 -p 4510-4559:4510-4559 -e PERSISTENCE=1 -e LAMBDA_KEEPALIVE_MS=120000 -e LOCALSTACK_AUTH_TOKEN=$env:LOCALSTACK_AUTH_TOKEN -e ENFORCE_IAM=1 -e DISABLE_CORS_CHECKS=1 -v //var/run/docker.sock:/var/run/docker.sock localstack/localstack-pro:_LOCALSTACK_VERSION_
    }
    else {
        docker run -d --name _CONTAINER_NAME_ -p 4566:4566 -p 4510-4559:4510-4559 -e PERSISTENCE=1 -e LAMBDA_KEEPALIVE_MS=120000 -e ENFORCE_IAM=1 -e DISABLE_CORS_CHECKS=1 -v //var/run/docker.sock:/var/run/docker.sock localstack/localstack:_LOCALSTACK_VERSION_
    }    

    Write-Output 'Waiting for LocalStack services to be ready...'
    $services = @('ecr', 'ecs', 'lambda', 's3', 'sns', 'sqs')
    $ready = $false
    $timeout = 300
    $startTime = Get-Date

    try {
        Write-Output "Resolving host.docker.internal..."
        $hostIp = [System.Net.Dns]::GetHostAddresses("host.docker.internal") | Where-Object { $_.AddressFamily -eq 'InterNetwork' } | Select-Object -First 1 -ExpandProperty IPAddressToString
        Write-Output "Resolved host.docker.internal to $hostIp."
    } 
    catch {
        Write-Output "host.docker.internal is unknown, using 127.0.0.1 as host IP."
        $hostIp = '127.0.0.1'
    }

    $url = "http://${hostIp}:4566/_localstack/health"
    Write-Output "Checking health with $url."

    while (-not $ready -and ((Get-Date) - $startTime).TotalSeconds -lt $timeout) {
        try {
            $healthCheck = Invoke-WebRequest -Uri $url -UseBasicParsing
            if ($healthCheck.StatusCode -eq 200) {
                $health = ConvertFrom-Json $healthCheck.Content
                $ready = $true
                foreach ($service in $services) {
                    if ($health.services.$service -ne "available" -and $health.services.$service -ne "running") {
                        Write-Output "Service '$service' not yet available. Status: $($health.services.$service)"
                        $ready = $false
                        break
                    }
                }
                if ($ready) {
                    Write-Output 'All required services are available.'
                } else {
                    Write-Output 'Not all required services are available yet. Waiting...'
                    Start-Sleep -s 1
                }
            } else {
                Write-Output "Health check failed with status code: $($healthCheck.StatusCode). Waiting..."
                Start-Sleep -s 5
            }
        } catch {
            Write-Output "Waiting for successful health check..."
            Start-Sleep -s 5
        }
    }

    if (-not $ready) {
        Write-Error "Timeout waiting for LocalStack services to become available."
        exit 1
    }

    Write-Output 'LocalStack services are ready.'
}
