const { CosmosDBManagementClient } = require("@azure/arm-cosmosdb");
const { DefaultAzureCredential } = require("@azure/identity");

exports.CreateOrUpdateCosmosDBAccount = async (resourceGroupName, accountName, cosmosDbParams, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const cosmosClient = new CosmosDBManagementClient(credential, subscriptionId);

        const cosmosDbResponse = await cosmosClient.databaseAccounts.beginCreateOrUpdateAndWait(
            resourceGroupName,
            accountName,
            cosmosDbParams
        );

        return cosmosDbResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteCosmosDBAccount = async (resourceGroupName, accountName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const cosmosClient = new CosmosDBManagementClient(credential, subscriptionId);

        await cosmosClient.databaseAccounts.beginDeleteAndWait(resourceGroupName, accountName);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.GetCosmosDBAccount = async (resourceGroupName, accountName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const cosmosClient = new CosmosDBManagementClient(credential, subscriptionId);

        var cosmosDbAccount = await cosmosClient.databaseAccounts.get(resourceGroupName, accountName);

        return cosmosDbAccount;
    } catch (error) {
        throw new Error(error.message);
    }
};