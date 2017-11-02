def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any
        stages {
            stage('checkout git') {
                steps {
                    echo 'checkout...'
                }
            }

            stage('build') {
                steps {
                    echo 'building...'
                }
            }

            stage ('test') {
                steps {
                    parallel (
                            "unit tests": { echo 'unit tests...' },
                            "integration tests": { echo 'integration tests...' }
                    )
                }
            }

            stage('deploy'){
                steps {
                    echo 'deploy...'
                }
            }
        }
        post {
            failure {
                echo 'failure...'
            }
        }
    }
}
