def call(Map config = [:]) {

    def profile = config.profile ?: 'default'
    def image = config.image ?: ''
    def tag = config.tag ?: 'latest'

    echo("Checking for ECR repository ${image}")
    try {

        def repositoryUri = findRepository(profile, image)
        if (repositoryUri) {
            echo("Repository ${image} already exists.")
        }
        else {
            echo("Creating ECR repository ${image}")
            sh(script: "aws ecr create-repository --profile ${profile} --repository-name ${image} --image-scanning-configuration scanOnPush=true --no-cli-pager")
            repositoryUri = findRepository(profile, image)
            if (!repositoryUri) {
                error("Failed to create ECR repository ${image}")
            }
        }
        
        echo("Tagging ${repositoryUri}:${tag}")
        sh(script: "docker tag ${image}:${tag} ${repositoryUri}:${tag}")
        echo("Pushing ${repositoryUri}:${tag}")
        sh(script: "docker push ${repositoryUri}:${tag}")

        env.PUSHED_IMAGE_ID = "${repositoryUri}:${tag}"        
        
    } catch (Exception e) {
        echo("Failed to push Docker image to AWS ECR: ${e.message}")
        throw e
    }
}

def findRepository(String profile, String image) {
    def repositories = sh(script: "aws ecr describe-repositories --profile ${profile} --no-cli-pager", returnStdout: true)
    def jsonRepositories = readJSON(text: repositories)
    if (jsonRepositories.repositories) {
        for (repo in jsonRepositories.repositories) {
            if (repo.repositoryName == image) {
                return repo.repositoryUri
            }
        }
    }
    return null
}