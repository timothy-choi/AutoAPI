const { ApiKeysClient } = require("@google-cloud/apikeys");

exports.createApiKey = async (projectId, displayName, restrictions) => {
    const apiKeysClient = new ApiKeysClient();

    const [apiKey] = await apiKeysClient.createKey({
        parent: `projects/${projectId}/locations/global`,
        key: {
            displayName: displayName,
            restrictions: restrictions
        }
    });
};

exports.deleteApiKey = async (apiKeyId) => {
    const apiKeysClient = new ApiKeysClient();

    await apiKeysClient.deleteKey({
        name: apiKeyId
    });
};