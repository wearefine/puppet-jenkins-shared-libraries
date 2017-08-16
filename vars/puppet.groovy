#!/usr/bin/env groovy

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (!config.DEBUG) {
    env.DEBUG = 'false'
  }
  if (!config.SLACK_CHANNEL) {
    env.SLACK_CHANNEL = '#puppet'
  }
  if (!config.RUBY_VERSION){
    error 'RUBY_VERSION is required'
  } else {
    env.RUBY_VERSION = config.RUBY_VERSION
  }
  if (!config.RUBY_GEMSET){
    error 'RUBY_GEMSET is required'
    
  } else {
    env.RUBY_GEMSET = config.RUBY_GEMSET
  }
  if (!config.TEST_RESULTS_DIR) {
    config.TEST_RESULTS_DIR = 'testresults'
  }
  if (!config.RUN_ACCEPTANCE) {
    config.RUN_ACCEPTANCE = 'false'
  } else if (config.RUN_ACCEPTANCE == 'true') {
    if (!config.ACCEPTANCE_TESTS) {
      error 'ACCEPTANCE_TESTS is required when RUN_ACCEPTANCE is set to true'
    }
  }
  if (!config.DEPLOY_WITH_R10K) {
    config.DEPLOY_WITH_R10K = 'false'
  } else if (config.DEPLOY_WITH_R10K == 'true') {
    if (!config.R10K_DEPLOY_URL) {
      error 'R10K_DEPLOY_URL is required when DEPLOY_WITH_R10K is set to true'
    }
    if (config.R10K_DEPLOY_BASIC_AUTH_CRED_ID) {
      withCredentials([string(credentialsId: config.R10K_DEPLOY_BASIC_AUTH_CRED_ID, variable: 'basic_auth')]) {
        config.BASIC_AUTH_HEADER = "--header \'authorization: ${basic_auth}\'"
      }
    } else {
      config.BASIC_AUTH_HEADER = ''
    }
    if (!config.R10K_DEPLOY_BRANCH) {
      error 'R10K_DEPLOY_BRANCH is required when DEPLOY_WITH_R10K is set to true'
    }
  }

  // if (env.DEBUG == 'false') {
    println config.RUBY_VERSION
    println config.RUBY_GEMSET
    println config.TEST_RESULTS_DIR
    println config.RUN_ACCEPTANCE
    println config.ACCEPTANCE_TESTS
    println config.DEPLOY_WITH_R10K
    println config.R10K_DEPLOY_URL
    println config.R10K_DEPLOY_BASIC_AUTH_CRED_ID
    println config.R10K_DEPLOY_BRANCH
    println config.SLACK_CHANNEL
    println config.DEBUG
  // }

  node {
    timestamps {
      if (env.DEBUG == 'false') {
        notifySlack(env.SLACK_CHANNEL)
      }

      try {
        stage('Checkout') {
          checkout scm
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        currentBuild.result = 'FAILURE'
        if (env.DEBUG == 'false') {
          notifySlack(env.SLACK_CHANNEL)
        }
        throw e
      }
      if (env.DEBUG == 'true') {
        echo "BRANCH_NAME: ${env.BRANCH_NAME}"
      }

      try {
        stage('Install Dependancies'){
          milestone label: 'Install Dependancies'
          retry(2) {
            rvm('bundle install')
          }
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        currentBuild.result = 'FAILURE'
        if (env.DEBUG == 'false') {
          notifySlack(env.SLACK_CHANNEL)
        }
        throw e
      }

      try {
        stage('Unit Test'){
          milestone label: 'Unit Test'
          dir(config.TEST_RESULTS_DIR) {
            deleteDir()
          }
          rvm("rake test")
          junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
        currentBuild.result = 'FAILURE'
        if (env.DEBUG == 'false') {
          notifySlack(env.SLACK_CHANNEL)
        }
        throw e
      }

      try {
        stage('Acceptance Test'){
          milestone label: 'Acceptance Test'
          if(config.RUN_ACCEPTANCE == 'true') {
            parallel config.ACCEPTANCE_TESTS
          }
          junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
        currentBuild.result = 'FAILURE'
        if (env.DEBUG == 'false') {
          notifySlack(env.SLACK_CHANNEL)
        }
        throw e
      }
      if (config.DEPLOY_WITH_R10K == 'true') {
        try {
          stage('Deploy'){
            milestone label: 'Deploy'
            def deploy_branch = R10K_DEPLOY_BRANCH.any {it == env.BRANCH_NAME}
              if (deploy_branch) {
                sh returnStdout: true, script: "curl --request POST -k --url ${env.R10K_DEPLOY_URL}/payload  --header \'content-type: application/json\' ${config.BASIC_AUTH_HEADER} --data \'{\"push\":{\"changes\":[{\"new\":{\"name\":\"${env.BRANCH_NAME}\"}}]}}\'"
                currentBuild.result = 'SUCCESS'
              }
          }
        } catch(Exception e) {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: "${config.TEST_RESULTS_DIR}/*.xml"
          currentBuild.result = 'FAILURE'
          if (env.DEBUG == 'false') {
            notifySlack(env.SLACK_CHANNEL)
          }
          throw e
        }
      }
    } // timestamps
    if (env.DEBUG == 'false') {
      notifySlack(env.SLACK_CHANNEL)
    }
  } //node
}
