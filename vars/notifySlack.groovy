#!/usr/bin/env groovy
import hudson.tasks.test.AbstractTestResultAction;

def call(String channel){
  def String color

  if(currentBuild.result == null) {
    if(currentBuild.id == '1'){
      color = 'blue'
    }
    else if(currentBuild.previousBuild.result == 'SUCCESS'){
      color = 'good'
    }
    else {
      color = 'danger'
    }
    started = "Build started"
    test_results_string = ''
    slackSend channel: channel, failOnError: true, color: color, message: "${env.JOB_NAME} - ${env.BUILD_DISPLAY_NAME} ${started} (<${env.BUILD_URL}|Open>)\n${test_results_string}"
  }
  else if(currentBuild.result == 'SUCCESS'){
    color = 'good'
    results()
    slackSend channel: channel, failOnError: true, color: color, message: "${env.JOB_NAME} - ${env.BUILD_DISPLAY_NAME} *${currentBuild.result}* (<${env.BUILD_URL}|Open>)\n${test_results_string}"
  }
  else {
    color = 'danger'
    results()
    slackSend channel: channel, failOnError: true, color: color, message: "${env.JOB_NAME} - ${env.BUILD_DISPLAY_NAME} *${currentBuild.result}* (<${env.BUILD_URL}|Open>)\n${test_results_string}"
  }
}

@NonCPS
def results(){
  def diff
  AbstractTestResultAction<?> resultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
  if (resultAction != null) {
    diff = resultAction.getFailureDiffString()
    diff = diff.split( )
    if(diff.size() <= 1) {
      diff = ':robot_face:'
    }
    else {
      diff = diff[1]
    }
    def passed = resultAction.getTotalCount() - resultAction.getFailCount()
    test_results_string = "Passed: ${passed} | Failed: ${resultAction.getFailCount()} | Skipped: ${resultAction.getSkipCount()} | Diff: ${diff}"
  }
  else {
    test_results_string = 'No tests found!'
  }
  return test_results_string
}
