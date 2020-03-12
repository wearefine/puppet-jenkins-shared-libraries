#!/usr/bin/env groovy

def call(Map config) {
  try {
    stage('Install Dependancies') {
      milestone label: 'Install Dependancies'
      retry(2) {
        sh "${config.container} ./install_pdk.sh"
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
