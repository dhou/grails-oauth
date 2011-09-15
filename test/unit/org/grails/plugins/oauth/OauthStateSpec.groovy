package org.grails.plugins.oauth

import grails.plugin.spock.UnitSpec
import spock.lang.Unroll

@Mixin(uk.co.desirableobjects.credit.control.OAuthState)
class OauthStateSpec extends UnitSpec {

    private static final Map ACTIVE_SESSION = ['oauthToken': [k: 0, v: 0]]
    private session

    def "Check session state where session is #sessionActive"() {

        given:
            session = sessionContent

        expect:
            oauthSession == sessionActive

        where:
            sessionContent              | sessionActive
            [:]                         | false
            ACTIVE_SESSION              | true

    }

    def "Retrieve credentials where session is #sessionContent"() {

        given:
            session = sessionContent

        expect:
            oauthCredentials == expectedCredentials

        where:
            sessionContent              | expectedCredentials
            [:]                         | null
            ACTIVE_SESSION              | ACTIVE_SESSION[OAUTH_SESSION_KEY]

    }

}