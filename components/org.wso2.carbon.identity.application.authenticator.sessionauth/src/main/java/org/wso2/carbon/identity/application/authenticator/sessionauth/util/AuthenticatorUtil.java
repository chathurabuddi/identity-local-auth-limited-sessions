/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.wso2.carbon.identity.application.authenticator.sessionauth.util;

import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.ssl.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.identity.application.authentication.framework.config.ConfigurationFacade;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authenticator.sessionauth.SessionCountAuthenticatorConstants;
import org.wso2.carbon.identity.application.authenticator.sessionauth.exception.SessionValidationException;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Contains Utility methods used in the Session Count Authenticator.
 */
public class AuthenticatorUtil {

    /**
     * Method to retrieve session data from session data source.
     *
     * @param authenticatedUser AuthenticatedUser object that represent the user
     * @return JSON array with each element describing active session
     * @throws IOException                When it fails to read response from the REST call
     * @throws SessionValidationException when REST response is not in state 200
     */
    public static JSONArray getSessionDetails(AuthenticatedUser authenticatedUser) throws
            IOException, SessionValidationException {

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        HttpClient httpClient = httpClientBuilder.build();
        HttpPost httpPost = createHttpRequest(authenticatedUser);
        HttpResponse httpResponse;
        JSONArray responseJsonArray;
        httpResponse = httpClient.execute(httpPost);
        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpResponse.getEntity()
                    .getContent(),
                    StandardCharsets.UTF_8.name()))) {
                StringBuilder responseResult = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseResult.append(line);
                }
                responseJsonArray = new JSONArray(responseResult.toString());
            }
        } else {
            throw new SessionValidationException("Failed to retrieve data from endpoint. Error code :" +
                    httpResponse.getStatusLine().getStatusCode());
        }
        return responseJsonArray;
    }

    /**
     * Method to create HTTP Request.
     *
     * @param authenticatedUser AuthenticatedUser object for the user
     * @return HttpPost request object
     */
    private static HttpPost createHttpRequest(AuthenticatedUser authenticatedUser) {

        JSONObject requestData = new JSONObject();
        requestData.put(SessionCountAuthenticatorConstants.TABLE_NAME_TAG,
                SessionCountAuthenticatorConstants.ACTIVE_SESSION_TABLE_NAME);
        requestData.put(SessionCountAuthenticatorConstants.QUERY_TAG,
                getQuery(authenticatedUser.getTenantDomain(), authenticatedUser.getUserName(), authenticatedUser
                        .getUserStoreDomain()));
        requestData.put(SessionCountAuthenticatorConstants.START_TAG, SessionCountAuthenticatorConstants.START_INDEX);
        requestData.put(SessionCountAuthenticatorConstants.COUNT_TAG,
                SessionCountAuthenticatorConstants.SESSION_COUNT_MAX);
        StringEntity entity = new StringEntity(requestData.toString(), ContentType.APPLICATION_JSON);
        HttpPost httpRequest = new HttpPost(IdentityUtil.getProperty(SessionCountAuthenticatorConstants
                .TABLE_SEARCH_CONFIG_NAMES));
        String toEncode = IdentityUtil.getProperty(SessionCountAuthenticatorConstants.USERNAME_CONFIG_NAME)
                + SessionCountAuthenticatorConstants.ATTRIBUTE_SEPARATOR
                + IdentityUtil.getProperty(SessionCountAuthenticatorConstants.PASSWORD_CONFIG_NAME);
        byte[] encoding = Base64.encodeBase64(toEncode.getBytes(Charset.forName(StandardCharsets.UTF_8.name())));
        String authHeader = new String(encoding, Charset.defaultCharset());
        //Adding headers to request
        httpRequest.addHeader(HTTPConstants.HEADER_AUTHORIZATION, SessionCountAuthenticatorConstants.AUTH_TYPE_KEY +
                authHeader);
        httpRequest.addHeader(SessionCountAuthenticatorConstants.CONTENT_TYPE_TAG, "application/json");
        httpRequest.setEntity(entity);
        return httpRequest;
    }

    /**
     * Method for building the query to get data from the data service.
     *
     * @param tenantDomain Tenant domain of the user
     * @param username     Username of the user
     * @param userStore    User Store of the user
     * @return Query string
     */
    public static String getQuery(String tenantDomain, String username, String userStore) {

        return
                SessionCountAuthenticatorConstants.TENANT_DOMAIN_TAG +
                        SessionCountAuthenticatorConstants.ATTRIBUTE_SEPARATOR +
                        tenantDomain +
                        SessionCountAuthenticatorConstants.AND_TAG +
                        SessionCountAuthenticatorConstants.USERNAME_TAG +
                        SessionCountAuthenticatorConstants.ATTRIBUTE_SEPARATOR +
                        username +
                        SessionCountAuthenticatorConstants.AND_TAG +
                        SessionCountAuthenticatorConstants.USER_STORE_TAG +
                        SessionCountAuthenticatorConstants.ATTRIBUTE_SEPARATOR +
                        userStore;
    }

    /**
     * Method used for adding authentication header for httpMethod.
     *
     * @param httpMethod httpMethod that needs auth header to be added
     * @param username   username of user
     * @param password   password of the user
     */
    public static HttpPost setAuthorizationHeader(HttpPost httpMethod, String username, String password) {

        String toEncode = username + SessionCountAuthenticatorConstants.ATTRIBUTE_SEPARATOR + password;
        byte[] encoding = org.apache.commons.codec.binary.Base64.encodeBase64(toEncode.getBytes(Charset.forName(StandardCharsets.UTF_8.name())));
        String authHeader = new String(encoding, Charset.defaultCharset());
        httpMethod.addHeader(HTTPConstants.HEADER_AUTHORIZATION,
                SessionCountAuthenticatorConstants.AUTH_TYPE_KEY + authHeader);
        return httpMethod;
    }

    /**
     * Method to retrieve custom login page for the authenticator.
     *
     * @return custom login page of authenticator
     */
    public static String getLoginPageURL() {

        return ConfigurationFacade.getInstance().getAuthenticationEndpointURL().replace(
                SessionCountAuthenticatorConstants.LOGIN_STANDARD_PAGE,
                SessionCountAuthenticatorConstants.SESSION_TERMINATION_ENFORCER_PAGE);
    }
}
