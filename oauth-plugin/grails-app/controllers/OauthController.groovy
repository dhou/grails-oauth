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
			def consumerName = params.consumer
			def token = oauthService.fetchRequestToken(consumerName)
			session.tokenKey = token.key
	    	session.tokenSecret = token.secret
	    	def redir = oauthService.getAuthUrl(token.key, consumerName, params)
	    	log.debug "Going to redirect to auth url: $redir"
	    	redirect(url:redir)
	    	return
		}catch(OAuthProblemException e){
			flash.oauthError = e.problem?:'oauth.unknown'
			flash.oauthErrorParams = e.parameters
			redirect(controller:errorController, action:errorAction)
		}
    }
    
	/**
	 * This action will be called when the OAuth service returns
	 * Do not (and no need) to call this explicitly
	 */
    def callback = {
    	log.debug "Got params: $params"
    	
		def returnController = params.remove('return_controller')
		def returnAction = params.remove('return_action')
		def errorController = params.remove('error_controller')
		def errorAction = params.remove('error_action')
		params.remove('controller')
		params.remove('action')
    	try{
			def accessToken = oauthService.fetchAccessToken(params.consumer, [key:session.tokenKey, secret:session.tokenSecret])
			session.tokenKey = accessToken.key
			session.tokenSecret = accessToken.secret
			log.debug("Got access token: ${accessToken.key}\nGot token secret: ${accessToken.secret}")
			log.debug("Saved token to session: [key]${session.tokenKey} [secret]${session.tokenSecret}")
			log.debug "Redirecting: [controller]$returnController, [action]$returnAction"
			redirect(controller:returnController, action:returnAction, params:params)
    	}catch(OAuthProblemException e){
    		log.debug "OAuthProblemException problem: ${e?.problem}"
    		log.debug "status code: ${e.httpStatusCode}"
    		if(e.httpStatusCode == 400){
    			flash.oauthError = e.problem?:'oauth.badrequest'
    		} else if (e.httpStatusCode == 401){
    			flash.oauthError = e.problem?:'oauth.unauthorized'
    		} else {
    			flash.oauthError = e.problem?:'oauth.unknown'
    		}
    		flash.oauthErrorParams = e.parameters
    		e.parameters.each{key,value->
    			log.debug "$key:$value"
    		}
    		redirect(controller:errorController, action:errorAction, params:params)
    	}
    }

}