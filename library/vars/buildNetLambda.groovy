def call(Map config = [:]) {

    def path = config.path ?: '.'
    def zip = config.zip ?: 'lambda.zip'
    def architecture = config.architecture ?: 'arm64'

    dir(path) {
        echo("Building .NET Lambda package ${zip} for ${architecture}")
        sh("dotnet lambda package ${zip} --function-architecture ${architecture}")        
    }
}