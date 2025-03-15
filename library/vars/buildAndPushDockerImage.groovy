def call(Map config = [:]) {

    def imageName = config.imageName ?: env.IMAGE_NAME
    def buildId = config.buildId ?: env.BUILD_ID
    def dockerFile = config.dockerFile ?: env.DOCKERFILE
    def workDir = config.workDir ?: env.WORK_DIR

    pwsh """
    \$tag = "${imageName}:${buildId}"
    \$repositoryName = "${imageName}"

    Write-Output "Building \$tag using ${dockerFile} in ${workDir}"
    Set-Location "${workDir}"
    docker build -t \$tag -f "${dockerFile}" . --build-arg IMAGE_VERSION=${buildId}
    if (\$LASTEXITCODE -ne 0) { exit 1 }

    Write-Output "Creating ECR Repository \$repositoryName"
    aws ecr create-repository --profile localstack --repository-name \$repositoryName --image-scanning-configuration scanOnPush=true --no-cli-pager
    if (\$LASTEXITCODE -ne 0) { exit 1 }

    Write-Output "Tagging \$repositoryName/\$tag"
    docker tag \$tag \$repositoryName/\$tag
    if (\$LASTEXITCODE -ne 0) { exit 1 }

    Write-Output "Pushing \$repositoryName/\$tag"
    docker push \$repositoryName/\$tag
    if (\$LASTEXITCODE -ne 0) { exit 1 }
    """
}