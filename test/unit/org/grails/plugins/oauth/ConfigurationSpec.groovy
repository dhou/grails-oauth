package org.grails.plugins.oauth

import grails.plugin.spock.ControllerSpec
import org.grails.plugins.oauth.OauthService
import grails.plugin.spock.UnitSpec
import spock.lang.Unroll

class ConfigurationSpec extends UnitSpec {

    @Unroll("Socket timeout is correctly read when socket timeout is #socketTimeout and connection timeout is #connectionTimeout")
    def "Socket timeout is read correctly"() {

        given:
            mockConfig """
                httpClient {
                    timeout {
                        socket = ${socketTimeout}
                        connection = ${connectionTimeout}
                    }
                }
            """

        when:
            OauthService oauthService = new OauthService()

        then:
            oauthService.connectionTimeout == connectionTimeout
            oauthService.socketTimeout == socketTimeout

        where:
            connectionTimeout | socketTimeout
            5000              | 5000
            60000             | 60000

    }

    def "Socket timeout is defaulted when no configuration is available"() {

        given:
            mockConfig ''

        when:
            OauthService oauthService = new OauthService()

        then:
            oauthService.connectionTimeout == 60000
            oauthService.socketTimeout == 60000


    }

}
