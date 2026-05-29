// Mint the ROPC token pool ONCE (in setup). Mirrors get-token.sh (password-realm grant).
import http from 'k6/http';
import { sleep } from 'k6';
import { AUTH0, POOL_SIZE } from './config.js';

export function mintTokenPool() {
  if (!Number.isInteger(POOL_SIZE) || POOL_SIZE < 1) {
    throw new Error('POOL_SIZE must be a positive integer (check .env), got: ' + String(POOL_SIZE));
  }
  const tokens = [];
  for (let i = 1; i <= POOL_SIZE; i++) {
    const res = http.post(
      'https://' + AUTH0.domain + '/oauth/token',
      {
        grant_type: 'http://auth0.com/oauth/grant-type/password-realm',
        realm: AUTH0.connection,
        audience: AUTH0.audience,
        scope: 'openid profile email',
        client_id: AUTH0.clientId,
        client_secret: AUTH0.clientSecret,
        username: AUTH0.emailPrefix + '-' + i + '@' + AUTH0.emailDomain,
        password: AUTH0.password,
      },
      { tags: { endpoint: 'auth/token', service: 'auth0' } }
    );
    if (res.status !== 200) {
      throw new Error('token mint ' + i + ' failed: ' + res.status + ' ' + String(res.body).slice(0, 200));
    }
    tokens.push(res.json('access_token'));
    sleep(0.15); // gentle pacing — Auth0 /oauth/token is itself rate-limited
  }
  return tokens;
}
