const GetApiClient = require('./MongoDBApiHelper');

const retryOperation = async (operation, retries = 3, delay = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation(); 
        } catch (error) {
            attempt++;
            if (attempt >= retries) {
                throw new Error(`Operation failed after ${retries} attempts: ${error.message}`);
            }
            await new Promise(resolve => setTimeout(resolve, delay * Math.pow(2, attempt - 1))); 
        }
    }
  };

exports.createServiceAccount = async (mongoServiceAccountUri, name, apiKey, headerInfo) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {
            const response = await apiClient.post(mongoServiceAccountUri, JSON.stringify({
                name,
                description: "Service account for connecting to cloud services",
            }), { headerInfo });

            return response.data;
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getServiceAccountInfo = async (projectId, apiKeyId, headerInfo) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const url = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/apiKeys/${apiKeyId}`;

        var response = await apiClient.get(url, { headerInfo });

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateServiceAccount = async (projectId, apiKeyId, headerInfo, updates) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const url = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            var response = await apiClient.patch(url, updates, { headerInfo });

            return response.data;
        };

        return await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteServiceAccount = async (projectId, apiKeyId, headerInfo) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const url = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            await apiClient.delete(url, { headerInfo });
        };

        await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.addWhitelistEntry = async (projectId, apiKeyId, whitelistEntry, headerInfo) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const url = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            var response = await apiClient.post(url, { ipddress: whitelistEntry}, { headerInfo });

            return response.data;
        };

        await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.linkServiceAccountToProject = async (apiKeyId, projectId, roles, headerInfo) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const url = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            var response = await apiClient.post(url, JSON.stringify({roles: roles}), { headerInfo });

            return response.data;
        };

        return await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};