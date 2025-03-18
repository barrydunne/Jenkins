import groovy.json.JsonOutput
import java.net.URLEncoder

def call(Map config = [:]) {

    def project = config.project ?: env.DEPLOY_PROJECT_NAME                 // Aws.Playground.Api
    def group = config.group ?: env.DEPLOY_PROJECT_GROUP ?: 'Default'       // Api
    def template = config.template ?: env.DEPLOY_TEMPLATE                   // ecs-service
    def workingDir = config.workingDir ?: '.tf'                             // .tf/api_ecs_cluster_service
    def artifact = config.artifact ?: env.BUILD_ARTIFACT                    // aws-playground-api:2025.0313.2008.0012
    def version = config.version ?: env.BUILD_ID ?: env.BUILD_DISPLAY_NAME  // 2025.0313.2008.0012
    def repo = config.repo ?: env.GIT_URL                                   // http://host.docker.internal:3000/Barry/aws.playground.git
    def commit = config.commit ?: env.GIT_COMMIT                            // d81a24ce3b9501b03125789f91228330b9897724
    def branch = config.branch ?: env.GIT_BRANCH                            // main
    def link = config.link ?: env.RUN_DISPLAY_URL                           // http://localhost:7080/job/Aws.Playground.Api/job/main/12/display/redirect
    def options = config.options instanceof List ? config.options : (config.options ? [ config.options ] : [])
    def downloads = config.downloads instanceof List ? config.downloads : (config.downloads ? [ config.downloads ] : []) // [ 's3://releases/Aws.Playground.Lambda/Aws.Playground.Lambda_2025.0314.1242.001.zip' ]
    def gocdServer = config.gocdServer ?: 'http://host.docker.internal:8153'

    // Check if the required environment variables are set
    if ("${env.GOCD_API_TOKEN}".length() == 0) {
        error("GOCD_API_TOKEN environment variable is not set")
    }

    def baseUrl = "${gocdServer}/go/api"
    def headers = [
        'Accept': 'application/vnd.go.cd+json',
        'Content-Type': 'application/json',
        'Authorization': "Bearer ${env.GOCD_API_TOKEN}"
    ]

    echo("Notify deployment server about new ${project} version ${version} of ${commit} commit in ${branch} branch from ${repo} with link ${link} using template ${template} with options ${options}")

    def jsonPipeline = loadTemplate(template, project, group, workingDir, artifact, version, repo, commit, branch, link, downloads, options)
    def jsonSchedule = loadTemplate('schedule', project, group, workingDir, artifact, version, repo, commit, branch, link, downloads, options)

    createGroup(group, baseUrl, headers)
    createPipeline(jsonPipeline, group, project, baseUrl, headers)
    createSchedule(jsonSchedule, project, repo, branch, baseUrl, headers)
}

private def loadTemplate(String template, String project, String group, String workingDir, String artifact, String version, String repo, String commit, String branch, String link, List downloads, List options) {

    def replacements = [
        '_DEPLOY_PROJECT_NAME_': project ?: 'none',
        '_DEPLOY_PROJECT_GROUP_': group ?: 'none',
        '_WORKING_DIR_': workingDir ?: 'none',
        '_BUILD_ARTIFACT_': artifact ?: 'none',
        '_VERSION_': version ?: 'none',
        '_GIT_URL_': repo ?: 'none',
        '_GIT_COMMIT_': commit ?: 'none',
        '_GIT_BRANCH_': branch ?: 'none',
        '_RUN_DISPLAY_URL_': link ?: 'none',
        '_DOWNLOADS_': downloads ? downloads.collect { "\\\"${it}\\\"" }.join(' ') : "\\\"\\\""
    ]

    options.eachWithIndex { opt, idx ->
        replacements["_OPTIONS_${idx+1}_"] = opt
    }
    
    def jsonTemplate = libraryResource("deploy-templates/${template}.json")
    replacements.each { placeholder, value ->
        echo("Replacing '${placeholder}' with '${value}'")
        jsonTemplate = jsonTemplate.replaceAll(placeholder, java.util.regex.Matcher.quoteReplacement(value))
    }
    return jsonTemplate
}

private def createGroup(String pipelineGroup, String baseUrl, Map headers) {

    if (!getPipelineGroup(pipelineGroup, baseUrl, headers)) {
        newPipelineGroup(pipelineGroup, baseUrl, headers)
        echo("Created pipeline group: ${pipelineGroup}")
    } else {
        echo("Pipeline group already exists: ${pipelineGroup}")
    }
}

private def getPipelineGroup(String pipelineGroup, String baseUrl, Map headers) {

    echo("Looking for pipeline group: ${pipelineGroup}")
    def url = "${baseUrl}/admin/pipeline_groups"
    def response = httpRequest(
        url: url,
        customHeaders: headers.collect { k, v -> [name: k, value: v] },
        httpMode: 'GET',
        validResponseCodes: '200'
    )
    def json = readJSON(text: response.content)
    return (json._embedded.groups ?: []).find { it.name == pipelineGroup }
}

