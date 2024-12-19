const { google } = require('googleapis');
const path = require('path');
const { OAuth2Client } = require('google-auth-library');

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;

const createOAuth2Client = async (accessToken, refreshToken) => {
    try {
        const oauth2Client = new OAuth2Client(
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

const createGCloudProject = async (projectInfo, authClient) => {
    try {
        const cloudResourceManager = google.cloudresourcemanager({
            version: 'v3',
            auth: authClient,
        });

        const res = await cloudResourceManager.projects.create(projectInfo);

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
};


exports.createGoogleCloudProject = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var oauthClient = createOAuth2Client(accessToken, req.body.refreshToken);

        var projectResponse = await createGCloudProject(req.body.projectInfo, oauthClient);

        return res.status(201).send({"projectResponse": projectResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};
