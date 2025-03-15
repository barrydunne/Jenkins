$data = Join-Path -Path $PSScriptRoot -ChildPath 'data'

$containerId = docker ps -q -f name=jenkins
if (-not $containerId) {
    Write-Host 'Starting Jenkins...'
    docker run -d `
        --name jenkins `
        --restart unless-stopped `
        -p 7080:8080 `
        -p 50000:50000 `
        -v ${data}:/var/jenkins_home `
        jenkins/jenkins:2.501-jdk21
}

$crumbSalt = Join-Path -Path $PSScriptRoot -ChildPath 'data' -AdditionalChildPath 'secrets', 'jenkins.model.Jenkins.crumbSalt'
if (-not (Test-Path $crumbSalt)) {
    $initialAdminPassword = Join-Path -Path $PSScriptRoot -ChildPath 'data' -AdditionalChildPath 'secrets', 'initialAdminPassword'
    $attempt = 0
    $password = $null
    while (-not $password -and $attempt -lt 20) {
        if (Test-Path $initialAdminPassword) {
            $password = Get-Content -Path $initialAdminPassword -Raw -ErrorAction SilentlyContinue
            if ($password) {
                $password = $password.Split("`n")[0].Trim()
                break
            }
        }
        if (-not $password) {
            $attempt++
            Write-Host "Initial password not found. Retrying in 5 seconds. Attempt $attempt of 20."
            Start-Sleep -Seconds 5
        }
    }
    if (-not $password) {
        Write-Host "Initial password not found after $attempt attempts."
    }
    else {
        Write-Host "##################################################"
        Write-Host "Initial password: $password"
        Write-Host "##################################################"
    }
}

$containerId = docker ps -q -f name=jenkins-agent-dotnet9
if (-not $containerId) {
    Write-Host 'Starting Jenkins .Net9 Agent...'
    docker run -d `
        --name jenkins-agent-dotnet9 `
        --restart unless-stopped `
        -e JENKINS_AGENT_NAME=dotnet9 `
        -e JENKINS_URL=http://host.docker.internal:7080 `
        -e JENKINS_SECRET=476606c0e9025ac71da2d0e6cc1fe6b01800f99f4ac26e7a5d57014064abfc85 `
        -e DOTNET_CLI_TELEMETRY_OPTOUT=1 `
        my-jenkins-agents/dotnet9
}

$containerId = docker ps -q -f name=jenkins-agent-docker
if (-not $containerId) {
    Write-Host 'Starting Jenkins Docker Agent...'
    docker run -d `
        --name jenkins-agent-docker `
        --restart unless-stopped `
        -v //var/run/docker.sock:/var/run/docker.sock `
        -e LOCALSTACK_AUTH_TOKEN=$env:LOCALSTACK_AUTH_TOKEN `
        -e JENKINS_AGENT_NAME=docker `
        -e JENKINS_URL=http://host.docker.internal:7080 `
        -e JENKINS_SECRET=8c30d59fd291e860b8a68c2890085c79febc759b67dd37bdef50b86a2f68b66b `
        my-jenkins-agents/docker
}

$containerId = docker ps -q -f name=jenkins-agent-terraform
if (-not $containerId) {
    Write-Host 'Starting Jenkins Terraform Agent...'
    docker run -d `
        --name jenkins-agent-terraform `
        --restart unless-stopped `
        -e JENKINS_AGENT_NAME=terraform `
        -e JENKINS_URL=http://host.docker.internal:7080 `
        -e JENKINS_SECRET=673c766077e32ccc325ec9c97a2b21cf47c4d6baa72993a0ed61102b6aebd603 `
        my-jenkins-agents/terraform
}
