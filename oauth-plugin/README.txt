
Copyright 2010 Anthony Campbell (anthonycampbell.co.uk)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

--------------------------------------
Grails OAuth Plug-in
--------------------------------------

Simple plugin to provide easy interaction with OAuth service providers.
OAuth is an open standard for secure API authentication. Using OAuth,
users can securely and easily allow third party applications to access
their private data maintained by OAuth provider services through web
service API. Refer to the OAuth 1.0 spec for complete details.

If you find any issues, please submit a bug on JIRA:

    http://jira.codehaus.org/browse/GRAILSPLUGINS

Please look at the CHANGES file to see what has changed since the last 
official release.

----------------------
Upgrading from an earlier release
----------------------
Since 0.4 the getAuthUrl methods on the OauthService have been removed.
Instead the service returns the authorization URL as part of the request
acess token call. This allows less calls to be made to the service, and
for the service to become a stateless session bean.

------------------------
Install command:
------------------------
To install the plug-in from the repository enter the following command:

    grails install-plugin oauth

------------------------
Configuration:
------------------------
Before using the OAuth plug-in, the required OAuth parameters should be
defined in the grails-app/conf/Config.groovy file of your application.

e.g. One consumer identity for each OAuth service provider:

    oauth {
        provider_name {
            requestTokenUrl = 'http://example.com/oauth/request_token'
            accessTokenUrl = 'http://example.com/oauth/access_token'
            authUrl = 'http://example.com/oauth/authorize'
            consumer.key = 'key'
            consumer.secret = 'secret'

            // Optional - currently required by the Google GData APIs
            scope = 'http://example.com/oauth/feed/api/'
        }
    }

In the above case, the OAuth provider name and the corresponding consumer
name will both be "provider_name".

You can also specify multiple consumer identities for each OAuth service
provider. e.g.:

    oauth {
        provider_name {
	    requestTokenUrl = 'http://example.com/oauth/request_token'
            accessTokenUrl = 'http://example.com/oauth/access_token'
            authUrl = 'http://example.com/oauth/authorize'
            consumers {
                consumer_a {
                    key = 'key'
                    secret = 'secret'
                }
                consumer_b {
                    key = 'key'
                    secret = 'secret'
                }
            }

            // Optional - currently required by the Google GData APIs
            scope = 'http://example.com/oauth/feed/api/'
        }
    }

In the above case, the OAuth provider name will be "provider_name". The
consumer names will be "consumer_a" and "consumer_b".

In addition to defining your OAuth providers, you also need to ensure your 
grails-app/conf/Config.groovy file defines your grails.serverURL. This will
be used by the plug-in when generating your callback URL:

    // Set per-environment serverURL stem for creating absolute links
    environments {
        production {
            grails.serverURL = "http://www.changeme.com"
        }
        development {
            grails.serverURL = "http://localhost:8080/${appName}"
        }
        test {
            grails.serverURL = "http://localhost:8080/${appName}"
        }
    }

------------------------
Asking for authorization:
------------------------
To initiate the OAuth authentication process, first call the "auth"
action of the plugin's OauthController with the consumer name and
other optional parameters. The taglib shipped with the plugin
provides convenient tags to help you create the link to the action
with the parameters. Refer to the OauthTagLib section below for
information about the taglib. The "auth" action will perform the
perparation work and redirect to the user authorization URL.

For example, the following code creates a link to the "auth" action.
"myConsumer" should be specified in the config. When the user clicks
the link, the plugin will collect and prepare the required data and
redirect to the user authorization URL of the service provider which
the specified consumer belongs to. Then the user will be asked by
the service provider whether to allow your application to access the
user resources or not.

    <g:oauthLink consumer='myConsumer'
        returnTo="[controller: 'myController',
            action: 'oauthComplete']">Authorize</g:oauthLink>

------------------------
The callback and the access token:
------------------------
OAuth plugin provides a predefined callback action. It also handles
exchanging the authorized token with the access token. So the only
thing you need to take care of is to define your own action to handle
the access token stored in the session by the callback.

After the user has chosen to allow or disallow the access, the
service provider redirects back to "callback" action of the
OauthController. The "callback" action will do the more homework and
finally store the access token in the session object "oauthToken".
"oauthToken" is a map with entries "key" and "secret", which contains
the token key and secret of the access token.

Then your application's "returnTo" controller action will be called.
You can then have your own code handle the access token any way you
want (e.g. store the access token in the database).

However, depending on the implementation of the service provider, you
may have to specify a hard coded callback URL on the service provider's
website. In  that case, specify the callback URL at the service
provider to the URL of the "callback" action of the OauthController.
e.g.:

    http://your.server/your-app/oauth/callback

