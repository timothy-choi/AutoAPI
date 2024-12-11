const { google } = require('googleapis');

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;


exports.createOAuth2Client = async (accessToken, refreshToken) => {
    try {
        const oauth2Client = new google.auth.OAuth2(
            GCLOUD_CLIENT_ID,
            GCLOUD_CLIENT_SECRET,
            GCLOUD_REDIRECT_URL 
        );

        oauth2Client.setCredentials({ access_token: accessToken, refresh_token: refreshToken });
        return oauth2Client;
    } catch (error) {
        throw new Error(error.message);
    }
}

