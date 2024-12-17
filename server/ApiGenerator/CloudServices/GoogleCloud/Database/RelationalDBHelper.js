const { google } = require('googleapis');

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;

const executeWithRetry = async (operation, retries = 3, delayMs = 1000, backoffFactor = 2) => {
    let attempt = 0;

    while (attempt < retries) {
        try {
            return await operation();
        } catch (error) {
            attempt++;
            if (attempt >= retries) {
                throw new Error(`Operation failed after ${attempt} retries: ${error.message}`);
            }

            const backoffDelay = delayMs * Math.pow(backoffFactor, attempt - 1);
            await new Promise(resolve => setTimeout(resolve, backoffDelay));
        }
    }
};

exports.trackGCloudDBOperationStatus = async (projectId, operationId, authClient, timeoutMs = 300000) => {
    const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

    const startTime = Date.now();
    const backoffFactor = 2; 
    let delayMs = 5000;     

    while (true) {
        try {
            const response = await executeWithRetry(
                () => sqlAdmin.operations.get({
                    project: projectId,
                    operation: operationId,
                    auth: authClient,
                }),
                3, 1000);

            if (response.data.status === 'DONE') {
                if (response.data.error) {
                    throw new Error(`Operation failed: ${JSON.stringify(response.data.error)}`);
                }
                return response.data; 
            }
        } catch (error) {
            throw new Error(`Error tracking operation: ${error.message}`);
        }

        const elapsedTime = Date.now() - startTime;
        if (elapsedTime > timeoutMs) {
            throw new Error(`Operation timed out after ${timeoutMs}ms.`);
        }

        await new Promise(resolve => setTimeout(resolve, delayMs));
        delayMs = Math.min(delayMs * backoffFactor, 30000); 
    }
};


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

exports.startOrStopGCloudDBInstance = async (projectId, instanceId, action) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.instances.patch({
            project: projectId,
            instance: instanceId,
            auth: authClient,
            requestBody: {
                settings: {
                    activationPolicy: (action === 'start' ? 'ALWAYS' : 'NEVER'),
                },
            },
        });

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createGCloudBackup = async (authClient, projectId, instanceId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.backupRuns.insert({
            project: projectId,
            instance: instanceId,
        });

        return res.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.restoreGCloudBackup = async (authClient, projectId, instanceId, backupId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        await sqlAdmin.instances.restoreBackup({
            project: projectId,
            instance: instanceId,
            requestBody: {
                restoreBackupContext: {
                    backupRunId: backupId,
                },
            },
        });
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.updateGCloudDBInstanceSettings = async (authClient, projectId, instanceId, settings) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        var response = await sqlAdmin.instances.patch({
            project: projectId,
            instance: instanceId,
            requestBody: { settings },
        });

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.FailoverGCloudDBInstance = async (authClient, projectId, instanceId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const response = await sqlAdmin.instances.failover({
            project: projectId,
            instance: instanceId,
            requestBody: { failoverContext: {} },
        });

        return response.data;
    } catch (error) {
        throw new Error('Failed to trigger failover.', error);
    }
};

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

exports.listGCloudDBInstances = async (authClient, projectId) => {
    try {
        const sqlAdmin = google.sqladmin({ version: 'v1beta4', auth: authClient });

        const res = await sqlAdmin.instances.list({ project: projectId });

        return res.data.items || [];
    } catch (error) {
        throw new Error(`Failed to list instances: ${error.message}`);
    }
};