You can also map the URL any way you like by using your own UrlMappings.

------------------------
Accessing OAuth protected resource:
------------------------
After the completion of OAuth authorization by obtaining the authorized
access token, your application is now qualified to access the protected
user resource. The OauthService#accessResource method provides a
convenient way for doing this.

Accessing a read-only resource. The "GET" method is used by default.
The response returned is a String containing the http response body:

    def response = oauthService.accessResource('http://api.url',
        'consumer_name', [key:'accesskey',secret:'accesssecret'])

Posting data to API resource:

    def response = oauthService.accessResource('http://api.url',
	'consumer_name', [key:'accesskey',secret:'accesssecret'],
	'POST',	[param1:'additional parameter'])

You can also use named parameters. Note that "consumer", "url", and
"token" are required parameters:

    def response = oauthService.accessResource(url: 'http://api.url',
        consumer: 'consumer_name', token: [key: 'accesskey',
            secret: 'accesssecret'], method: 'POST')

------------------------
Error handling:
------------------------
When problems occur, the OauthService will throw an OauthServiceException.
You may catch the exception in your own code.

The OauthController makes use of the following i18n codes whenever an
OauthServiceExceptioin is caught from the service:

    * oauth.unknown
    * oauth.requesttoken.missing
    * oauth.accesstoken.missing
    * oauth.400badrequest
    * oauth.oauth.401unauthorized
    * oauth.invalid.consumer
    * oauth.invalid.token

You can create i18n messages for each of the error code. You can open the
grails-app/i18n/messages.properties file shipped with the plugin to see the
sample error messages and include them in your application's i18n messages
file.

------------------------
OauthService
------------------------

    fetchRequestToken(java.lang.String consumerName)

Used to fetch the authorization request token from the provided consumer.

    fetchAccessToken(java.lang.String consumerName,
        java.util.Map requestToken)

Used to return the access token returned from the provided consumer.

    accessResource(java.lang.String url, java.lang.String consumer,
        java.util.Map token, java.lang.String method='GET',
        java.util.Map params=null)

Helper function with default parameters to access an OAuth protected
resource. "url", "consumer", and "token" are required. "token" should be
a map containing two entries; "key" and "secret". Returns the response
body from the service as a String.

    accessResource(java.util.Map args)

Overloaded helper function with named parameters to access an OAuth
protected resource. Returns the response body from the service as a
String.

    getConsumer(java.lang.String name)

Returns the consumer instance by name.

    getProvider(java.lang.String name)

Returns the OAuth service provider instance by name.

------------------------
OauthController
------------------------

    auth

The action to call to start the OAuth authorization sequence.

    callback

This action will be called when the OAuth service provider returns
from user authorization.

------------------------
OauthTagLib
------------------------

    oauthLink

Renders an OAuth user authorization request link to the service
provider. "returnTo" and "error" are optional. If none specified,
the current controller and action will be used by default. The
"returnTo" controller action will be called by OauthController#callback
when the authorization process completes successfully. "error"
controller and action will be called in case of any problem occurs.

Example:

    <g:oauthLink consumer='myConsumer'>Authorize</g:oauthLink>
    <g:oauthLink consumer='myConsumer'
        returnTo="[controller: 'myController']">Authorize</g:oauthLink>
    <g:oauthLink consumer='myConsumer'
        returnTo="[controller: 'myController',
            action: 'oauthComplete']">Authorize</g:oauthLink>
    <g:oauthLink consumer='myConsumer'
        returnTo="[controller: 'myController',
            action: 'oauthComplete']"
        error="[controller: 'errorController',
            action: 'errorAction']">Authorize</g:oauthLink>

    oauthUrl

Construct the URL string for OAuth authorization action

    hasOauthError

Renders the body of the tag when there's an OAuth error

Example:

    <g:hasOauthError>
        <div class="errors">
            <g:renderOauthError />
        </div>
    </g:hasLoginError>

    renderOauthError

Renders the OAuth error. You should define messages for the error
code. Refer to the "Error handling" section above.

Example:

    <g:renderOauthError />

------------------------
Further documentation
------------------------
For further information regarding OAuth and the libraries used to
provide OAuth in this plug-in, please refer to the following
documentation:

    * http://oauth.net
    * http://code.google.com/p/oauth-signpost

------------------------
Example usage
------------------------
For an example usage of this plug-in please refer to the Grails
Picasa plug-in:

    * http://www.grails.org/plugin/picasa

------------------------
Contribute
------------------------
If you wish to contribute to the project you can find the full
source available on GitHub.org:

    * http://wiki.github.com/dhou/grails-oauth
