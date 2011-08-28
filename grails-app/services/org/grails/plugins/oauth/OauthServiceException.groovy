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

/**
 * OAuth Service Exception.
 *
 * Checked exception used to wrap exceptions thrown by the OAuth Service.
 *
 * @author Anthony Campbell (anthonycampbell.co.uk)
 */
class OauthServiceException extends Exception {

    // Declare exception properties
    final String message

    /**
     * Constructor.
     *
     * @param message the exception message.
     */
    public OauthServiceException(String message) {
        super(message)
        this.message = message
    }

    /**
     * Constructor.
     *
     * @param message the exception message.
     * @param exception the wrapped exception.
     */
    public OauthServiceException(String message, Exception exception) {
        super(message, exception)
        this.message = message
    }

    /**
     * Return the exception error message.
     *
     * @return the exception error message.
     */
    public String getMessage() {
       return(this.message)
    }
}
