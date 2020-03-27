# puppet-jenkins-shared-libraries

Testing your Puppet on every change should be a smooth process. With Jenkins pipelines you can describe the entire process through code. We did the hard work to make our puppet testing and deployment library open source. It gives you a drop in pipeline shared library with a configurable Jenkinsfile.

## Prerequisites

If you're new to Jenkins pipelines you should go read the [documentation](https://jenkins.io/doc/book/pipeline/) before proceeding to get a sense for what to expect using this code. The rest of the setup process will assume you have basic knowledge of Jenkins or CI/CD jobs in general.

OS

- rvm installed in the jenkins user
- git
- build-essential
- docker if needed for acceptance tests

Jenkins

- Version: > 2.7.3 - tested on (2.89.4 LTS)

Plugins

- slack
- pipeline (workflow-aggregator)
- git
- timestamper
- credentials
- junit

Scripts Approval

When the job runs the first time you will need to work through allowing certain functions to execute in the groovy sandbox. This is normal as not all high use groovy functions are in the default safelist but more are added all the time.

Puppet

Validation and testing is done using the [PDK] (Puppet Development Kit).

[PDK]: https://puppet.com/docs/pdk/1.x/pdk.html

### Manage with Puppet

The following modules work great to manage a Jenkins instance.

- maestrodev/rvm
- puppetlabs/apache
- rtyler/jenkins
- garethr/docker

## Jenkinsfile

``` groovy
puppet {
  PUPPET_VERSION = '6'
  RUBY_GEMSET = 'puppet'
  TEST_RESULTS_DIR = 'testresults'
  DEPLOY_WITH_R10K = 'true'
  R10K_DEPLOY_URL = 'https://puppet.my-company.com:8088'
  R10K_DEPLOY_BASIC_AUTH_CRED_ID = 'puppet-basic-auth'
  R10K_DEPLOY_BRANCH = ['production', 'support']
  SLACK_CHANNEL = '#puppet'
  DEBUG = 'false'
  DOCKER_REGISTRY_CREDS_ID = 'creds_id_to_registry'
  DOCKER_REGISTRY_URL = 'https://hub.docker.io'
  AWS_DEFAULT_REGION = 'us-east-1'
}
```

### Required Parameters

- **PUPPET_VERSION:** The version of Puppet to use for validation and testing. [String]
- **RUBY_GEMSET:** Name of the gemset to create. [String]

### Optional Parameters

- **DEPLOY_WITH_R10K:** Deploy branch with r10k. [String] (true|false) Default: false **NOTE:** This requires r10k to be configured [web hook support](https://forge.puppet.com/puppet/r10k#webhook-support)
- **TEST_RESULTS_DIR:** Directory to look for junit output of test results. [String] Default: testresults
- **R10K_DEPLOY_URL:** Required if DEPLOY_WITH_R10K is true. The URL of the Puppet server. [String]
- **R10K_DEPLOY_BASIC_AUTH_CRED_ID:** If your Puppet server is behind basic auth then set a credential in Jenkins. This is the credentialsId set in the Jenkins credentials plugin. [String]
- **R10K_DEPLOY_BRANCH:** Env branch(s) to deploy with r10k [List]
- **SLACK_CHANNEL:** Specify the Slack channel to use for notifications. [String] Default: #puppet
- **DEBUG:** Turn off Slack notifications and turn on more console output. [String] Default: false
- **DOCKER_REGISTRY_URL:** The private Docker registry URL. Required to build with Docker. [String]
- **DOCKER_REGISTRY_CREDS_ID:** The private Docker registry credentials ID in Jenkins. Required to build with Docker. [String]
- **AWS_DEFAULT_REGION:** The AWS region of you Elastic Container Registry

## Acceptance Test Configuration

Running acceptance tests can be a bit tricky and there are a lot of things that go into running the tests. Most of them are automated with beaker but there are still a few things we need to do to run them effectively. Lets start with a snippet from the above Jenkinsfile.

## Test Results

All test results are assumed to be in JUnit format and placed in a single directory.

## Docker Builds

If a docker-compose.yml is present in the repo and `DOCKER_REGISTRY_URL` and `DOCKER_REGISTRY_CREDS_ID` are set builds will run with Docker. All containers are cleaned up after the build completes. Gems are stored in a Docker volume per branch (eg. puppet_master-gems). This allows for faster builds since gems are cached between runs. This can, however, lead to filling up the disk quickly it is recommended that you run a periodic clean job to remove old volumes. See Jenkinsfile.clean_example.

**Note:** The current setup only works with AWS ECR but can easily be adapted to work with other private registries.

## [Changelog](CHANGELOG.md)

## [MIT License](LICENSE)
