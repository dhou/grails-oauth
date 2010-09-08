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
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;

import org.apache.http.HttpResponse
import org.apache.http.HttpVersion
import org.apache.http.NameValuePair
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair
import org.apache.http.params.HttpProtocolParams
import org.apache.http.params.HttpParams
import org.apache.http.params.BasicHttpParams
import org.apache.http.protocol.HTTP
import org.apache.http.util.EntityUtils

/**
 * OAuth Service to provide OAuth functionality to a
 * Grails application.
 * 
 * @author Damien Hou
 * @author Anthony Campbell (anthonycampbell.co.uk)
 * @author Pete Doyle
 */
class OauthService implements InitializingBean {
    // Transactional service
    boolean transactional = false

    // Service properties
    def providers = [:]
    def consumers = [:]
    private HttpClient httpClient
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
     * Parses OAuth settings in Config.groovy and prepares configuration cache for 
     * providers and consumers
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

            // Initialise provider config map
            providers[key] = ['requestTokenUrl': requestTokenUrl, 'accessTokenUrl': value?.accessTokenUrl,
                'authUrl': value?.authUrl]
	        
	        if (value?.consumer) {
	        	/*
                 * Default single consumer if single consumer defined, will not go on to parse
                 * multiple consumers.
                 */
	        	log?.debug "- Single consumer:"
	        	log?.debug "--- Key: ${value?.consumer?.key}"
	        	log?.debug "--- Secret: ${value?.consumer?.secret}"

                consumers[key] = ['key': value.consumer.key, 'secret': value.consumer.secret]

	        } else if (value?.consumers) {
	        	// Multiple consumers from same provider
	        	log?.debug "- Multiple consumers:"

	        	final def allConsumers = value?.consumers
	        	allConsumers.each { name, token ->
	        		log?.debug "--- Consumer: ${name}"
                    log?.debug "----- Key: ${token?.key}"
                    log?.debug "----- Secret: ${token?.secret}"

                    consumers[name] = ['key': token?.key, 'secret': token?.secret]
	        	}
	        } else {
	        	log?.error "Error initializaing OauthService: No consumers defined!"
	        }   
        }

        // Release old connections on service resets
        if (httpClient) {
            httpClient.getConnectionManager().shutdown()
        }

        /*
         * Prepare HTTP protocol parameters.
         * Note: Twitter requires "expect continue" to be disabled.
         */
        final HttpParams clientParams = new BasicHttpParams()
        HttpProtocolParams.setVersion(clientParams, HttpVersion.HTTP_1_1)
        HttpProtocolParams.setContentCharset(clientParams, HTTP.UTF_8)
        HttpProtocolParams.setUseExpectContinue(clientParams, false)

        // Scheme registry
        final SchemeRegistry schemeRegistry = new SchemeRegistry()
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80))
        schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443))

        // Prepare connection managed and initialise new client
        final ClientConnectionManager manager = new ThreadSafeClientConnManager(clientParams, schemeRegistry)
        httpClient = new DefaultHttpClient(manager, clientParams)

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
            final OAuthConsumer consumer = getConsumer(consumerName)
            final OAuthProvider provider = getProvider(consumerName)

            // Retrieve request token
            final def authorisationURL = provider?.retrieveRequestToken(consumer, callback)
            final def isOAuth10a = provider.isOAuth10a()

            log.debug "Request token: ${consumer?.getToken()}"
            log.debug "Token secret: ${consumer?.getTokenSecret()}"
            log.debug "Authorisation URL: ${authorisationURL}\n"
            log.debug "Is OAuth 1.0a: ${isOAuth10a}"

            [key: consumer?.getToken(), secret: consumer?.getTokenSecret(), authUrl: authorisationURL,
                isOAuth10a: isOAuth10a]

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
            final OAuthConsumer consumer = getConsumer(consumerName)
            final OAuthProvider provider = getProvider(consumerName)
            
            // Set the request token
            consumer.setTokenWithSecret(requestToken.key, requestToken.secret)
            
            /*
             * Set to OAuth 1.0a if necessary (to make signpost add 'oath_verifier'
             * from callback to request)
             */
            if (requestToken.isOAuth10a) {
                provider.setOAuth10a(true)
            }
            
            // Retrieve access token
            provider.retrieveAccessToken(consumer, requestToken.verifier)

            final def accessToken = consumer?.getToken()
            final def tokenSecret = consumer?.getTokenSecret()

            log.debug "Access token: $accessToken"
            log.debug "Token secret: $tokenSecret\n"

            if (!accessToken || !tokenSecret) {
                final def errorMessage = "Unable to fetch access token, access token is missing! (" +
                    "consumerName=$consumerName, requestToken=$requestToken, " +
                    "accessToken=$accessToken, tokenSecret=$tokenSecret)"

                log.error(errorMessage, ex)
                throw new OauthServiceException(errorMessage, ex)
            }

            [key: accessToken, secret: tokenSecret]

        } catch (Exception ex) {
            final def errorMessage = "Unable to fetch access token! (consumerName=$consumerName, " +
                "requestToken=$requestToken)"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }
    
    /**
     * Helper function with default parameters to access an OAuth protected resource.
     * 
     * @param url the url to the protected resource.
     * @param consumer the consumer name.
     * @param token the access token.
     * @param method (OPTIONAL) the HTTP method to use.  Defaults to 'GET'.  One of 'GET', 
     *      'PUT', 'POST', 'DELETE', 'HEAD', or 'OPTIONS'.
     * @param params (OPTIONAL) a map of parameters to add to the request.
     * @param headers (OPTIONAL) a map of headers to add to the request.
     * @param body (OPTIONAL) the request body.
     * @param accept (OPTIONAL) the content type you're willing to accept (sets the Accept header).
     * @param contentType (OPTIONAL) the content type you're sending (sets the Content-Type header).
     *
     * @return The response body as a {@code String}.
     *
     * @throws OauthServiceException
     *      If {@code method} is neither unset (defaults to 'GET') nor one of 'GET', 'PUT', 'POST',
     *      'DELETE', 'HEAD', or 'OPTIONS'.
     * @throws OauthServiceException
     *      If {@code args.token} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code args.consumer} does not represent an existing consumer.
     */
    def accessResource(final def url, final def consumer, final def token,
        final def method = 'GET', final def params = null, final def headers = [:],
        final def body = null, final def accept = null, final def contentType = null) {
        
        accessResource(url: url, consumer: consumer, token: token, method: method,
            params: params, headers: headers, body: body, accept: accept, contentType: contentType)
    }

    /**
     * Helper function with named parameters to access an OAuth protected resource.
     *
     * @param args 
     *        A map of arguments to use (i.e. by using Groovy named parameters).<br/>
     *        Required arguments: url, consumer, token<br/>
     *        Valid arguments: url, consumer, token, method, params, headers, body, accept,
     *        and contentType.<br/>
     *        
     * @return the response body as a {@code String}.
     * 
     * @throws OauthServiceException
     *      If {@code method} is neither unset (defaults to 'GET') nor one of 'GET', 'PUT', 'POST',
     *      'DELETE', 'HEAD', or 'OPTIONS'.
     * @throws OauthServiceException
     *      If {@code args.token} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code args.consumer} does not represent an existing consumer.
     */
    def accessResource(final def Map args) {
        final HttpResponse response = doRequest(args)
        String body = ""

        try {
            log.debug "Reading response body"

            body = EntityUtils.toString(response.getEntity())

            log.debug "Response body read successfully"

        } catch (Exception ex) {
            final def errorMessage = "Unable to read response from protected resource request! " +
                "(args=$args)"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }

        //  Provide response
        return body
    }

    /**
     * Performs a request and returns an {@link HttpResponse} object that can be
     * used to obtain any desired response info.
     * 
     * @param url the url.
     * @param consumer the consumer name.
     * @param token the access token.
     * @param method (OPTIONAL) the HTTP method to use.  Defaults to 'GET'.  One of 'GET', 
     *      'PUT', 'POST', 'DELETE', 'HEAD', or 'OPTIONS'.
     * @param params (OPTIONAL) a map of parameters to add to the request.
     * @param headers (OPTIONAL) a map of headers to add to the request.
     * @param body (OPTIONAL) the request body.
     * @param accept (OPTIONAL) the content type you're willing to accept (sets the Accept header).
     * @param contentType (OPTIONAL) the content type you're sending (sets the Content-Type header).
     *
     * @return the resulting {@link HttpResponse}.
     *
     * @throws OauthServiceException
     *      If {@code method} is neither unset (defaults to 'GET') nor one of 'GET', 'PUT', 'POST',
     *      'DELETE', 'HEAD', or 'OPTIONS'.
     * @throws OauthServiceException
     *      If {@code args.token} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code args.consumer} does not represent an existing consumer.
     */
    def doRequest(final def url, final String consumer, final def token,
        final def method = 'GET', final def params = [:], final def headers = [:],
        final def body = null, final def accept = null, final def contentType = null) {

        doRequest(url: url, consumer: consumer, token: token, method: method,
            params: params, headers: headers, body: body, accept: accept, contentType: contentType)
    }

    /**
     * <p>Helper function which allows for use of named parameters to call 
     * {@link doRequest(url, consumer, token, method, params, headers, body, contentType)}.</p>
     * 
     * <p>Performs a request and returns an {@link HttpResponse} object that can be 
     * used to obtain any desired response info.</p>
     * 
     * @param args
     *      A map of arguments to use (i.e. by using Groovy named parameters).<br/>
     *      Required arguments: url, consumer, token<br/>
     *      Valid arguments: url, consumer, token, method, params, headers, body, accept, and
     *      contentType.<br/>
     *
     * @return the resulting {@link HttpResponse}.
     *
     * @throws OauthServiceException
     *      If {@code method} is neither unset (defaults to 'GET') nor one of 'GET', 'PUT', 'POST',
     *      'DELETE', 'HEAD', or 'OPTIONS'.
     * @throws OauthServiceException
     *      If {@code args.token} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code args.consumer} does not represent an existing consumer.
     */
    def doRequest(final Map args) {
        // Declare request arguments
        final String consumer = args.consumer
        def url = args.url
        def token = args.token
        def method = args.method ?: 'GET'
        def params = args.params ?: [:]
        def headers = args.headers ?: [:]
        def body = args.body
        def accept = args.accept
        def contentType = args.contentType

        log.debug "Attempting to access protected resource"

        if (!token || !token?.key || !token?.secret) {
            final def errorMessage = "Unable to access protected resource, invalid token! (" +
                "method=$method, url=$url, params=$params, consumer=$consumer, " +
                "token=$token, token.key=${token?.key}, token.secret=${token?.secret}, "
                "headers=$headers, contentType=$contentType, " +
                "body=${body?.length() < 100 ? body : body?.substring(0, 99) + '...'})"

            log.error(errorMessage)
            throw new OauthServiceException(errorMessage)
        }

        if (url instanceof URL) {
            url = url?.toURI()
        }

        // Declare request method
        HttpUriRequest requestMethod

        // Set request method
        switch(method?.toUpperCase()) {
            case "GET":
                requestMethod = new HttpGet(url)
                break;
            case "PUT":
                requestMethod = new HttpPut(url)
                break;
            case "POST":
                requestMethod = new HttpPost(url)
                break;
            case "DELETE":
                requestMethod = new HttpDelete(url)
                break;
            case "HEAD":
                requestMethod = new HttpHead(url)
                break;
            case "OPTIONS":
                requestMethod = new HttpOptions(url)
                break;
            default:
                throw new OauthServiceException("Unsupported request method! (method=$method)")
                break
        }

        // Prepare parameters
        final List<NameValuePair> parameters = new ArrayList<NameValuePair>()
        params?.each { k, v ->
            log.debug("Adding param: [$k: $v]")
            parameters.add(new BasicNameValuePair("$k", "$v"))
        }

        // Set the parameters on the request
        if (parameters.size() > 0) {
            // POST with params, no body, and no contentType should use x-www-form-urlencoded
            if (!body && (!contentType || contentType?.equalsIgnoreCase("x-www-form-urlencoded")) &&
                    method == "POST") {
                log.debug("POSTing params in an 'x-www-form-urlencoded' body")
                UrlEncodedFormEntity formEncodedEntity = new UrlEncodedFormEntity(parameters, HTTP.UTF_8)
                requestMethod.setEntity(formEncodedEntity)
                
            } else {
                final def queryString = "?" + URLEncodedUtils.format(parameters, HTTP.UTF_8)
                log.debug("Adding params as query string (queryString=$queryString)")
                requestMethod.setURI(new URI(requestMethod.getURI()?.toString() + queryString))
            }
        }

        // Set the body
        if (body) {
            log.debug("Set body content as request entity")
            requestMethod.setEntity(new StringEntity(body))
        }

        // Set the headers
        headers?.each { k, v ->
            log.debug("Adding header: [$k: $v]")
            requestMethod.addHeader("$k", "$v")
        }

        // Set the Content-Type header
        if (contentType) {
            log.debug("Set the Content-Type header to '$contentType'")
            requestMethod.setHeader(HTTP.CONTENT_TYPE, "$contentType")
        }

        // Set the Accept header
        if (accept) {
            log.debug("Set the Accept header to '$accept'")
            requestMethod.setHeader("Accept", "$accept")
        }

        // Sign, execute and return HttpResponse
        execute(requestMethod, consumer, token)
    }

    /**
     * Signs the given {@link HttpUriRequest}, executes it, and returns the 
     * {@link HttpResponse}. A utility method which allows for custom setup
     * of any {@link HttpUriRequest} request.
     * 
     * @param request
     *      The {@link HttpUriRequest} (for example, an {@link HttpGet} or {@link HttpPost}).
     * @param consumerName
     *      The name of the consumer.
     * @param accessToken
     *      The access token. An object (usually a Map) containing a 'key' and a 'secret'.
     * @return The {@link HttpResponse}
     *
     * @throws OauthServiceException
     *      If {@code accessToken} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code consumerName} does not represent an existing consumer.
     */
    def HttpResponse execute(final HttpUriRequest request, final String consumerName,
            final def accessToken) {
        // Validate token
        assertAccessToken(accessToken)
        
        log.debug("Executing ${request?.getMethod()} to ${request?.getURI()}")
        
        try {
            // Sign the request
            sign(request, consumerName, accessToken)

            // Execute it, return the HttpResponse
            httpClient.execute(request)

        } catch (Exception ex) {
            final def errorMessage = "Unable to sign execute HTTP method! (request=$request, " +
                "consumerName=$consumerName, accessToken=$accessToken)"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }

    /**
     * Signs the given {@link HttpUriRequest} without executing it.
     * This method allows you to set up any {@link HttpUriRequest} to your 
     * specifications, sign it, then later execute it using your own instance of 
     * {@link HttpClient} (i.e. using custom {@link HttpParams}).
     *
     * @param request
     *      The {@link HttpUriRequest} to sign (i.e. {@link HttpGet}, {@link HttpPost}, etc.
     * @param consumerName
     *      The name of the consumer.
     * @param accessToken
     *      The access token. An object (usually a Map) containing a 'key' and a 'secret'.
     * 
     * @throws OauthServiceException
     *      If {@code accessToken} does not contain both a 'key' and 'secret'.
     * @throws OauthServiceException
     *      If {@code consumerName} does not represent an existing consumer.
     */
    def sign(final HttpUriRequest request, final String consumerName, final def accessToken) {
        // Check access token is valid
        assertAccessToken(accessToken)

        try {
            final OAuthConsumer consumer = getConsumer(consumerName)
            consumer.setTokenWithSecret(accessToken.key, accessToken.secret)
            consumer.sign(request)

        } catch (Exception ex) {
            final def errorMessage = "Unable to sign HTTP request! (request=$request, " +
                "consumerName=$consumerName, accessToken=$accessToken)"

            log.error(errorMessage, ex)
            throw new OauthServiceException(errorMessage, ex)
        }
    }
    
    /**
     * Returns the current consumer for the provided name.
     *
     * @param consumerName the consumer name.
     * @return the consumer instance by name.
     * 
     * @throws OauthServiceException
     *      If {@code consumerName} does not represent an existing consumer.
     */
    private def getConsumer(final String consumerName) {
        // Get values
        final def consumerValues = consumers[consumerName]

        // Validate
        if (!consumerValues) {
            throw new OauthServiceException("Unknown consumer: '$consumerName'")
        }

        // Initialise new consumer
        return new CommonsHttpOAuthConsumer(consumerValues.key, consumerValues.secret)
    }

    /**
     * Returns the current provider for the provided consumer.
     *
     * @param providerName the provider name.
     * @return the provider instance by name.
     *
     * @throws OauthServiceException
     *      If {@code providerName} does not represent an existing provider.
     */
    private def getProvider(final String providerName) {
        // Get values
        final def providerValues = providers[providerName]

        // Validate
        if (!providerValues) {
            throw new OauthServiceException("Unknown provider! (providerName=$providerName)")
        }

        // Initialise new provider
        return new CommonsHttpOAuthProvider(providerValues.requestTokenUrl,
            providerValues.accessTokenUrl, providerValues.authUrl, httpClient)
    }

    /**
     * Validate the provided OAuth access token.
     *
     * Simple check to see that the provided token also contains a token and secret.
     *
     * @param the token to validate.
     * @throws OauthServiceException when provided token is invalid.
     */
    private def assertAccessToken(def token) {
        if (!token || !token?.key || !token?.secret) {
            final def errorMessage = "Invalid access token! (token=$token, token.key=${token?.key}, " +
                "token.secret=${token?.secret})"

            log.error(errorMessage)
            throw new OauthServiceException(errorMessage)
        }
	}
}
