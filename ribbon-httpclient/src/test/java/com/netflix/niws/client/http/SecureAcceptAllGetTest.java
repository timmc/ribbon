/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.niws.client.http;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.testutil.SimpleSSLTestServer;
import com.netflix.config.ConfigurationManager;
import com.sun.jersey.core.util.Base64;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.*;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.File;
import java.io.FileOutputStream;

import java.net.URI;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * Test that the AcceptAllSocketFactory works against a dumbed down TLS server, except when it shouldn't...
 *
 * @author jzarfoss
 *
 */
public class SecureAcceptAllGetTest {

    private static int TEST_PORT;
    private static String TEST_SERVICE_URI;

    private static File TEST_FILE_KS;
    private static File TEST_FILE_TS;

    private static SimpleSSLTestServer TEST_SERVER;


    @BeforeClass
    public static void init() throws Exception {

        // setup server 1, will use first keystore/truststore with client auth
        TEST_PORT = new Random().nextInt(1000) + 4000;
        TEST_SERVICE_URI = "https://127.0.0.1:" + TEST_PORT + "/";

        // jks format
        byte[] sampleTruststore1 = Base64.decode(SecureGetTest.TEST_TS1);
        byte[] sampleKeystore1 = Base64.decode(SecureGetTest.TEST_KS1);

        TEST_FILE_KS = File.createTempFile("SecureAcceptAllGetTest", ".keystore");
        TEST_FILE_TS = File.createTempFile("SecureAcceptAllGetTest", ".truststore");

        FileOutputStream keystoreFileOut = new FileOutputStream(TEST_FILE_KS);
        keystoreFileOut.write(sampleKeystore1);
        keystoreFileOut.close();

        FileOutputStream truststoreFileOut = new FileOutputStream(TEST_FILE_TS);
        truststoreFileOut.write(sampleTruststore1);
        truststoreFileOut.close();

        try{
            TEST_SERVER = new SimpleSSLTestServer(TEST_FILE_TS, SecureGetTest.PASSWORD, TEST_FILE_KS, SecureGetTest.PASSWORD, TEST_PORT, false);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }


    }

    @AfterClass
    public static void shutDown(){

        try{
            TEST_SERVER.close();
        }catch(Exception e){
            e.printStackTrace();
        }

    }


    @Test
    public void testPositiveAcceptAllSSLSocketFactory() throws Exception{

        // test connection succeeds connecting to a random SSL endpoint with allow all SSL factory

        AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

        String name = "GetPostSecureTest" + ".testPositiveAcceptAllSSLSocketFactory";

        String configPrefix = name + "." + "ribbon";

        cm.setProperty(configPrefix + "." + CommonClientConfigKey.CustomSSLSocketFactoryClassName, "com.netflix.http4.ssl.AcceptAllSocketFactory");

        RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

        TEST_SERVER.accept();

        URI getUri = new URI(TEST_SERVICE_URI + "test/");
        HttpRequest request = HttpRequest.newBuilder().uri(getUri).queryParams("name", "test").build();
        HttpResponse response = rc.execute(request);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testNegativeAcceptAllSSLSocketFactoryCannotWorkWithTrustStore() throws Exception{

        // test config exception happens before we even try to connect to anything

        AbstractConfiguration cm = ConfigurationManager.getConfigInstance();

        String name = "GetPostSecureTest" + ".testNegativeAcceptAllSSLSocketFactoryCannotWorkWithTrustStore";

        String configPrefix = name + "." + "ribbon";

        cm.setProperty(configPrefix + "." + CommonClientConfigKey.CustomSSLSocketFactoryClassName, "com.netflix.http4.ssl.AcceptAllSocketFactory");
        cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStore, TEST_FILE_TS.getAbsolutePath());
        cm.setProperty(configPrefix + "." + CommonClientConfigKey.TrustStorePassword, SecureGetTest.PASSWORD);

        boolean foundCause = false;

        try{

            ClientFactory.getNamedClient(name);

        }catch(Throwable t){

             while(t != null && ! foundCause){

                 if(t instanceof IllegalArgumentException && t.getMessage().startsWith("Invalid value associated with property:CustomSSLSocketFactoryClassName")){
                     foundCause = true;
                     break;
                 }

                 t = t.getCause();
             }
        }

        assertTrue(foundCause);

    }


    @Test
    public void testNegativeAcceptAllSSLSocketFactory() throws Exception{

        // test exception is thrown connecting to a random SSL endpoint without explicitly setting factory to allow all

        String name = "GetPostSecureTest" + ".testNegativeAcceptAllSSLSocketFactory";

        // don't set any interesting properties -- really we're just setting the defaults

        RestClient rc = (RestClient) ClientFactory.getNamedClient(name);

        TEST_SERVER.accept();

        URI getUri = new URI(TEST_SERVICE_URI + "test/");
        HttpRequest request = HttpRequest.newBuilder().uri(getUri).queryParams("name", "test").build();


        boolean foundCause = false;

        try{

            rc.execute(request);

        }catch(Throwable t){

            while(t != null && ! foundCause){

                if(t instanceof SSLPeerUnverifiedException && t.getMessage().startsWith("peer not authenticated")){
                    foundCause = true;
                    break;
                }

                t = t.getCause();
            }
        }

        assertTrue(foundCause);



    }


}
