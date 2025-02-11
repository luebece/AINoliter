pipeline {
    agent any

    stages {
        stage('GIT Clone') {
            steps {
                git url: 'https://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@github.com/luebece/AINoliter.git'
                branch: 'master',
                credentialsId: 'jenkins-github'

            }
        }
    }
}
