/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.security;

import java.io.IOException;
import java.util.logging.Level;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.AuthenticationInfo;
import org.restlet.data.ChallengeMessage;
import org.restlet.data.ChallengeRequest;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Parameter;
import org.restlet.engine.Engine;
import org.restlet.engine.http.HeaderReader;
import org.restlet.engine.http.HttpConstants;
import org.restlet.security.Guard;
import org.restlet.util.Series;

/**
 * Authentication utilities.
 * 
 * @author Jerome Louvel
 * @author Ray Waldin (ray@waldin.net)
 */
@SuppressWarnings("deprecation")
public class AuthenticatorUtils {

    /**
     * Indicates if any of the objects is null.
     * 
     * @param objects
     *            The objects to test.
     * @return True if any of the objects is null.
     */
    public static boolean anyNull(Object... objects) {
        for (final Object o : objects) {
            if (o == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates if the request is properly authenticated. By default, this
     * delegates credentials checking to checkSecret().
     * 
     * @param request
     *            The request to authenticate.
     * @param guard
     *            The associated guard to callback.
     * @return -1 if the given credentials were invalid, 0 if no credentials
     *         were found and 1 otherwise.
     * @see Guard#checkSecret(Request, String, char[])
     * @deprecated See new org.restlet.security package.
     */
    @Deprecated
    public static int authenticate(Request request, Guard guard) {
        int result = Guard.AUTHENTICATION_MISSING;

        if (guard.getScheme() != null) {
            // An authentication scheme has been defined,
            // the request must be authenticated
            final ChallengeResponse cr = request.getChallengeResponse();

            if (cr != null) {
                if (guard.getScheme().equals(cr.getScheme())) {
                    final AuthenticatorHelper helper = Engine.getInstance()
                            .findHelper(cr.getScheme(), false, true);

                    if (helper != null) {
                        result = helper.authenticate(cr, request, guard);
                    } else {
                        throw new IllegalArgumentException("Challenge scheme "
                                + guard.getScheme()
                                + " not supported by the Restlet engine.");
                    }
                } else {
                    // The challenge schemes are incompatible, we need to
                    // challenge the client
                }
            } else {
                // No challenge response found, we need to challenge the client
            }
        }

        if (request.getChallengeResponse() != null) {
            // Update the challenge response accordingly
            request.getChallengeResponse().setAuthenticated(
                    result == Guard.AUTHENTICATION_VALID);
        }

        // Update the client info accordingly
        request.getClientInfo().setAuthenticated(
                result == Guard.AUTHENTICATION_VALID);

        return result;
    }

    /**
     * Challenges the client by adding a challenge request to the response and
     * by setting the status to CLIENT_ERROR_UNAUTHORIZED.
     * 
     * @param response
     *            The response to update.
     * @param stale
     *            Indicates if the new challenge is due to a stale response.
     * @param guard
     *            The associated guard to callback.
     * @deprecated See new org.restlet.security package.
     */
    @Deprecated
    public static void challenge(Response response, boolean stale, Guard guard) {
        final AuthenticatorHelper helper = Engine.getInstance().findHelper(
                guard.getScheme(), false, true);

        if (helper != null) {
            helper.challenge(response, stale, guard);
        } else {
            throw new IllegalArgumentException("Challenge scheme "
                    + guard.getScheme()
                    + " not supported by the Restlet engine.");
        }
    }

    /**
     * Formats a challenge request as a HTTP header value. The header is
     * {@link HttpConstants#HEADER_WWW_AUTHENTICATE}.
     * 
     * @param challenge
     *            The challenge request to format.
     * @param response
     *            The parent response.
     * @param httpHeaders
     *            The current response HTTP headers.
     * @return The {@link HttpConstants#HEADER_WWW_AUTHENTICATE} header value.
     */
    public static String formatRequest(ChallengeRequest challenge,
            Response response, Series<Parameter> httpHeaders) {
        String result = null;

        if (challenge != null) {
            AuthenticatorHelper helper = Engine.getInstance().findHelper(
                    challenge.getScheme(), false, true);

            if (helper != null) {
                try {
                    result = helper.formatRequest(challenge, response,
                            httpHeaders);
                } catch (IOException e) {
                    Context.getCurrentLogger().log(
                            Level.WARNING,
                            "Unable to format the challenge request: "
                                    + challenge, e);
                }
            } else {
                result = "?";
                Context.getCurrentLogger().warning(
                        "Challenge scheme " + challenge.getScheme()
                                + " not supported by the Restlet engine.");
            }
        }

        return result;
    }

    /**
     * Formats a challenge response as a HTTP header value. The header is
     * {@link HttpConstants#HEADER_AUTHORIZATION}.
     * 
     * @param challenge
     *            The challenge response to format.
     * @param request
     *            The parent request.
     * @param httpHeaders
     *            The current request HTTP headers.
     * @return The {@link HttpConstants#HEADER_AUTHORIZATION} header value.
     * @throws IOException
     */
    public static String formatResponse(ChallengeResponse challenge,
            Request request, Series<Parameter> httpHeaders) throws IOException {
        String result = null;
        AuthenticatorHelper helper = Engine.getInstance().findHelper(
                challenge.getScheme(), true, false);

        if (helper != null) {
            result = helper.formatResponse(challenge, request, httpHeaders);
        } else {
            result = "?";
            Context.getCurrentLogger().warning(
                    "Challenge scheme " + challenge.getScheme()
                            + " not supported by the Restlet engine.");
        }

        return result;
    }

    /**
     * Parses the "Authentication-Info" header.
     * 
     * @param header
     *            The header value to parse.
     * @return The equivalent {@link AuthenticationInfo} instance.
     * @throws IOException
     */
    public static AuthenticationInfo parseAuthenticationInfo(String header) {
        AuthenticationInfo result = null;
        HeaderReader hr = new HeaderReader(header);

        try {
            Parameter param;
            param = hr.readParameter();

            while (param != null) {

                param = hr.readParameter();
            }

            String nextNonce = null;
            int nonceCount = 0;
            String cnonce = null;
            String qop = null;
            String responseAuth = null;

            String[] authFields = header.split(",");
            for (String field : authFields) {
                String[] nameValuePair = field.trim().split("=");
                if (nameValuePair[0].equals("nextnonce")) {
                    nextNonce = nameValuePair[1];
                } else if (nameValuePair[0].equals("nc")) {
                    nonceCount = Integer.parseInt(nameValuePair[1], 16);
                } else if (nameValuePair[0].equals("cnonce")) {
                    cnonce = nameValuePair[1];
                    if (cnonce.charAt(0) == '"') {
                        cnonce = cnonce.substring(1, cnonce.length() - 1);
                    }
                } else if (nameValuePair[0].equals("qop")) {
                    qop = nameValuePair[1];
                } else if (nameValuePair[0].equals("responseAuth")) {
                    responseAuth = nameValuePair[1];
                }
            }

            result = new AuthenticationInfo(nextNonce, nonceCount, cnonce, qop,
                    responseAuth);
        } catch (IOException e) {
            Context.getCurrentLogger()
                    .log(
                            Level.WARNING,
                            "Unable to parse the authentication info header: "
                                    + header, e);
        }

        return result;
    }

    /**
     * Parses an authorization header into a challenge response. The header is
     * {@link HttpConstants#HEADER_AUTHORIZATION}.
     * 
     * @param challenge
     *            The challenge response to update.
     * @param request
     *            The parent request.
     * @param httpHeaders
     *            The current request HTTP headers.
     * @return The parsed challenge response.
     */
    private static ChallengeMessage parseMessage(boolean isChallengeResponse,
            Request request, Response response, String header,
            Series<Parameter> httpHeaders) {
        ChallengeMessage result = null;

        if (header != null) {
            int space = header.indexOf(' ');

            if (space != -1) {
                String scheme = header.substring(0, space);
                String rawValue = header.substring(space + 1);

                if (isChallengeResponse) {
                    result = new ChallengeResponse(new ChallengeScheme("HTTP_"
                            + scheme, scheme));
                } else {
                    result = new ChallengeRequest(new ChallengeScheme("HTTP_"
                            + scheme, scheme));
                }

                result.setRawValue(rawValue);
            }
        }

        if (result != null) {
            // Give a chance to the authenticator helper to do further parsing
            AuthenticatorHelper helper = Engine.getInstance().findHelper(
                    result.getScheme(), true, false);

            if (helper != null) {
                if (isChallengeResponse) {
                    helper.parseResponse((ChallengeResponse) result, request,
                            httpHeaders);
                } else {
                    helper.parseRequest((ChallengeRequest) result, response,
                            httpHeaders);
                }
            } else {
                Context.getCurrentLogger().warning(
                        "Couldn't find any helper support the "
                                + result.getScheme() + " challenge scheme.");
            }
        }

        return result;
    }

    /**
     * Parses an authenticate header into a challenge request. The header is
     * {@link HttpConstants#HEADER_WWW_AUTHENTICATE}.
     * 
     * @param header
     *            The HTTP header value to parse.
     * @param httpHeaders
     *            The current response HTTP headers.
     * @return The parsed challenge request.
     */
    public static ChallengeRequest parseRequest(Response response,
            String header, Series<Parameter> httpHeaders) {
        return (ChallengeRequest) parseMessage(false, null, response, header,
                httpHeaders);
    }

    /**
     * Parses an authorization header into a challenge response. The header is
     * {@link HttpConstants#HEADER_AUTHORIZATION}.
     * 
     * @param challenge
     *            The challenge response to update.
     * @param request
     *            The parent request.
     * @param httpHeaders
     *            The current request HTTP headers.
     * @return The parsed challenge response.
     */
    public static ChallengeResponse parseResponse(Request request,
            String header, Series<Parameter> httpHeaders) {
        return (ChallengeResponse) parseMessage(true, request, null, header,
                httpHeaders);
    }

    /**
     * Private constructor to ensure that the class acts as a true utility class
     * i.e. it isn't instantiable and extensible.
     */
    private AuthenticatorUtils() {
    }

}