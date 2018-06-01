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
  env.GEM_VOLUME = "${env.REPO_NAME}_${env.BRANCH_NAME}_gems"
  
  docker.withRegistry(config.DOCKER_REGISTRY_URL, "ecr:${env.AWS_DEFAULT_REGION}:${config.DOCKER_REGISTRY_CREDS_ID}") {

    config.container = "docker run -t --rm --name ${env.BUILD_TAG} -w /app -v ${env.WORKSPACE}:/app -v ${env.GEM_VOLUME}:/gems -e RAILS_ENV=${env.RAILS_ENV} ${config.DOCKER_REGISTRY}:${env.RUBY_VERSION}"

    puppetInstallDepsDocker(config)

    puppetTestsDocker(config)

    puppetDeployDocker(config)

    sh "${config.container} chown -R 1003:1004 /app"
  } // withRegistry
} // top level function
