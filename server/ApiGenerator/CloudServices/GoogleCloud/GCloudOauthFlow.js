const {OAuth2Client} = require('google-auth-library');

require('dotenv').config();

const crypto = require('crypto');

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;
const GCLOUD_SCOPES = process.env.GCLOUD_SCOPES.split(',').map(scope => scope.trim());

const oauth2Client = new OAuth2Client(
    GCLOUD_CLIENT_ID,
    GCLOUD_CLIENT_SECRET,
    GCLOUD_REDIRECT_URL
);

const generateState = () => crypto.randomBytes(16).toString('hex');

exports.LoginToGCloud = async (req, res) => {
    try {
        const state = generateState();
        req.session.state = state; 

        const url = oauth2Client.generateAuthUrl({
            access_type: 'offline', 
            scope: GCLOUD_SCOPES,
            prompt: 'consent',
            state: state,
        });
    
        res.redirect(url);
    } catch (error) {
        return res.status(500).send({ error: 'Failed to initiate login.', details: error.message });
    }
}

exports.GCloudOAuthCallback = async (req, res) => {
    const {code, state} = req.query;
    if (!code) {
        return res.status(400).send('No code returned from Google');
    }

    if (state !== req.session.state) {
        return res.status(400).send({ error: 'Invalid state parameter. Potential CSRF attack.' });
    }

    try {
        const { tokens } = await oauth2Client.getToken(code);
        oauth2Client.setCredentials(tokens);

        return res.status(200).send({"accessToken": tokens.access_token, "refreshToken": tokens.refresh_token});
    } catch (error) {
        return res.status(500).send("Failed to authenticate");
    }
}

exports.RefreshAccessToken = async (refreshToken) => {
    try {
        oauth2Client.setCredentials({ refresh_token: refreshToken });

        const { token: newAccessToken, res } = await oauth2Client.getAccessToken();

        const newRefreshToken = res.data.refresh_token || refreshToken;

        return {
            accessToken: newAccessToken,
            refreshToken: newRefreshToken,
        };
    } catch (error) {
        throw new Error(error.Message);
    }
}
