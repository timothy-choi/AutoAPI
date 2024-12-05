require("dotenv").config();

const AZURE_CLIENT_ID = process.env.AZURE_CLIENT_ID;
const AZURE_CLIENT_SECRET = process.env.AZURE_CLIENT_SECRET;
const AZURE_TENANT_ID = process.env.AZURE_TENANT_ID;
const AZURE_REDIRECT_URI = process.env.AZURE_REDIRECT_URI;
const AZURE_AUTH_URL = process.env.AZURE_AUTH_URL;
const AZURE_TOKEN_URL = process.env.AZURE_TOKEN_URL;
const AZURE_SCOPES = process.env.AZURE_SCOPES;

const axios = require('axios');
const crypto = require("crypto");

const querystring = require('querystring');

const generateState = () => crypto.randomBytes(16).toString("hex");

exports.LoginToAzure = async (req, res) => {
    try {
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

        res.redirect(authUrl);
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

        req.session.accessToken = response.data.access_token;
        req.session.refreshToken = response.data.refresh_token;

        return res.status(200).send({"accessToken": response.data.access_token, "refreshToken": response.data.refresh_token, "id_token": response.data.id_token});
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
    
        return res.status(200).send({
          accessTokenVal: tokens.access_token,
          refreshTokenVal: refreshToken || tokens.refresh_token,
        });
    } catch (error) {
        return res.status(500).send("Failed to get new access token.");
    }
}