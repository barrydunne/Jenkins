def call(Map config = [:]) {

    def name = config.name ?: 'localstack'
    def version = config.version ?: 'latest'

    def script = libraryResource('powershell/Run-LocalStack.ps1')

    script = script.replaceAll('_CONTAINER_NAME_', name)
    script = script.replaceAll('_LOCALSTACK_VERSION_', version)

    pwsh(script)
}