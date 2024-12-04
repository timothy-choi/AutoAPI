const {OAuth2Client} = require('google-auth-library');

require('dotenv').config();

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;
const GCLOUD_SCOPES = process.env.GCLOUD_SCOPES;

const oauth2Client = OAuth2Client(
    GCLOUD_CLIENT_ID,
    GCLOUD_CLIENT_SECRET,
    GCLOUD_REDIRECT_URL
);

exports.LoginToGCloud = async (req, res) => {
    const url = oauth2Client.generateAuthUrl({
        access_type: 'offline', 
        scope: GCLOUD_SCOPES,
    });

    res.redirect(url);
}

exports.GCloudOAuthCallback = async (req, res) => {
    const code = req.query.code;
    if (!code) {
        return res.status(400).send('No code returned from Google');
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
    oauth2Client.setCredentials({ refresh_token: refreshToken });

    try {
        const { token: newAccessToken, res } = await oauth2Client.getAccessToken();

        const newRefreshToken = res.data.refresh_token || refreshToken;

        return {
            accessToken: newAccessToken,
            refreshToken: newRefreshToken,
        };
    } catch (error) {
        console.error('Error refreshing access token:', error);
        throw error;
    }
}
