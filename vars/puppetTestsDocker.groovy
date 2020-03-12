#!/usr/bin/env groovy

def call(Map config) {
  try {
    stage('Lint') {
      milestone label: 'Test'

      sh "${config.container} /usr/local/bin/pdk validate"
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

      sh "${config.container} /usr/local/bin/pdk test unit --clean-fixtures --format junit:results/report.xml"
      
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
