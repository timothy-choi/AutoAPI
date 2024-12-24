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

exports.createServiceAccount = async (mongoServiceAccountUri, name, apiKey) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {
            const response = await apiClient.post(mongoServiceAccountUri, {
                name,
                description: "Service account for connecting to cloud services",
            });

            return response.data;
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getServiceAccountInfo = async (organizationId, apiKeyId, headerInfo) => {
    try {
        const url = `https://cloud.mongodb.com/api/atlas/v1.0/orgs/${organizationId}/apiKeys/${apiKeyId}`;

        var response = await apiClient.get(url, { headerInfo });

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateServiceAccount = async (organizationId, apiKeyId, headerInfo, updates) => {
    try {
        const url = `https://cloud.mongodb.com/api/atlas/v1.0/orgs/${organizationId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            var response = await apiClient.patch(url, updates, { headerInfo });

            return response.data;
        };

        return await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteServiceAccount = async (organizationId, apiKeyId, headerInfo) => {
    try {
        const url = `https://cloud.mongodb.com/api/atlas/v1.0/orgs/${organizationId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            await apiClient.delete(url, { headerInfo });
        };

        await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.addWhitelistEntry = async (organizationId, apiKeyId, whitelistEntry, headerInfo) => {
    try {
        const url = `https://cloud.mongodb.com/api/atlas/v1.0/orgs/${organizationId}/apiKeys/${apiKeyId}`;

        var operation = async () => {
            var response = await apiClient.post(url, { ipddress: whitelistEntry}, { headerInfo });

            return response.data;
        };

        await retryOperation(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}