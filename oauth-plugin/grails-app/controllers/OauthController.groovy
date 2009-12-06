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

import org.apache.commons.httpclient.HttpClient
import net.oauth.*
import net.oauth.client.*
import org.codehaus.groovy.grails.commons.ConfigurationHolder as C

class OauthController {
	def oauthService
	
	/**
	 * The action to call to start the OAuth authorization sequence
	 * First, an unauthorized token is retrieved from the server
	 * Then user will be redirected to the authorization URL for manual actions
	 */
    def auth = {
		try{
			//Some services like FireEagle don't retain callback and params
			//Must store the params in session
			//def consumerName = params.consumer
			params.remove('controller')
			params.remove('action')
			session.cbparams = params
			
			def token = oauthService.fetchRequestToken(params.consumer)
			session.oauthToken = token
	    	log.debug "Stored token to session: ${session.oauthToken}"
	    	def redir = oauthService.getAuthUrl(token.key, params.consumer, params)
	    	log.debug "Going to redirect to auth url: $redir"
	    	redirect(url:redir)
	    	return
		}catch(OAuthProblemException e){
			flash.oauthError = e?.problem?e?.problem.replace('_','.'):'oauth.unknown'
			flash.oauthErrorParams = e.parameters
			redirect(controller:errorController, action:errorAction)
		}
    }
    
	/**
	 * This action will be called when the OAuth service returns from user authorization
	 * Do not (and no need) to call this explicitly
	 * Access token and secret are stored in session
	 * Get them by session.oauthToken.key and session.oauthToken.secret
	 */
    def callback = {
    	log.debug "Got callback params: $params"
    	session.cbparams.each{k,v->
    		log.debug("session param[$k]: $v")
	 	}
		def returnController = params.remove('return_controller')?:session.cbparams.remove('return_controller')
		def returnAction = params.remove('return_action')?:session.cbparams.remove('return_action')
		def errorController = params.remove('error_controller')?:session.cbparams.remove('error_controller')
		def errorAction = params.remove('error_action')?:session.cbparams.remove('error_action')
		
		params.remove('controller')
		params.remove('action')
		params.each{k,v->
			log.debug "remaining params[$k]: $v"
		}
		def redirParams = params + session.cbparams
		redirParams.each{k,v->
			log.debug "Redir params[$k]: $v"
		}
		session.cbparams = null
				
		def oauth_token = params?.oauth_token
		if(oauth_token && oauth_token != session.oauthToken.key){
			//returned token is different from the last received request token
			flash.oauthError = 'oauth.token.mismatch'
			redirect(controller:errorController, action:errorAction, params:redirParams)
			return
		}
		// OAuth 1.0a
		def oauth_verifier = params?.oauth_verifier
		
    	try{
			def accessToken = oauthService.fetchAccessToken(redirParams.consumer, [key:session.oauthToken.key, secret:session.oauthToken.secret, verifier:oauth_verifier])
			session.oauthToken = accessToken
			log.debug("Got access token: ${accessToken.key}\nGot token secret: ${accessToken.secret}\nOAuth Verifier: ${oauth_verifier}")
			log.debug("Saved token to session: [key]${session.oauthToken.key} [secret]${session.oauthToken.secret}")
			log.debug "Redirecting: [controller]$returnController, [action]$returnAction"
			redirect(controller:returnController, action:returnAction, params:redirParams)
    	}catch(OAuthProblemException e){
    		log.debug "OAuthProblemException problem: ${e?.problem}"
    		log.debug "status code: ${e.httpStatusCode}"
    		if(!e?.problem){
	    		if(e.httpStatusCode == 400){
	    			flash.oauthError = e?.problem?e?.problem.replace('_','.'):'oauth.400badrequest'
	    		} else if (e.httpStatusCode == 401){
	    			flash.oauthError = e?.problem?e?.problem.replace('_','.'):'oauth.401unauthorized'
	    		} else {
	    			flash.oauthError = e?.problem?e?.problem.replace('_','.'):'oauth.unknown'
	    		}
    		}
    		flash.oauthErrorParams = e.parameters
    		e.parameters.each{key,value->
    			log.debug "$key:$value"
    		}
    		redirect(controller:errorController, action:errorAction, params:redirParams)
    	}
    }

}