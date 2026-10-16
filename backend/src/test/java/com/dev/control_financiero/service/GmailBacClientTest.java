package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailBacClientTest {
    @Test void sendsPaySearchWithoutDoubleEncoding() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new GmailBacClient("test","test","http://localhost/callback");
        ReflectionTestUtils.setField(client,"http",builder.build());
        server.expect(request->{
            var query=URLDecoder.decode(request.getURI().getRawQuery(),StandardCharsets.UTF_8);
            assertTrue(query.contains("from:notificacionespay@bcr.gob.sv"));
            assertTrue(query.contains("subject:Transacción after:123"));
            assertFalse(query.contains("%40"));
        }).andRespond(withSuccess("{\"messages\":[{\"id\":\"new\"}]}",MediaType.APPLICATION_JSON));
        assertEquals("new",client.listPay("test",123,null).path("messages").get(0).path("id").asText());
        server.verify();
    }
    @Test void sendsBacSearchWithoutDoubleEncoding() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new GmailBacClient("test","test","http://localhost/callback");
        ReflectionTestUtils.setField(client,"http",builder.build());
        server.expect(request->{
            var query=URLDecoder.decode(request.getURI().getRawQuery(),StandardCharsets.UTF_8);
            assertTrue(query.contains("from:baccredomatic.com"));
            assertTrue(query.contains("subject:pago"));
            assertTrue(query.contains("after:123"));
        }).andRespond(withSuccess("{\"messages\":[]}",MediaType.APPLICATION_JSON));
        client.list("test",123,null); server.verify();
    }
}
