#!/usr/bin/env groovy

def call(Map config) {
  if (!config.DOCKER_REGISTRY_CREDS_ID) {
    error 'DOCKER_REGISTRY_CREDS_ID is required to use Docker builds'
  }
  if (!config.DOCKER_REGISTRY_URL){
    error 'DOCKER_REGISTRY_URL is required to use Docker builds'
  }
  if (!config.AWS_DEFAULT_REGION){
    env.AWS_DEFAULT_REGION = 'us-west-2'
  } else {
    env.AWS_DEFAULT_REGION = config.AWS_DEFAULT_REGION
  }

  env.BRANCH_NAME = env.BRANCH_NAME.split('-')[0].toLowerCase()
  echo "BRANCH_NAME: ${env.BRANCH_NAME}"
  
  config.DOCKER_REGISTRY = config.DOCKER_REGISTRY_URL.split('https://')[1]
  env.REPO_NAME = env.JOB_NAME.split('/')[0]
  env.PDK_VOLUME = "${env.REPO_NAME}_${env.BRANCH_NAME}_pdk_gems"
  env.PUPPETLABS_VOLUME = "${env.REPO_NAME}_${env.BRANCH_NAME}_puppetlabs_cache"
  
  docker.withRegistry(config.DOCKER_REGISTRY_URL, "ecr:${env.AWS_DEFAULT_REGION}:${config.DOCKER_REGISTRY_CREDS_ID}") {

    containerArgs = "--name ${env.BUILD_TAG} -v ${PDK_VOLUME}:/.pdk -v ${PUPPETLABS_VOLUME}:/.puppetlabs -e PDK_DISABLE_ANALYTICS=true -e PDK_FEATURE_FLAGS=controlrepo"

    docker.image("${config.DOCKER_REGISTRY}:${env.PUPPET_VERSION}").inside(containerArgs) {

      try {
        stage('Lint') {
          milestone label: 'Test'

          sh "pdk validate --puppet-version ${config.PUPPET_VERSION}"
        }
      } catch(Exception e) {
        junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
        
        currentBuild.result = 'FAILURE'
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
        throw e
      }

      try {
        stage('Unit Test') {
          milestone label: 'Test'

          sh "pdk test unit --puppet-version ${config.PUPPET_VERSION} --clean-fixtures --format junit:${config.TEST_RESULTS_DIR}/report.xml"
          
          junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
        
        currentBuild.result = 'FAILURE'
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
        throw e
      }

      try {
        stage('Deploy') {
          milestone label: 'Deploy'

          if (config.DEPLOY_WITH_R10K == 'true') {
            try {
              stage('Deploy'){
                milestone label: 'Deploy'
                def deploy_branch = config.R10K_DEPLOY_BRANCH.any {it == env.BRANCH_NAME}
                  
                  if (deploy_branch) {
                    sh returnStdout: true, script: "curl --request POST -k --url ${config.R10K_DEPLOY_URL}/payload  --header \'content-type: application/json\' ${config.BASIC_AUTH_HEADER} --data \'{\"push\":{\"changes\":[{\"new\":{\"name\":\"${env.BRANCH_NAME}\"}}]}}\'"

                    currentBuild.result = 'SUCCESS'
                  }
              }
            } catch(Exception e) {
              junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
              currentBuild.result = 'FAILURE'
              if (config.DEBUG == 'false') {
                puppetSlack(config.SLACK_CHANNEL)
              }
              throw e
            }
          }

          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        currentBuild.result = 'FAILURE'
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
        throw e
      }
    } // image
  } // withRegistry
} // top level function
