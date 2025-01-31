const { DefaultAzureCredential } = require("@azure/identity");
const { ApiManagementClient } = require("@azure/apimanagement");
const { PublicClientApplication } = require("@azure/msal-node");
const { GraphClient } = require("@microsoft/microsoft-graph-client");


exports.createAzureApiKey = async (userId, productId, resourceGroup, serviceName, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);

    const response = await client.subscription.createOrUpdate(
        resourceGroup,
        serviceName,
        `sub-${userId}`,
        {
            displayName: `API Key for ${userId}`,
            scope: `/products/${productId}`,  
            state: "active"
        }
    );
    return response.primaryKey;
};

exports.deleteAzureApiKey = async (resourceGroup, serviceName, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);

    await client.subscription.deleteMethod(resourceGroup, serviceName, subscriptionId);
};

exports.createAzureJwtToken = async (scope, username, password, clientId, tenantId) => {
    const msalConfig = {
        auth: {
            clientId: clientId,
            authority: `https://login.microsoftonline.com/${tenantId}`,
        }
    };

    const pca = new PublicClientApplication(msalConfig);

    const authParams = {
        scopes: [scope], 
        username: username, 
        password: password 
    };

    const response = await pca.acquireTokenByUsernamePassword(authParams);

    return response.accessToken;
};

exports.revokeRefreshToken = async (userId) => {
    const credential = new DefaultAzureCredential();

    const graphClient = GraphClient.initWithMiddleware({ authProvider: credential });

    await graphClient.api(`/users/${userId}/revokeSignInSessions`).post();
};