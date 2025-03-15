def call(Map config = [:]) {

    def waitMinutes = config.waitMinutes ?: 120
    def approvalVar = config.envVarName ?: 'DEPLOYMENT_APPROVED'

    try {
        timeout(time: waitMinutes, unit: "MINUTES") {
            input(message: 'Do you want this build to be deployed?', ok: 'Deploy', cancel: 'Skip deployment')
            env[approvalVar] = 'yes'
        }
    } catch (e) {
        echo "Deployment skipped."
        env[approvalVar] = 'no'
    }
}
