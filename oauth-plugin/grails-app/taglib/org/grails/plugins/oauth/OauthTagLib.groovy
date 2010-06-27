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

class OauthTagLib {
	/**
	 * Renders an OAuth user authorization request link to the service provider
	 * 
	 * Attributes:
	 * consumer - the oauth consumer name
	 * returnTo (optional) - a map containing the controller and action to redirect to after authorization complete 
	 * error (optional) - a map containing the controller and action to redirect to when authorization error happens
     *
	 * Examples:
	 * <g:oauthLink consumer='myConsumer' returnTo="[controller:'myController',action:'oauthComplete']">Authorize</g:oauthLink>
	 * <g:oauthLink consumer='myConsumer' returnTo="[controller:'myController']">Authorize</g:oauthLink>
	 * <g:oauthLink consumer='myConsumer'>Authorize</g:oauthLink>
	 * <g:oauthLink consumer='myConsumer' returnTo="[controller:'myController',action:'oauthComplete']" error="[controller:'errorController',action:'errorAction']">Authorize</g:oauthLink>
	 */
	def oauthLink = { attrs, body ->
	    attrs.url = g.oauthUrl(attrs)
	    out << g.link(attrs, body)
	}
	 
	/**
	 * Construct the URL string for OAuth authorization action.
	 * To be used in other means than a simple <a> link.
	 */
	def oauthUrl = { attrs ->
		attrs.url = [controller:'oauth', action:'auth', params:[:]]

	    final def returnTo = attrs.remove('returnTo')
	    final def controller = returnTo?.controller ?: controllerName
	    final def action = returnTo?.action ?: actionName
	    final def id = returnTo?.id ?: ""
	    attrs.url.params["return_controller"] = controller
	    attrs.url.params["return_action"] = action
	    attrs.url.params["return_id"] = id

	    final def error = attrs.remove('error')
	    final def errorController = error?.controller ?: controller
        final def errorAction = error?.action ?: action
        final def errorId = error?.id ?: ""
	    attrs.url.params['error_controller'] = errorController
	    attrs.url.params['error_action'] = errorAction
	    attrs.url.params['error_id'] = errorId

	    def consumer = attrs.remove('consumer')
	    attrs.url.params['consumer'] = consumer

	    out << g.createLink(attrs)
	}
    
	/**
	 * Renders the body of the tag when there's an OAuth error.
	 * 
	 * Example:
	 * 
	 * <g:hasOauthError>
	 *     <div class="errors">
	 *         <g:renderOauthError />
	 *     </div>
	 * </g:hasLoginError>
	 */
	def hasOauthError = { attrs, body ->
	    if (flash.oauthError) {
	        out << body()
	    }
	}
	 
	/**
	 * Renders the OAuth error.
	 * 
	 * Example:
	 * 
	 * <g:renderOauthError />
	 */
	def renderOauthError = { attrs ->
	    if (flash.oauthError) {
	        out << message(code: flash.oauthError)
	    }
	}
}
