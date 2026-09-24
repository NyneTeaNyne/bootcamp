// Gets an access token the same way Bruno does (OAuth2 authorization code + PKCE),
// so the load tests can call the protected API without copying a token by hand.
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

const CLIENT_ID = __ENV.CLIENT_ID || 'localClientId';
const CLIENT_SECRET = __ENV.CLIENT_SECRET || 'localClientSecret';
const REDIRECT_URI = __ENV.REDIRECT_URI || 'http://127.0.0.1:8080/api/v1/authorized';
const USERNAME = __ENV.LOAD_TEST_USERNAME || 'load-test-user';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD || 'LoadTestPassw0rd!';

/** Creates the load test account (sign-up is public); does nothing if it already exists. */
export function ensureLoadTestUser() {
    const res = http.post(`${BASE_URL}/users`,
        JSON.stringify({ username: USERNAME, password: PASSWORD }),
        {
            headers: { 'Content-Type': 'application/json' },
            // 400 = the account already exists: expected, must not count as a failed request
            responseCallback: http.expectedStatuses(201, 400),
        });
    if (res.status !== 201 && res.status !== 400) {
        throw new Error(`Cannot create the load test user: HTTP ${res.status} ${res.body}`);
    }
}

/** Returns an access token for the load test account. Call it from setup(): it runs once. */
export function getAccessToken() {
    ensureLoadTestUser();

    // 1. Log in with the login form: the session cookie is kept by k6
    const login = http.post(`${BASE_URL}/login`, { username: USERNAME, password: PASSWORD }, { redirects: 0 });
    const afterLogin = login.headers['Location'] || '';
    if (login.status !== 302 || afterLogin.includes('error')) {
        throw new Error(`Login failed for ${USERNAME}: HTTP ${login.status} -> ${afterLogin}`);
    }

    // 2. Ask for an authorization code, protected by PKCE (required by Spring Security 7)
    const verifier = encoding.b64encode(crypto.randomBytes(32), 'rawurl');
    const challenge = encoding.b64encode(crypto.sha256(verifier, 'binary'), 'rawurl');
    const authorizeUrl = `${BASE_URL}/oauth2/authorize?response_type=code&scope=read`
        + `&client_id=${encodeURIComponent(CLIENT_ID)}`
        + `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}`
        + `&code_challenge=${challenge}&code_challenge_method=S256`;
    const authorize = http.get(authorizeUrl, { redirects: 0, headers: { Accept: 'text/html' } });
    const match = /[?&]code=([^&]+)/.exec(authorize.headers['Location'] || '');
    if (!match) {
        throw new Error(`No authorization code: HTTP ${authorize.status} -> ${authorize.headers['Location']}`);
    }

    // 3. Exchange the code for an access token (the client authenticates with its id and secret)
    const token = http.post(`${BASE_URL}/oauth2/token`, {
        grant_type: 'authorization_code',
        code: decodeURIComponent(match[1]),
        redirect_uri: REDIRECT_URI,
        code_verifier: verifier,
    }, { headers: { Authorization: `Basic ${encoding.b64encode(`${CLIENT_ID}:${CLIENT_SECRET}`)}` } });
    if (token.status !== 200) {
        throw new Error(`Token request failed: HTTP ${token.status} ${token.body}`);
    }
    return token.json('access_token');
}

export function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}
