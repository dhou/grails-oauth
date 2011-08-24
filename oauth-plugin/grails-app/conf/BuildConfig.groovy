
grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'

    repositories {
        grailsPlugins()
        grailsHome()
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        runtime 'oauth.signpost:signpost-core:1.2.1.1'
        runtime 'oauth.signpost:signpost-commonshttp4:1.2.1.1'
    }

    plugins {

    }
}

