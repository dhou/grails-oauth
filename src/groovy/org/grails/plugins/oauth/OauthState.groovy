package org.grails.plugins.oauth

class OauthState {

    static final String OAUTH_SESSION_KEY = 'oauthToken'

    Map getOauthCredentials() {
        return session ? session[OAUTH_SESSION_KEY] : null
    }

    boolean isOauthSession() {
        return oauthCredentials ? true : false
    }

}
