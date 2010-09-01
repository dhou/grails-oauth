package org.grails.plugins.oauth

/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.codehaus.groovy.grails.commons.ConfigurationHolder as C

class OauthController {
	def oauthService
	
	/**
	 * The action to call to start the OAuth authorization sequence.
	 * First, an unauthorized token is retrieved from the server.
	 * Then user will be redirected to the authorization URL for manual actions.
	 */
    def auth = {
        log.debug "Prepare for open authorisation..."

        // Get consumer name from auth request
        final def consumerName = params?.consumer ?: session?.cbparams?.consumer
        final def errorController = params?.error_controller ?: session?.cbparams?.error_controller
        final def errorAction = params?.error_action ?: session?.cbparams?.error_action
        final def errorId = params?.error_id ?: session?.cbparams?.error_id

        try {
            /*
             * Some services like FireEagle don't retain callback and params.
             * Must store the params in session.
             */
            params?.remove('controller')
            params?.remove('action')
            session.cbparams = params

            def token = oauthService?.fetchRequestToken(consumerName)
            session.oauthToken = token

            log.debug "Stored token to session: ${session.oauthToken}"
            def redir = token.authUrl

            log.debug "Going to redirect to auth url: $redir"
            redirect(url: redir)
            return

        } catch (OauthServiceException ose) {
            log.error "Unable to initialise authorisation: $ose"

            flash.oauthError = message(code: "oauth.requesttoken.missing",
                default: "Failed to retrieve the request token from the OAuth service provider. " +
                    "Please try to the authorization action again.")
            redirect(controller: errorController, action: errorAction, id: errorId)
        }
    }

    /**
     * This action will be called when the OAuth service returns from user authorization.
     * Do not (and no need) to call this explicitly.
     * Access token and secret are stored in session.
     * Get them by session.oauthToken.key and session.oauthToken.secret.
     */
    def callback = {
        log.debug "Callback received..."

        log.debug "Got callback params: $params"

        // List session parameters
        log.debug "Session parameters:"
        session.cbparams.each{ k, v ->
            log.debug "- $k: $v"
        }

        // Get required redirect controllers and actions
        final def returnController = params?.remove('return_controller') ?: session?.cbparams?.remove('return_controller')
        final def returnAction = params?.remove('return_action') ?: session?.cbparams?.remove('return_action')
        final def returnId = params?.remove('return_id') ?: session?.cbparams?.remove('return_id')
        final def errorController = params?.remove('error_controller') ?: session?.cbparams?.remove('error_controller')
        final def errorAction = params?.remove('error_action') ?: session?.cbparams?.remove('error_action')
        final def errorId = params?.remove('error_id') ?: session?.cbparams?.remove('error_id')

        // Clean up
        params?.remove('controller')
        params?.remove('action')

        //  List remaining parameters
        log.debug "Remaining parameters:"
        params.each { k, v ->
            log.debug "- $k: $v"
        }

        // Update request parameters with session
        final def redirParams = params + session.cbparams

        // List re-direct parameters
        log.debug "Re-direct parameters:"
        redirParams.each{ k, v ->
            log.debug "- $k: $v"
        }

        // Kill session parameters
        session.cbparams = null

        final def oauth_token = params?.oauth_token
        if (oauth_token && oauth_token != session.oauthToken.key) {
            // Returned token is different from the last received request token
            flash.oauthError = message(code: "oauth.token.mismatch",
                default: "There has been an error in the OAuth request. Please try again.")
            redirect(controller: errorController, action: errorAction, id: errorId,
                params: redirParams)
            return
        }

        // OAuth 1.0a
        def oauth_verifier = params?.oauth_verifier

        try {
            def accessToken = oauthService?.fetchAccessToken(redirParams?.consumer,
                [key: session?.oauthToken?.key, secret: session?.oauthToken?.secret,
                    verifier: oauth_verifier, isOAuth10a: session?.oauthToken?.isOAuth10a])
            session.oauthToken = accessToken

            log.debug("Got access token: ${accessToken?.key}")
            log.debug("Got token secret: ${accessToken?.secret}")
            log.debug("OAuth Verifier: ${oauth_verifier}")
            log.debug("Saved token to session: [key]${session?.oauthToken?.key} " +
                "[secret]${session?.oauthToken?.secret} " +
                "[verifier]${session?.oauthToken?.verifier} " +
                "[isOAuth10a]${session?.oauthToken?.isOAuth10a}")
            log.debug "Redirecting: [controller]$returnController, [action]$returnAction\n"

            redirect(controller: returnController, action: returnAction, id: returnId,
                params: redirParams)
            
    	} catch (OauthServiceException ose) {
    		log.error "Unable to fetch access token: $ose"
            
            flash.oauthError = message(code: "oauth.400badrequest",
                default: "There has been an error in the OAuth request. Please try again.")
    		redirect(controller: errorController, action: errorAction, params: redirParams)
    	}
    }
}