private def newPipelineGroup(String pipelineGroup, String baseUrl, Map headers) {

    echo("Creating pipeline group: ${pipelineGroup}")
    def body = JsonOutput.toJson([ "name": pipelineGroup ])
    def url = "${baseUrl}/admin/pipeline_groups"
    def response = httpRequest(
        url: url,
        customHeaders: headers.collect { k, v -> [name: k, value: v] },
        httpMode: 'POST',
        requestBody: body
    )
    def json = readJSON(text: response.content)
    echo("Created pipeline group: ${json?._links?.self?.href}")
    return json
}

private def createPipeline(String pipelineJson, String pipelineGroup, String pipelineName, String baseUrl, Map headers) {

    // If the pipeline already exists use a PUT to update it with the current branch, otherwise use a POST to create it
    def method = 'POST'
    def newHeaders = new HashMap(headers)
    def etag = getPipelineETag(pipelineName, baseUrl, headers)
    if (etag) {
        newHeaders['If-Match'] = etag
        method = 'PUT'
    }
    else {
        pipelineJson = '{ "group": "' + pipelineGroup + '", "pipeline": ' + pipelineJson + ' }'
    }

    newPipeline(pipelineJson, pipelineName, baseUrl, newHeaders, method)
    echo("Created pipeline: ${pipelineName}")
}

private def getPipelineETag(String pipelineName, String baseUrl, Map headers) {
    
    echo("Looking for pipeline: ${pipelineName}")
    def encodedPipelineName = URLEncoder.encode(pipelineName, 'UTF-8')
    def url = "${baseUrl}/admin/pipelines/${encodedPipelineName}"
    def response = httpRequest(
        url: url,
        customHeaders: headers.collect { k, v -> [name: k, value: v] },
        httpMode: 'GET',
        validResponseCodes: '200,404'
    )
    if (response.status == 404) {
        echo('Pipeline not found')
        return null
    }
    echo("Found existing pipeline: ${pipelineName}")
    def etag = response.headers['ETag'][0]
    echo("Existing pipeline ETag: ${etag}")
    return etag
}

private def newPipeline(String pipelineJson, String pipelineName, String baseUrl, Map headers, String method) {

    def url = "${baseUrl}/admin/pipelines"
    if (method == 'PUT') {
        echo("Updating pipeline: ${pipelineName}")
        def encodedPipelineName = URLEncoder.encode(pipelineName, 'UTF-8')
        url = "${url}/${encodedPipelineName}"
    } else {
        echo("Creating pipeline: ${pipelineName}")
    }

    def newHeaders = new HashMap(headers)
    newHeaders['X-GoCD-Confirm'] = 'true'
    def response = httpRequest(
        url: url,
        customHeaders: newHeaders.collect { k, v -> [name: k, value: v] },
        httpMode: method,
        requestBody: pipelineJson,
        validResponseCodes: '200,202,400,422'
    )
    def json = readJSON(text: response.content)
    if ((response.status == 400) || (response.status == 422)) {
        echo('Pipeline rejected')
        echo("Pipeline:\n${pipelineJson}")
        echo("Response:\n${response.content}")
        error("Pipeline rejected: ${json.message}")
    } else {
        echo("Created pipeline: ${json?._links?.self?.href}")
    }    
    return json
}

private def createSchedule(String scheduleJson, String pipelineName, String repo, String branch, String baseUrl, Map headers) {

    echo("Creating pipeline scheldule: ${pipelineName}")

    def fingerprint = getMaterialFingerprint(repo, branch, baseUrl, headers) ?: 'none'
    scheduleJson = scheduleJson.replaceAll('_FINGERPRINT_', fingerprint)

    echo("-------------------------")
    echo("${scheduleJson}")
    echo("-------------------------")

    def encodedPipelineName = URLEncoder.encode(pipelineName, 'UTF-8')
    def url = "${baseUrl}/pipelines/${encodedPipelineName}/schedule"
    def response = httpRequest(
        url: url,
        customHeaders: headers.collect { k, v -> [name: k, value: v] },
        httpMode: 'POST',
        requestBody: scheduleJson,
        validResponseCodes: '200,202,422'
    )
    def json = readJSON(text: response.content)    
    if (response.status == 422) {
        echo("Schedule pipeline rejected: ${response.content}")
        error("Pipeline schedule rejected: ${json.message}")
    } else {
        echo("Created pipeline schedule: ${json?.message}")
    }
    return json
}

private def getMaterialFingerprint(String gitURL, String branch, String baseUrl, Map headers) {
    echo("Looking for fingerprint: ${gitURL}")
    def url = "${baseUrl}/config/materials"
    def response = httpRequest(
        url: url,
        customHeaders: headers.collect { k, v -> [name: k, value: v] },
        httpMode: 'GET',
        validResponseCodes: '200'
    )
    def json = readJSON(text: response.content)
    def material = json?._embedded?.materials?.find { 
        (it.type == 'git') && 
        (it.attributes?.url == gitURL) &&
        (it.attributes?.branch == branch) 
    }
    return material?.fingerprint
}
