podTemplate(
        cloud: 'insly_cluster',
        label: 'insly_test_podv1',
        nodeSelector: 'cloud.google.com/gke-preemptible: true',
        slaveConnectTimeout: 500,
        containers: [
                containerTemplate(name: 'workspace', image: 'kuzaxak/workspace', ttyEnabled: true),
                containerTemplate(name: 'php-fpm', image: 'kuzaxak/php-fpm', ttyEnabled: true),
                containerTemplate(name: 'selenium', image: 'selenium/standalone-chrome:3.4.0-einsteinium', ttyEnabled: true),
                containerTemplate(name: 'fop', image: 'kuzaxak/fop'),
                containerTemplate(name: 'mailcatcher', image: 'kuzaxak/mailcatcher'),
                containerTemplate(name: 'phpqa', image: 'kuzaxak/phpqa:v1.16', ttyEnabled: true, command: 'cat'),
                containerTemplate(name: 'nginx', image: 'kuzaxak/nginx:ci-0.8'),
                containerTemplate(name: 'dnsmasq', image: 'kuzaxak/dnsmasq'),
        ],
        volumes: [
                secretVolume(secretName: 'jenkins-ssh', mountPath: '/home/jenkins/.ssh'),
                secretVolume(secretName: 'mysql.root', mountPath: '/home/jenkins/mysql'),
                emptyDirVolume(mountPath: '/var/www', memory: false),
        ],
        annotations: [
                podAnnotation(key: "pod.beta.kubernetes.io/init-containers", value: '''[
                        {
                            "name": "init-php-fpm",
                            "image": "busybox",
                            "command": ["sh", "-c", "until nslookup php-fpm; do echo 'waiting for myservice'; sleep 2; done;"]
                        }]'''),
        ]
) {
    node('insly_test_podv1') {
        timestamps {
            stage("checkout") {
                git branch: "origin/${env.gitlabSourceBranch}",
                        poll: false,
                        url: "${env.gitlabSourceRepoSshUrl}",
                        credentialsId: 'test-j-deploy-key'
            }

            try {
                gitlabBuilds(builds: ["prepare", "analysis", "behat"]) {
                    stage("prepare") {
                        gitlabCommitStatus("prepare") {
                            container('workspace') {
                                def mysql_root = readFile "/home/jenkins/mysql/root_password"

                                wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: mysql_root, var: '${mysql_root}']]]) {
                                    sh """
                                ln -sfn ./ /var/www/insly3
                                echo \"${BUILD_NUMBER}\"
                                echo \"${env.gitlabSourceBranch}\"
                                mysql -h mysql -uroot -p${mysql_root} -e \"SELECT @@VERSION AS 'SQL Server Version';\"
                                composer install -q
                                """
                                }
                            }
                        }
                    }

                    parallel (
                        "analysis" : {
                            stage("analysis") {
                                gitlabCommitStatus("analysis") {
                                    container('phpqa') {
                                        sh '''
                                    ls -la
                                    ls -la /var/www
                                    ls -la /var/www/insly3

                                    /composer/vendor/edgedesign/phpqa/phpqa tools
                                    /composer/vendor/edgedesign/phpqa/phpqa --report offline --analyzedDirs live --ignoredDirs contrib,vendor,build,migrations,test --ignoredFiles _ide_helper.php --tools phpcpd,phploc,pdepend,phpmd,phpmetrics,phpcs,security-checker,phpstan,parallel-lint
                                    '''

                                        archive 'build/*'
                                        publishHTML(target: [
                                                allowMissing         : false,
                                                alwaysLinkToLastBuild: false,
                                                keepAll              : false,
                                                reportDir            : 'build',
                                                reportFiles          : 'phpqa.html',
                                                reportName           : 'PHP QA Report',
                                                reportTitles         : ''
                                        ])
                                    }
                                }
                            }
                        },
                        "behat" : {
                            stage("behat") {
                                gitlabCommitStatus("behat") {
                                    container('workspace') {
                                        sh """
                                            ls -la
                                            ls -la /var/www
                                            ls -la /var/www/insly3
                                        """
                                    }
                                }
                            }
                        }
                    )
                }
            }
            catch (err) {
                echo 'Build failed'
                throw err
            }
        }
    }
}