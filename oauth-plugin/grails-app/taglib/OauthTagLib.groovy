class OauthTagLib {
	/**
	 * Renders a authorization request link
	 * 
	 * Attributes:
	 * consumer - the oauth consumer name
	 * returnTo (optional) - a map containing the controller and action to redirect to after authorization complete 
	 * 
	 * Examples:
	 * 
	 * <oauth:authLink consumer='myConsumer' returnTo="[controller:'myController',action:'oauthComplete']">Authorize</oauth:authLink>
	 * <oauth:authLink consumer='myConsumer' returnTo="[controller:'myController']">Authorize</oauth:authLink>
	 * <oauth:authLink consumer='myConsumer'>Authorize</oauth:authLink>
	 */
	def authLink = { attrs, body ->
	    attrs.url = [controller:'oauth', action:'auth', params:[:]]
	    
	    def returnTo = attrs.remove('returnTo')
	    def controller = returnTo?.controller?:controllerName
	    attrs.url.params["return_controller"] = controller
	    def action = returnTo?.action?:actionName
	    attrs.url.params["return_action"] = action
	    
	    def error = attrs.remove('error')
	    def errorController = error?.controller?:controller
	    attrs.url.params['error_controller'] = errorController
	    def errorAction = error?.action?:action
	    attrs.url.params['error_action'] = errorAction
	    
	    def consumer = attrs.remove('consumer')
	    attrs.url.params['consumer'] = consumer
	    
	    out << g.link(attrs, body)
	}
     
	/**
	 * Invokes the body of this tag if there is a login error
	 * 
	 * Example:
	 * 
	 * <oauth:hasLoginError>
	 *     <div class="errors">
	 *         <ul>
	 *             <li><openid:renderLoginError /></li>
	 *         </ul>
	 *     </div>
	 * </oauth:hasLoginError>
	 */
	def hasOauthError = { attrs, body ->
	    if (flash.oauthError) {
	        out << body()
	    }
	}
	 
	 /**
	 * Renders the login error
	 * 
	 * Example:
	 * 
	 * <oauth:renderOauthError />
	 */
	def renderOauthError = { attrs ->
	    if (flash.oauthError) {
	        out << message(code:flash.oauthError)
	    }
	}
     
}
