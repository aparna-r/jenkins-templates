def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    pipelineParams.put('application', '')
    pipelineParams.put('email', '')
    pipelineParams.put('agent', 'build-java8')
    pipelineParams.put('schedule', '0 0 * * *')
    pipelineParams.put('build_params', '')
    pipelineParams.put('integration_params', '')
    pipelineParams.put('quality_params', '')
    pipelineParams.put('security_params', '')
    pipelineParams.put('publish_params', '')
    pipelineParams.put('deploy_params', '')
    pipelineParams.put('functional_params', '')

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    echo "started pipeline for: ${env.BRANCH_NAME}"
    def cronSchedule = ""
    if (env.BRANCH_NAME == 'develop') {
        cronSchedule = pipelineParams.schedule
    }

    pipeline {
        agent {
            label pipelineParams.agent
        }

        triggers {
            cron(cronSchedule)
            gitlab(triggerOnPush: true, triggerOnMergeRequest: true, ciSkip: true, branchFilterType: 'All', includeBranchesSpec: 'develop')
        }

        environment {
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
                    sh "mvn -U clean install"
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
                    sh "mvn clean verify -P it ${pipelineParams.integration_params}"
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
                    sh "mvn clean verify -P quality ${pipelineParams.quality_params}"
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
                    sh "mvn clean verify -P security ${pipelineParams.security_params}"
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
                    sh "mvn clean deploy ${pipelineParams.publish_params}"
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
                    deploy app: "${pipelineParams.application}", version: "${params.version}", environment: "${params.env}"
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
                    sh "mvn clean verify -P ft -Dtarget.env=${params.env} ${pipelineParams.functional_params}"
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
                }
            }
        }
    }
}
