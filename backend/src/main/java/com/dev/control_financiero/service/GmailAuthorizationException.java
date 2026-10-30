package com.dev.control_financiero.service;

/** Safe marker: never retains Google's response body or any credential. */
public class GmailAuthorizationException extends IllegalStateException {
    public static final String PREFIX="REAUTH_REQUIRED: ";
    public GmailAuthorizationException() { super("El permiso de Gmail venció o fue revocado. Autoriza nuevamente la conexión."); }
}
