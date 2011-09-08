package org.grails.plugins.oauth

import grails.plugin.spock.TagLibSpec
import spock.lang.Unroll

@Mixin(OauthState)
class OauthTagLibSpec extends TagLibSpec {

     @Unroll("Tag renders #renderedContent if session is (#sessionContent)")
     def "Tag renders or does not render depending on session"() {

        given:
            if (sessionKey) {
                mockSession[sessionKey] = sessionContent
            }

            tagLib.connected([:]) {
                '<h1>connected</h1>'
            }
            tagLib.disconnected([:]) {
                '<h2>no connection</h2>'
            }

        expect:
            tagLib.out.toString().contains(renderedContent)

        where:
            sessionKey          | sessionContent          | renderedContent
            OAUTH_SESSION_KEY   | [key:'x', secret:'y']   | '<h1>connected</h1>'
            OAUTH_SESSION_KEY   | null                    | '<h2>no connection</h2>'
            null                | [:]                     | '<h2>no connection</h2>'


    }

    @Unroll('Render Xero connect link with attributes #attributes contains #matchedText')
    def "Xero connect link renders with all required attributes"() {

        given:
            tagLib.metaClass.oauthLink = { attribs, body ->
                assert attribs?.'class' == attributes?.'class'
                assert attribs?.consumer == 'creditApp'
                assert attribs?.returnTo.controller == 'home'
                assert attribs?.returnTo.action == 'dashboard'
                assert attribs?.error.controller == 'home'
                assert attribs?.error.action == 'connection_failed'
            }
            tagLib.xeroLink(attributes) {
                'connect'
            }

        where:
            attributes << [null, [:], ['class':'big-button-text']]

    }

}