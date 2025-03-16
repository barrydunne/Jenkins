This repository contains custom build agents and library functions for Jenkins.

# Getting started

Build the agents and run Jenkins

```pwsh
./Build-Agents.ps1
./Run-Jenkins.ps1
```

## First Run

If this is the first time running Jenkins:

1. Wait to see the initial password, either from the `Run-Jenkins.ps1` output or the Jenkins docker logs
1. Open Jenkins at http://localhost:7080 and enter the password
1. Choose `Select plugins to install`
1. Add the following plugin:
    * `Source Code Management > GitHub`
1. Press `Install`
1. Create an admin user
1. Press `Save and continue > Save and finish > Start using Jenkins`
1. From the `User > Appearance` menu, set to dark mode because we are all professionals here
1. From `Dashboard > Manage Jenkins > Plugins > Available plugins` install the following plugins
    * `Gitea`
    * `HTTP Request`
    * `Pipeline Utility Steps`
1. From `Dashboard > Manage Jenkins > Nodes > Built-in Node > Configure` set the `Number of executors` to 0
1. From `Dashboard > Manage Jenkins > Nodes` go to each node's page and copy the secret
1. Update the secret values in the `Run-Jenkins.ps1` script
1. Delete the three running agent containers
1. Run the agents again using `Run-Jenkins.ps1`
1. Verify the agents are now appearing as connected in the Jenkins dashboard


## Shared library

1. Open `Dashboard > Manage Jenkins > System`
1. Find `Global Trusted Pipeline Libraries > Add`
    * Name: `barry-lib`
    * Default version: `main`
    * Project repository: `https://github.com/barrydunne/jenkins.git`
    * Library Path: `library`
1. Save


## GoCD

See https://github.com/barrydunne/gocd

If using GoCD, add the API key

1. Open `Dashboard > Manage Jenkins > System`
1. Find `Global properties`
1. Check `Environment variables`
1. Add
    * Name `GOCD_API_TOKEN`
    * Value `<your GoCD token>`


## GitHub


If using GitHub, add the server

1. Open `Dashboard > Manage Jenkins > System` 
1. Find `GitHub Servers`
1. `Add GitHub Server > GitHub Server`
    * Name: `github`
1. `Credentials > Add > Jenkins`
    * Kind: `Username with password`
    * Username: `jenkins`
    * Password: `<your token>` _Generate at https://github.com/settings/personal-access-tokens_
        * Commit statuses: `Read and write`
        * Contents: `Read-only`
        * Metadata: `Read-only`
        * Pull requests: `Read-only`
    * Id: `github-jenkins`
1. Check `Manage hooks`

**NOTE** When creating the github personal access token it should have minimum repository permissions:
* Commit statuses: `Read and write`
* Contents: `Read-only`
* Metadata: `Read-only`
* Pull requests: `Read-only`

### Webbook

Use ngrok to tunnel to Jenkins with either of these commands
```
ngrok http --host-header="localhost:7080" 7080
ngrok http --host-header="localhost:7080" --url=your-domain.ngrok-free.app 7080
```

In GitHub add a webhook for a repository by

1. Go to the repository
1. `Settings > Webhooks > Add Webhook`
1. Payload URL: `https://<your-ngrok-domain>/github-webhook/`
1. Content type: `application/json`
1. Add Webhook


## Gitea

See https://github.com/barrydunne/gitea

If using Gitea, add the server

1. Open `Dashboard > Manage Jenkins > System` 
1. Find `Gitea Servers`
1. `Add > Gitea Server`
    * Name: `docker`
    * Server URL: `http://host.docker.internal:3000`
1. Check `Manage hooks`
1. Add credentials with Kind: `Gitea Personal Access Token`
1. Add the token. The token must have the following minimum permissions in Gitea:
    * orgnaisation `Read`
    * repository `Read and Write`
    * user `Read`
1. Give it an Id, eg `gitea-token`

### Webbook

In Gitea add a webhook for a repository by

1. Go to the repository
1. `Settings > Webhooks > Add Webhook > Gitea`
1. Target URL: `http://host.docker.internal:7080/gitea-webhook/post`
1. Add Webhook

### ALLOWED_HOST_LIST

For a webhook to work the target must be allowed.

1. Edit the `data/gitea/conf/app.ini` file in Gitea to include these lines:
    ```
    [webhooks]
    ALLOWED_HOST_LIST = *
    ```
1. Restart Gitea



## Adding an example project

See https://github.com/barrydunne/aws.playground

1. Tap `New Item` on the dashboard
1. Give it a name `Aws.Playground.Environment`
1. Select `Multibranch Pipeline`
1. OK
1. Display Name: `Aws.Playground.Environment`
1. Branch Sources: `Add source > Gitea`
    | Gitea | GitHub |
    |-------|--------|
    | Server `<select server added above>` | Credentials `<select credentials added above>` |
    | Credentials `<select credentials added above>` | Repository HTTPS URL `https://github.com/barrydunne/aws.playground.git` |
    | Owner: `Barry` | |
    | Repository: `Aws.Playground` | |
1. Build Configuration > Mode `by Jenkinsfile` > Script Path `.build/Jenkinsfile.environment`
1. Discard old items (_set desired values_)
1. Save
1. Monitor the build progress with any of these views:
    * http://localhost:7080/job/Aws.Playground.Environment/job/main/
    * http://localhost:7080/job/Aws.Playground.Environment/job/main/1/console
    * http://localhost:7080/job/Aws.Playground.Environment/job/main/1/pipeline-console/
    * http://localhost:7080/job/Aws.Playground.Environment/job/main/multi-pipeline-graph/





