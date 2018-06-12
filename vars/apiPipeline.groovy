def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "started pipeline for: ${env.BRANCH_NAME}"
    def cronSchedule = ""
    if (env.BRANCH_NAME == 'develop') {
        cronSchedule = pipelineParams.schedule
    }
      
    def build_params = pipelineParams.get('build_params', ['mvn clean install'])
    def integration_params = pipelineParams.get('integration_params', ['mvn clean verify -Pit'])
    def quality_params = pipelineParams.get('quality_params', ['mvn cobertura:cobertura'])
    def security_params = pipelineParams.get('security_params', ['mvn clean install'])
    def publish_params = pipelineParams.get('publish_params', ['mvn clean deploy'])
    def deploy_job = pipelineParams.get('deploy_job', ['sc-release'])
    def functional_params = pipelineParams.get('functional_params', ['mvn clean verify -Pft'])
    def release_params = pipelineParams.get('release_params', ['mvn release:prepare release:perform'])

    pipeline {
        triggers {
            cron(cronSchedule)
        }
        post {
            failure {
                updateGitlabCommitStatus name: 'build', state: 'failed'
            }

            success {
                updateGitlabCommitStatus name: 'build', state: 'success'
            }
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: "7"))
            disableConcurrentBuilds()
            skipDefaultCheckout()
            timeout(time: 60, unit: 'MINUTES')
        }

        parameters {
            booleanParam(defaultValue: true, description: '', name: 'build')
            booleanParam(defaultValue: true, description: "", name: "integration")
            booleanParam(defaultValue: true, description: "", name: "quality")
            booleanParam(defaultValue: true, description: "", name: "security")
            booleanParam(defaultValue: false, description: "", name: "publish")
            booleanParam(defaultValue: false, description: "", name: "deploy")
            booleanParam(defaultValue: true, description: "", name: "functional")
            booleanParam(defaultValue: false, description: "", name: "release")
            choice(choices: "quby-test\nelectrabel-test\neneco-acc\nviesgo-acc\nqutility-acc", description: "", name: "env")
            string(defaultValue: "", description: "leave empty to use pom version", name: "version")
        }

        stages {
            stage('checkout scm') {
                steps {
                    deleteDir()
                    checkout scm
                }
            }

            stage('build') {
                when {
                    expression {
                        return params.build
                    }
                }
                steps {
                    echo "Building.."
                    script {
                        build_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage ('integration test') {
                when {
                    expression {
                        return params.integration
                    }
                }

                steps {
                    echo "Running Integration Tests..."
                    script {
                        integration_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage("quality") {
                when {
                    expression {
                        return params.quality
                    }
                }
                steps {
                    echo "Checking quality...."
                    script {
                        quality_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage("security") {
                when {
                    expression {
                        return params.security
                    }
                }
                steps {
                    echo "Checking security...."
                    script {
                        security_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage('publish') {
                when {
                    expression {
                        return params.publish
                    }
                }

                steps {
                    echo 'publishing...'
                    script {
                        publish_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage('deploy') {
                when {
                    expression {
                        return params.deploy
                    }
                }

                steps {
                    echo 'deploying...'
                }
            }

            stage('functional test') {
                when {
                    expression {
                        return params.functional
                    }
                }

                steps {
                    echo 'running functional tests...'
                    script {
                        functional_params.each {
                            sh "${it}"
                        }
                    }
                }
            }

            stage('release') {
                when {
                    expression {
                        return params.release
                    }
                }
                
                steps {
                    echo 'release...'
                    script {
                        release_params.each {
                            sh "${it}"
                        }
                    }
                }
            }
        }
    }
}
