def call(Map config = [:]) {

    def name = config.name ?: "seq"
    def version = config.version ?: "2024.3"
    def port = config.port ?: 10081

    def script = libraryResource("powershell/Run-Seq.ps1")

    script = script.replaceAll('_CONTAINER_NAME_', name)
    script = script.replaceAll('_SEQ_VERSION_', version)
    script = script.replaceAll('_SEQ_PORT_', "${port}")

    pwsh script
}