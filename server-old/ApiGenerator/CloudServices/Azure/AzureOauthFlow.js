require("dotenv").config();

const AZURE_CLIENT_ID = process.env.AZURE_CLIENT_ID;
const AZURE_CLIENT_SECRET = process.env.AZURE_CLIENT_SECRET;
const AZURE_TENANT_ID = process.env.AZURE_TENANT_ID;
const AZURE_REDIRECT_URI = process.env.AZURE_REDIRECT_URI;
const AZURE_AUTH_URL = process.env.AZURE_AUTH_URL;
const AZURE_TOKEN_URL = process.env.AZURE_TOKEN_URL;
const AZURE_SCOPES = process.env.AZURE_SCOPES;
const AZURE_POST_LOGOUT_URI = proces.env.AZURE_POST_LOGOUT_URI;

const { DefaultAzureCredential } = require("@azure/identity");
const { SecretClient } = require("@azure/keyvault-secrets");

const axios = require('axios');
const crypto = require("crypto");

const querystring = require('querystring');
const jwt = require('jsonwebtoken');

const generateState = () => crypto.randomBytes(16).toString("hex");

const storeSecretInKeyVault = async (secretName, secretValue) => {
    const keyVaultName = process.env.AZURE_KEY_VAULT_NAME; 
    const keyVaultUri = `https://${keyVaultName}.vault.azure.net`;

    const credential = new DefaultAzureCredential();
    const client = new SecretClient(keyVaultUri, credential);

    try {
        await client.setSecret(secretName, secretValue);
    } catch (error) {
        throw new Error(error.message);
    }
};

const validateToken = async (token) => {
    try {
      const decoded = jwt.decode(token, { complete: true });
      if (!decoded?.header?.kid) throw new Error("Invalid token structure");
  
      const keysUrl = `https://login.microsoftonline.com/${AZURE_TENANT_ID}/discovery/v2.0/keys`;
      const { data } = await axios.get(keysUrl);
  
      const key = data.keys.find((k) => k.kid === decoded.header.kid);
      if (!key) throw new Error("Key not found for token validation");
  
      const publicKey = `-----BEGIN CERTIFICATE-----\n${key.x5c[0]}\n-----END CERTIFICATE-----`;
      return jwt.verify(token, publicKey, {
        algorithms: ["RS256"],
        audience: AZURE_CLIENT_ID,
        issuer: `https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0`,
      });
    } catch (error) {
      throw new Error(`Token validation failed: ${error.message}`);
    }
  };
  
exports.LoginToAzure = async (req, res) => {
    try {
        if (!req.session) throw new Error("Session not available");

        const state = generateState();
        req.session.state = state; 

        const params = querystring.stringify({
            client_id: AZURE_CLIENT_ID,
            response_type: "code",
            redirect_uri: AZURE_REDIRECT_URI,
            response_mode: "query",
            scope: "openid profile email offline_access " + AZURE_SCOPES,
            state: state,
        });

        const authUrl = `${AZURE_AUTH_URL}?${params}`;

        return res.redirect(authUrl);
    } catch (error) {
        return res.status(500).send("Failed to authenticate.");
    } 
}

exports.CallbackOAuth = async (req, res) => {
    const { code, state } = req.query;

    if (!code) {
        return res.status(400).send("Authorization code not found.");
    }

    if (!state || state !== req.session.state) {
        return res.status(400).send("Invalid state parameter.");
    }

    try {
        const response = await axios.post(
            AZURE_TOKEN_URL,
            querystring.stringify({
              client_id: AZURE_CLIENT_ID,
              client_secret: AZURE_CLIENT_SECRET,
              grant_type: "authorization_code",
              code: code,
              redirect_uri: AZURE_REDIRECT_URI,
            }),
            { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );

        if (!response.data.access_token || !response.data.refresh_token || !response.data.id_token) {
            return res.status(500).send("Failed to authenticate.");
        }

        const user = await validateToken(id_token);
        req.session.accessToken = response.data.access_token;
        req.session.refreshToken = response.data.refresh_token;

        var randomId = Math.random().toString(10);

        await storeSecretInKeyVault(`oauth-access-token-${randomId}`, response.data.accessToken);
        await storeSecretInKeyVault(`oauth-refresh-token-${randomId}`, response.data.refreshToken);

        const subscriptions = await getAzureSubscriptions(response.data.accessToken);

        return res.status(200).send({"accessToken": response.data.access_token, "refreshToken": response.data.refresh_token, "id_token": response.data.id_token, "token_verification": user, "tokenManagerId": randomId, "subscriptions": subscriptions});
    } catch (error) {
        return res.status(500).send("Failed to authenticate.");
    }
}

exports.RefreshAccessTokenHandler = async (refreshToken) => {
    try {
        const response = await axios.post(
            AZURE_TOKEN_URL,
            querystring.stringify({
              client_id: AZURE_CLIENT_ID,
              client_secret: AZURE_CLIENT_SECRET,
              grant_type: "refresh_token",
              refresh_token: refreshToken,
            }),
            { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );

        if (!response.data.access_token) {
            throw new Error('Access Token not created');
        }

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.RefreshAccessToken = async (req, res) => {
    try {
        let refreshToken = req.session?.refreshToken;
        if (!refreshToken) {
            refreshToken = req.session_refresh_token;
        }
    
        var tokens = await this.RefreshAccessTokenHandler(refreshToken);

        req.session.accessToken = tokens.access_token;
        if (tokens.refresh_token) {
          req.session.refreshToken = tokens.refresh_token;
        }

        await storeSecretInKeyVault(`oauth-access-token-${req.body.secretId}`, tokens.access_token);
        await storeSecretInKeyVault(`oauth-refresh-token-${req.body.secretId}`, tokens.refresh_token);
    
        return res.status(200).send({
          accessTokenVal: tokens.access_token,
          refreshTokenVal: refreshToken || tokens.refresh_token,
        });
    } catch (error) {
        return res.status(500).send("Failed to get new access token.");
    }
}

exports.LogoutFromAzure = async (req, res) => {
    if (!req.session) {
        return res.status(400).send({ error: "Session not available" });
    }

    const tenant = AZURE_TENANT_ID || "common"; 
    const logoutUrl = `https://login.microsoftonline.com/${tenant}/oauth2/v2.0/logout?post_logout_redirect_uri=${encodeURIComponent(
        AZURE_POST_LOGOUT_URI
    )}`;

    req.session.destroy((err) => {
        if (err) {
          return res.status(500).send({ error: "Failed to log out", details: err.message });
        }
    
        res.clearCookie("connect.sid");
    
        return res.redirect(logoutUrl);
    });

}

const getAzureSubscriptions = async (accessToken) => {
    try {
        const response = await axios.get("https://management.azure.com/subscriptions?api-version=2020-01-01", {
            headers: {
                Authorization: `Bearer ${accessToken}`,
            },
        });

        return response.data.value; 
    } catch (error) {
        throw new Error(`Failed to fetch subscriptions: ${error.message}`);
    }
};
