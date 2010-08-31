package org.grails.plugins.oauth

/*
 * Copyright 2010 the original author or authors.
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

import org.springframework.beans.factory.InitializingBean
import org.codehaus.groovy.grails.commons.ConfigurationHolder as C

import oauth.signpost.OAuth
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.basic.DefaultOAuthConsumer
import oauth.signpost.basic.DefaultOAuthProvider

class OauthService implements InitializingBean {
    // Transactional service
    boolean transactional = false

    // Service properties
    def providers = [:]
    def consumers = [:]
    String callback = ""

    /**
     * Initialise config properties.
     */
    @Override
    void afterPropertiesSet() {
        log?.info "Initialising the ${this.getClass().getSimpleName()}..."
        reset()
    }

    /**
     * Parses OAuth settings in Config.groovy and propagates providers and consumers
     * 
     * Example OAuth settings format in Config.groovy
     * 
     * e.g. Single consumer per provider
     * 
     * oauth {
     * 		provider_name {
     * 			requestTokenUrl='http://example.com/oauth/request_token'
     *			accessTokenUrl='http://example.com/oauth/access_token'
     *			authUrl='http://example.com/oauth/authorize'
     *			scope = 'http://example.com/data/feed/api/'
     *			consumer.key = 'key'
     *			consumer.secret = 'secret'
     *		}
     * }
     * 
     * e.g. Multiple consumers per provider
     * 
     * oauth {
     * 		provider_name {
     * 			requestTokenUrl = 'http://example.com/oauth/request_token'
     *			accessTokenUrl = 'http://example.com/oauth/access_token'
     *			authUrl = 'http://example.com/oauth/authorize'
     *			scope = 'http://example.com/data/feed/api/'
     *			consumers {
     *				consumer_name {
     *					key = 'key'
     *					secret = 'secret'
     *				}
     *				consumer_name_a {
     *					key = 'key'
     *					secret = 'secret'
     *				}
     *			}
     *		}
     * }
     *
     * Note: The scope provider specific property and is a optional. Only providers
     * such as Google's GDATA API make use of this property.
     */
    void reset() {
        log?.info "Resetting ${this.getClass().getSimpleName()} configuration..."
        
        // Initialize consumer list by reading config
        final String serverURL = C.config.grails.serverURL.toString()
        if (!serverURL.endsWith('/')) {
        	serverURL += '/'
        }

        // Create call back link
        callback = serverURL + "oauth/callback"
        log?.debug "- Callback URL: ${callback}"
        
        C.config.oauth.each { key, value ->
            log?.debug "Provider: ${key}"
            log?.debug "- Signed: ${value?.signed}"

            def requestTokenUrl = value?.requestTokenUrl
            if (value?.scope) {
                log?.debug "- Scope: " + value?.scope

                requestTokenUrl = requestTokenUrl + "?scope=" + URLEncoder.encode(value?.scope, "utf-8")
            }

            log?.debug "- Request token URL: ${requestTokenUrl}"
            log?.debug "- Access token URL: ${value?.accessTokenUrl}"
            log?.debug "- Authorisation URL: ${value?.authUrl}\n"

            // Initialise provider
            providers[key] = new DefaultOAuthProvider(requestTokenUrl,
                value?.accessTokenUrl, value?.authUrl)
	        
	        if (value?.consumer) {
	        	/*
                 * Default single consumer if single consumer defined, will not go on to parse
                 * multiple consumers.
                 */
	        	log?.debug "- Single consumer:"
	        	log?.debug "--- Key: ${value?.consumer?.key}"
	        	log?.debug "--- Secret: ${value?.consumer?.secret}"

                consumers[key] = new DefaultOAuthConsumer(value.consumer.key,
                    value.consumer.secret)

	        } else if (value?.consumers) {
	        	// Multiple consumers from same provider
	        	log?.debug "- Multiple consumers:"

	        	final def allConsumers = value?.consumers
	        	allConsumers.each { name, token ->
	        		log?.debug "--- Consumer: ${name}"
                    log?.debug "----- Key: ${token?.key}"
                    log?.debug "----- Secret: ${token?.secret}"

                    consumers[name] = new DefaultOAuthConsumer(token?.key, token?.secret)
	        	}
	        } else {
	        	log?.error "Error initializaing OauthService: No consumers defined!"
	        }   
        }

        log?.info "${this.getClass().getSimpleName()} intialisation complete\n"
    }

    /**
     * Retrieves an unauthorized request token from the OAuth service.
     *
     * @param consumerName the consumer to fetch request token from.
     * @return A map containing the token key, secret and authorisation URL.
     */
    def fetchRequestToken(final def consumerName) {
        log.debug "Fetching request token for ${consumerName}"

        try {
            // Get consumer and provider
            final DefaultOAuthConsumer consumer = getConsumer(consumerName)
            final DefaultOAuthProvider provider = getProvider(consumerName)

            // Retrieve request token
            final def authorisationURL = provider?.retrieveRequestToken(consumer, callback)

            log.debug "Request token: ${consumer?.getToken()}"
            log.debug "Token secret: ${consumer?.getTokenSecret()}"
            log.debug "Authorisation URL: ${authorisationURL}\n"

            [key: consumer?.getToken(), secret: consumer?.getTokenSecret(), authUrl: authorisationURL]

        } catch (Exception ex) {
            final def errorMessage = "Unable to fetch request token (consumerName=$consumerName)"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }

    /**
     * Exchanges the authorized request token for the access token.
     *
     * @return A map containing the access token and secret.
     */
    def fetchAccessToken(final def consumerName, final def requestToken) {
        log.debug "Going to exchange for access token"

        try {
            final DefaultOAuthConsumer consumer = getConsumer(consumerName)
            final DefaultOAuthProvider provider = getProvider(consumerName)

            // Retrieve access token
            provider.retrieveAccessToken(consumer, requestToken.verifier)

            final def accessToken = consumer?.getToken()
            final def tokenSecret = consumer?.getTokenSecret()

            log.debug "Access token: $accessToken"
            log.debug "Token secret: $tokenSecret\n"

            if (!accessToken || !tokenSecret) {
                final def errorMessage = "Unable to fetch access token, access token is missing! " +
                    "consumerName = $consumerName, requestToken = $requestToken, " +
                    "accessToken = $accessToken, tokenSecret = $tokenSecret"

                log.error(errorMessage, ex)
                throw new OauthServiceException(errorMessage, ex)
            }

            [key: accessToken, secret: tokenSecret]

        } catch (Exception ex) {
            final def errorMessage = "Unable to fetch access token: consumerName = $consumerName, " +
                "requestToken = $requestToken"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }

    /**
     * Performs a request and returns request object can be used to obtain
     * any desired request info.
     *
     * @param args access resource arguments.
     */
    protected def getRequest(final def Map args) {
        // Declare request parameters
        def method
        def params
        URL url
        DefaultOAuthConsumer consumer

        try {
            method = args?.get('method','GET')
            params = args?.params
            url = new URL(args?.url)
            consumer = getConsumer(args?.consumer)

            if (!consumer) {
                final def errorMessage = "Unable to access to procected resource, invalid consumer: " +
                    "method = $method, params = $params, url = $url, consumer = $consumer"

                log.error(errorMessage)
                throw new OauthServiceException(errorMessage)
            }

            final def token = args?.token
            if (!token || !token?.key || !token?.secret) {
                final def errorMessage = "Unable to access to procected resource, invalid token: " +
                    "method = $method, params = $params, url = $url, consumer = $consumer, " +
                    "token = $token, token.key = ${token?.key}, token.secret = ${token?.secret}"

                log.error(errorMessage)
                throw new OauthServiceException(errorMessage)
            }

            log.debug "Open connection to $url"

            // Create an HTTP request to a protected resource
            final HttpURLConnection request = (HttpURLConnection) url.openConnection()
            
            // Set the request method (i.e. GET, POST, etc)
            request.setRequestMethod(method)

            if (params) {
                log.debug "Putting additional params: $params"

                params.each { key, value ->
                    request.addRequestProperty(key, value)
                }

                log.debug "Request properties are now: ${request?.getRequestProperties()}"
            }

            // Sign the request
            consumer.sign(request)

            log.debug "Send request..."

            // Send the request
            request.connect()

            log.debug "Return request...\n"

            // Return the request
            request

        } catch (Exception ex) {
            final def errorMessage = "Unable to access to procected resource: method = $method, " +
                "params = $params, url = $url, consumer = $consumer"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }

    /**
     * Helper function with default parameters to access an OAuth protected resource.
     *
     * @param url URL to the protected resource.
     * @param consumer the consumer.
     * @param token the access token.
     * @param method HTTP method, whether to use POST or GET.
     * @param params any request parameters.
     * @return the response from the server.
     */
    def accessResource(final def url, final def consumer, final def token,
        final def method = 'GET', final def params = null) {
        
    	accessResource(url: url, consumer: consumer, token: token, method: method, params: params)
    }
    
    /**
     * Helper function with named parameters to access an OAuth protected resource.
     *
     * @param args access resource arguments.
     * @return the response from the server.
     */
    def accessResource(final def Map args) {
        log.debug "Attempting to access protected resource"

        // Declare response variables
        BufferedReader streamReader
        StringBuilder response
        String line

        try {
            // Get input stream from request
            streamReader = new BufferedReader(
                new InputStreamReader(getRequest(args).getInputStream())
            );

            // Read response
            log.debug "Initialized request reader"
            response = new StringBuilder();
            while ((line = streamReader.readLine()) != null) {
                response.append(line + "\n")
            }

            // Return
            log.debug "Content read successfully"
            response.toString()

        } catch (Exception ex) { // Should be only IOException here
            final def errorMessage = "Unable to read data from procected resource: " +
                "args = $args"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)

        } finally {
            streamReader = null
            response = null
            line = null
        }
    }

    /**
     * Returns the current consumer for the provided name.
     *
     * @param consumerName the consumer name.
     * @return the consumer instance by name.
     */
    def getConsumer(final def consumerName) {
    	consumers[consumerName]
    }

    /**
     * Returns the current provider for the provided consumer.
     *
     * @param consumerName the consumer name.
     * @return the provider instance by name.
     */
    def getProvider(final def consumerName) {
    	providers[consumerName]
    }
}
