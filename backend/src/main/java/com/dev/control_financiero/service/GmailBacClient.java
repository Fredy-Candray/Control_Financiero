package com.dev.control_financiero.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

@Component
public class GmailBacClient {
    private final String clientId,clientSecret,redirectUri;
    private final RestClient http;
    public GmailBacClient(@Value("${app.gmail.client-id:}") String id,
            @Value("${app.gmail.client-secret:}") String secret,
            @Value("${app.gmail.redirect-uri:http://localhost:8081/api/gmail-bac/callback}") String redirect) {
        clientId=id; clientSecret=secret; redirectUri=redirect;
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(10000); factory.setReadTimeout(20000);
        http=RestClient.builder().requestFactory(factory).build();
    }
    public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }
    public String authorizationUrl(String state,String challenge) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id",clientId).queryParam("redirect_uri",redirectUri).queryParam("response_type","code")
                .queryParam("scope","https://www.googleapis.com/auth/gmail.readonly").queryParam("access_type","offline")
                .queryParam("prompt","consent").queryParam("state",state).queryParam("code_challenge",challenge)
                .queryParam("code_challenge_method","S256").build().encode().toUriString();
    }
    public JsonNode exchange(String code,String verifier) {
        var form=baseForm(); form.add("code",code); form.add("code_verifier",verifier);
        form.add("redirect_uri",redirectUri); form.add("grant_type","authorization_code");
        return token(form);
    }
    public String accessToken(String refreshToken) {
        var form=baseForm(); form.add("grant_type","refresh_token"); form.add("refresh_token",refreshToken);
        return token(form).path("access_token").asText();
    }
    private LinkedMultiValueMap<String,String> baseForm() {
        var form=new LinkedMultiValueMap<String,String>(); form.add("client_id",clientId); form.add("client_secret",clientSecret); return form;
    }
    private JsonNode token(LinkedMultiValueMap<String,String> form) {
        return http.post().uri("https://oauth2.googleapis.com/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);
    }
    public JsonNode profile(String accessToken) { return get("https://gmail.googleapis.com/gmail/v1/users/me/profile",accessToken); }
    public JsonNode list(String accessToken,long after,String page) {
        return listQuery(accessToken,"from:baccredomatic.com {subject:\"Alerta PRF\" subject:\"Movimiento Monetario\" subject:Transfer365 subject:pago subject:abono subject:reverso subject:devolución} after:"+after,page);
    }
    public JsonNode listPay(String accessToken,long after,String page) {
        return listQuery(accessToken,"{from:notificacionespay@bcr.gob.sv from:notificaciones365pay@bcr.gob.sv} subject:Transacción after:"+after,page);
    }
    private JsonNode listQuery(String accessToken,String query,String page) {
        var uri=UriComponentsBuilder.fromUriString("https://gmail.googleapis.com/gmail/v1/users/me/messages")
                .queryParam("q",query).queryParam("maxResults",100);
        if(page!=null) uri.queryParam("pageToken",page);
        return get(uri.build().encode().toUriString(),accessToken);
    }
    public JsonNode message(String accessToken,String id) {
        if(!id.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Identificador de correo inválido.");
        return get("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id+"?format=raw",accessToken);
    }
    public void revoke(String token) {
        var form=new LinkedMultiValueMap<String,String>(); form.add("token",token);
        http.post().uri("https://oauth2.googleapis.com/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
    }
    private JsonNode get(String url,String accessToken) {
        // These URLs are already encoded by UriComponentsBuilder; a String template would encode them again.
        return http.get().uri(java.net.URI.create(url)).headers(h->h.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
    }
}
