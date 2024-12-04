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