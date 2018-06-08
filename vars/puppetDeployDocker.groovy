#!/usr/bin/env groovy

def call(Map config) {
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
}