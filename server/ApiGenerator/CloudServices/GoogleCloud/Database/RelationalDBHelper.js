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

exports.createGCloudDBInstance = async (authClient, projectId, instanceConfig) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.instances.insert({
            project: projectId,
            requestBody: instanceConfig,
        });

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getGCloudDBInstanceDetails = async (authClient, projectId, instanceId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.instances.get({
            project: projectId,
            instance: instanceId,
        });

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.deleteGCloudDBInstance = async (authClient, projectId, instanceId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.instances.delete({
            project: projectId,
            instance: instanceId,
        });

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
}