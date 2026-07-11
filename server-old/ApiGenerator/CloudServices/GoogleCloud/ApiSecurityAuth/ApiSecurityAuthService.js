const { ApiKeysClient } = require("@google-cloud/apikeys");
const admin = require("firebase-admin");

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

exports.createJwtToken = async (uid) => {
    var token = admin.auth().createCustomToken(uid);

    return token;
};

exports.deleteJwtToken = async (uid) => {
    admin.auth().deleteUser(uid);
}