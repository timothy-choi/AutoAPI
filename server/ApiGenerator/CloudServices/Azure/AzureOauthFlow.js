require("dotenv").config();

const AZURE_CLIENT_ID = process.env.AZURE_CLIENT_ID;
const AZURE_CLIENT_SECRET = process.env.AZURE_CLIENT_SECRET;
const AZURE_TENANT_ID = process.env.AZURE_TENANT_ID;
const AZURE_REDIRECT_URI = process.env.AZURE_REDIRECT_URI;
const AZURE_AUTH_URL = process.env.AZURE_AUTH_URL;
const AZURE_TOKEN_URL = process.env.AZURE_TOKEN_URL;
const AZURE_SCOPES = process.env.AZURE_SCOPES;

const axios = require('axios');

exports.LoginToAzure = async (req, res) => {
    const params = querystring.stringify({
        client_id: AZURE_CLIENT_ID,
        response_type: "code",
        redirect_uri: AZURE_REDIRECT_URI,
        response_mode: "query",
        scope: "openid profile email offline_access " + AZURE_SCOPES,
    });

    const authUrl = `${AZURE_AUTH_URL}?${params}`;

    res.redirect(authUrl);
}

exports.CallbackOAuth = async (req, res) => {
    const { code } = req.query;

    if (!code) {
        return res.status(400).send("Authorization code not found.");
    }

    try {
        const response = await axios.post(
            process.env.TOKEN_URL,
            querystring.stringify({
              client_id: AZURE_CLIENT_ID,
              client_secret: AZURE_CLIENT_SECRET,
              grant_type: "authorization_code",
              code: code,
              redirect_uri: AZURE_REDIRECT_URI,
            }),
            { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );

        return res.status(200).send({"accessToken": response.data.access_token, "refreshToken": response.data.refresh_token});
    } catch (error) {
        return res.status(500).send("Failed to authenticate.");
    }
}