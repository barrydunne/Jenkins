def call(Map config = [:]) {

    def path = config.path ?: '.'
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def image = config.image ?: ''
    def tag = config.tag ?: 'latest'
    def buildOptions = config.buildOptions ?: "--build-arg IMAGE_VERSION=${tag}"

    dir(path) {
        echo("Building ${image}:${tag} using ${dockerfile} in ${path}")
        sh("docker build -t ${image}:${tag} -f ${dockerfile} . ${buildOptions}")
    }
}