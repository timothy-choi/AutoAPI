const { CosmosDBManagementClient } = require("@azure/arm-cosmosdb");
const { DefaultAzureCredential } = require("@azure/identity");
const { CosmosClient } = require('@azure/cosmos');

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

exports.CreateCosmosDBInstance = async (databaseName, accountEndpoint, accountKey) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        await client.databases.createIfNotExists({ id: databaseName });

    } catch (error) {
        throw new Error(error.message);
    }
};

exports.CreateCosmosDBContainer = async (accountEndpoint, accountKey, databaseName, containerInfo) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);
        
        const { container } = await database.containers.create(containerInfo);

        return container;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteCosmosDBContainer = async (accountEndpoint, accountKey, databaseName, containerName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);
        const container = database.container(containerName);

        await container.delete();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteCosmosDBInstance = async (accountEndpoint, accountKey, databaseName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);

        await database.delete();
    } catch (error) {
        throw new Error(error.message);
    }
};