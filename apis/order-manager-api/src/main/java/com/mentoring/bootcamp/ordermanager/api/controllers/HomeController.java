package com.mentoring.bootcamp.ordermanager.api.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.security.Principal;

/**
 * Landing page after a direct login on /login (Spring Security redirects to "/" by default).
 * In the OAuth2 flow the user is sent back to the client (e.g. Bruno) instead, so this page is not involved.
 */
@RestController
@Hidden // Not part of the REST API: kept out of Swagger
public class HomeController {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Order Manager</title>
                <style>
                    body { font-family: system-ui, sans-serif; max-width: 36rem; margin: 4rem auto; padding: 0 1rem; color: #1f2937; }
                    h1 { font-size: 1.5rem; }
                    .ok { color: #15803d; }
                    .ko { color: #b45309; }
                    a { color: #1d4ed8; }
                    li { margin: .4rem 0; }
                </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home(Principal principal) {
        if (principal == null) {
            return PAGE.formatted("""
                    <h1 class="ko">Not connected</h1>
                    <p>You are not logged in.</p>
                    <ul>
                        <li><a href="login">Log in</a></li>
                        <li><a href="swagger-ui.html">API documentation (Swagger)</a></li>
                    </ul>
                    """);
        }
        return PAGE.formatted("""
                <h1 class="ok">&#10004; Successfully connected</h1>
                <p>Logged in as <strong>%s</strong>.</p>
                <p>To call the API, get an access token with Bruno (OAuth2) and send it as <code>Authorization: Bearer &lt;token&gt;</code>.</p>
                <ul>
                    <li><a href="swagger-ui.html">API documentation (Swagger)</a></li>
                    <li><a href="logout">Log out</a></li>
                </ul>
                """.formatted(HtmlUtils.htmlEscape(principal.getName())));
    }
}
