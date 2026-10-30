package com.dev.control_financiero.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpClientErrorException;

final class GmailPermissionErrors {
    private GmailPermissionErrors() {}
    static boolean needsReauthorization(Exception error) {
        if(error instanceof GmailAuthorizationException) return true;
        if(!(error instanceof HttpClientErrorException http))return false;
        if(http.getStatusCode().value()==401)return true;
        if(http.getStatusCode().value()!=403)return false;
        try {
            byte[] body=http.getResponseBodyAsByteArray();
            if(body.length>16384)return false;
            var json=new ObjectMapper().readTree(body);
            for(var detail:json.path("error").path("errors"))
                if("insufficientPermissions".equals(detail.path("reason").asText()))return true;
        } catch(Exception ignored) { }
        return false;
    }
}
