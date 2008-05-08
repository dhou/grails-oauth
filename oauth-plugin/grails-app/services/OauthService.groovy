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

import org.springframework.beans.factory.InitializingBean
import org.apache.commons.httpclient.HttpClient;
import net.oauth.*
import net.oauth.client.*
import org.codehaus.groovy.grails.commons.ConfigurationHolder as C

class OauthService implements InitializingBean{
	
    boolean transactional = false
    
    def providers = [:]
    def consumers = [:]
    def oauthClient = new OAuthHttpClient([getHttpClient:{
    	return new HttpClient();
    }] as HttpClientPool);
    
    /**
     * Parses OAuth settings in Config.groovy and propagates providers and consumers
     * 
     * Example OAuth settings format in Config.groovy
     * 
     * e.g. Single consumer per provider
     * 
     * oauth{
     * 		provider_name{
     * 			requestTokenUrl='http://example.com/oauth/request_token'
     *			accessTokenUrl='http://example.com/oauth/access_token'
     *			authUrl='http://example.com/oauth/authorize'
     *			consumer.key='key'
     *			consumer.secret='secret'
     *		}
     * }
     * 
     * e.g. Multiple consumers per provider
     * 
     * oauth{
     * 		provider_name{
     * 			requestTokenUrl='http://example.com/oauth/request_token'
     *			accessTokenUrl='http://example.com/oauth/access_token'
     *			authUrl='http://example.com/oauth/authorize'
     *			consumers{
     *				consumer_name{
     *					key='key'
     *					secret='secret'
     *				}
     *				consumer_name_a{
     *					key='key'
     *					secret='secret'
     *				}
     *			}
     *		}
     * }
     */
    void afterPropertiesSet() {
        println "Initializating OauthService"
        //initialize consumer list by reading config
        C.config.oauth.each{key, value->
	        OAuthServiceProvider provider = new OAuthServiceProvider(value.requestTokenUrl,
	    			value.authUrl, value.accessTokenUrl);
	        providers[key] = provider
	        if(value?.consumer){
	        	//default single consumer
	        	//if single consumer defined, will not go on to parse multiple consumers
	        	println "Single consumer"
		        OAuthConsumer consumer = new OAuthConsumer(C.config.grails.serverURL+'/oauth/callback',
		    			value.consumer.key, value.consumer.secret, provider);
		        consumers[key] = consumer
	        }else if(value?.consumers){
	        	//multiple consumers from same provider
	        	println "Multiple consumers"
	        	def allConsumers = value?.consumers
	        	allConsumers.each{name, token->
	        		println "Adding consumer[$name]: $token"
		        	OAuthConsumer consumer = new OAuthConsumer(C.config.grails.serverURL+'/oauth/callback',
			    			token.key, token.secret, provider);
			        consumers[name] = consumer
	        	}
	        }else{
	        	println "Error initializaing OauthService: No consumers defined!"
	        }
	        
        }
        providers.each{key, value->
        	println "provider[$key]: request[${value.requestTokenURL}] access[${value.accessTokenURL}] auth[${value.userAuthorizationURL}]"
        }
        consumers.each{key, value->
        	println "consumer[$key]: callback[${value.callbackURL}] key[${value.consumerKey}] secret[${value.consumerSecret}]"
        }
    }
    
    /**
     * Returns the consumer instance by name
     */
    def getConsumer(name){
    	consumers[name]
    }
    
    /**
     * Returns the OAuth service provider instance by name
     */
    def getProvider(name){
    	provicers[name]
    }
    
    /**
     * Returns an OAuthHttpClient instance
     */
    def getOauthClient(){
    	oauthClient
    }
    
    /**
     * Retrieves an unauthorized request token from the OAuth service
     * @return A map containing the token key and secret
     */
    def fetchRequestToken(consumerName){
    	def consumer = getConsumer(consumerName)
    	def accessor = new OAuthAccessor(consumer)
    	oauthClient.getRequestToken(accessor)
    	log.debug "Got request token and secret: ${accessor.requestToken}, ${accessor.tokenSecret}"
    	[key:accessor.requestToken, secret:accessor.tokenSecret]
    }
    
    /**
     * Constructs the URL for user authorization action, with required parameters appended
     * @return The URL to redirect the user to for authorization
     */
    def getAuthUrl(key, consumerName, params){
    	def consumer = getConsumer(consumerName)
    	OAuth.addParameters(consumer.serviceProvider.userAuthorizationURL,
    			'oauth_token', key,
    			'oauth_callback',
    			OAuth.addParameters(consumer.callbackURL,
    					params.entrySet()))
    }
    
    /**
     * Exchanges the authorized request token for the access token
     * @return A map containing the access token and secret
     */
    def fetchAccessToken(consumerName, requestToken){
    	log.debug "Going to exchange for access token"
    	def consumer = consumers[consumerName]
    	def accessor = new OAuthAccessor(consumer)
		accessor.requestToken = requestToken.key
		accessor.tokenSecret = requestToken.secret
		
		def accessUrl = consumer.serviceProvider.accessTokenURL
		def req = accessor.newRequestMessage("POST", accessUrl, [oauth_token:accessor.requestToken].entrySet())
    	OAuthResponseMessage response = (OAuthResponseMessage) oauthClient.invoke(req)
    	def accessToken = response.getParameter("oauth_token");
		def tokenSecret = response.getParameter("oauth_token_secret");
		[key:accessToken, secret:tokenSecret]
    }
    
    /**
     * Helper function with default parameters to access an OAuth protected resource
     * @return The response from the server
     */
    def accessResource(url, consumer, token, method='GET', params=null){
    	accessResource(url:url, consumer:consumer, token:token, method:method, params:params)
    }
    
    /**
     * Helper function with named parameters to access an OAuth protected resource
     * @return The response from the server
     */
    def accessResource(Map args){
    	//url, consumer, token, params=null, method='GET'
    	def method = args.get('method','GET')
    	def url = args.url
    	def consumer = getConsumer(args.consumer)
    	def accessor = new OAuthAccessor(consumer)
    	accessor.accessToken = args.token.key
		accessor.tokenSecret = args.token.secret
		def map = [oauth_token:accessor.accessToken]
    	if(args.params){
    		log.debug "Putting additional params: ${args.params}"
    		map += args.params
    		log.debug "Map is now: $map"
    	}
    	def req = accessor.newRequestMessage(method, url, map.entrySet())
		def client = getOauthClient()
    	OAuthResponseMessage response = (OAuthResponseMessage) client.invoke(req)
    	response.getBodyAsString()
    }

}