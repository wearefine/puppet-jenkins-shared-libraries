#!/usr/bin/env groovy

def call(Map config) {
  try {
    stage('Unit Test') {
      milestone label: 'Test'

      sh "${config.container} rake test"
      
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

  if(config.RUN_ACCEPTANCE == 'true') {
    try {
      stage('Acceptance Test'){
        milestone label: 'Acceptance Test'
        
        parallel config.ACCEPTANCE_TESTS
        
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
  }
}