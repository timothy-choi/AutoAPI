const { DefaultAzureCredential } = require("@azure/identity");
const { ApiManagementClient } = require("@azure/apimanagement");

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