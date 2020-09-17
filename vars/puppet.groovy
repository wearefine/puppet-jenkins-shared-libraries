#!/usr/bin/env groovy

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (!config.DEBUG) {
    config.DEBUG = 'false'
    env.DEBUG = 'false'
  }
  if (!config.SLACK_CHANNEL) {
    config.SLACK_CHANNEL = '#puppet'
  }
  if (!config.PUPPET_VERSION){
    error 'PUPPET_VERSION is required'
  } else {
    env.PUPPET_VERSION = config.PUPPET_VERSION
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
        config.BASIC_AUTH_HEADER = "--header \"Authorization: Basic ${basic_auth}\""
      }
    } else {
      config.BASIC_AUTH_HEADER = ''
    }
    if (!config.R10K_DEPLOY_BRANCH) {
      error 'R10K_DEPLOY_BRANCH is required when DEPLOY_WITH_R10K is set to true'
    }
  }

  if (config.DEBUG == 'true') {
    echo "RUBY_VERSION: ${config.RUBY_VERSION}"
    echo "RUBY_GEMSET: ${config.RUBY_GEMSET}"
    echo "TEST_RESULTS_DIR: ${config.TEST_RESULTS_DIR}"
    echo "RUN_ACCEPTANCE: ${config.RUN_ACCEPTANCE}"
    echo "ACCEPTANCE_TESTS: ${config.ACCEPTANCE_TESTS}"
    echo "DEPLOY_WITH_R10K: ${config.DEPLOY_WITH_R10K}"
    echo "R10K_DEPLOY_URL: ${config.R10K_DEPLOY_URL}"
    echo "R10K_DEPLOY_BASIC_AUTH_CRED_ID: ${config.R10K_DEPLOY_BASIC_AUTH_CRED_ID}"
    echo "R10K_DEPLOY_BRANCH: ${config.R10K_DEPLOY_BRANCH}"
    echo "SLACK_CHANNEL: ${config.SLACK_CHANNEL}"
    echo "DEBUG: ${config.DEBUG}"
  }

  node {
    timestamps {
      if (config.DEBUG == 'false') {
        puppetSlack(config.SLACK_CHANNEL)
      }

      try {
        stage('Checkout') {
          checkout scm
          currentBuild.result = 'SUCCESS'
        }
      } catch(Exception e) {
        currentBuild.result = 'FAILURE'
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
        throw e
      }
      if (config.DEBUG == 'true') {
        echo "BRANCH_NAME: ${env.BRANCH_NAME}"
      }

      def dockerBuild = fileExists 'docker-compose.yml'
      if (dockerBuild) {
        puppetDocker(config)
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
      } else {

        try {
          stage('Install Dependancies'){
            milestone label: 'Install Dependancies'
            retry(2) {
              puppetRvm('bundle install')
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

        try {
          stage('Unit Test'){
            milestone label: 'Unit Test'
            dir(config.TEST_RESULTS_DIR) {
              deleteDir()
            }
            puppetRvm("rake test")
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

        if (config.DEPLOY_WITH_R10K == 'true') {
          try {
            stage('Deploy'){
              milestone label: 'Deploy'
              def deploy_branch = config.R10K_DEPLOY_BRANCH.any {it == env.BRANCH_NAME}
                if (deploy_branch) {
                  sh returnStdout: true, script: "curl --request POST -k --url ${config.R10K_DEPLOY_URL}/payload --header \"Content-Type: application/json\" ${config.BASIC_AUTH_HEADER} --data \"{\"push\":{\"changes\":[{\"new\":{\"name\":\"${env.BRANCH_NAME}\"}}]}}\""
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
        if (config.DEBUG == 'false') {
          puppetSlack(config.SLACK_CHANNEL)
        }
      }
      cleanWs notFailBuild: true
    } // timestamps
  } //node
}
