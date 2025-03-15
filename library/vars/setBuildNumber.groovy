def call() {
    script {
        def timestamp = sh(script: 'date +"%Y.%m%d.%H%M"', returnStdout: true).trim()
        def buildNumberSegment = String.format("%04d", currentBuild.number % 10000)
        def shortCommit = env.GIT_COMMIT ? env.GIT_COMMIT.substring(0, 7) : null
        def lastSegment = shortCommit ?: buildNumberSegment
        env.BUILD_ID = "${timestamp}.${lastSegment}"
        currentBuild.displayName = "${env.BUILD_ID}"
    }
    echo "Pipeline initialized with BUILD_ID: ${env.BUILD_ID}"
}
