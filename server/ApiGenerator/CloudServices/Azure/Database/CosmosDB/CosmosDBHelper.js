const { CosmosDBManagementClient } = require("@azure/arm-cosmosdb");
const { DefaultAzureCredential } = require("@azure/identity");
const { CosmosClient } = require('@azure/cosmos');
const { uploadFile, downloadFile } = require('../../../../../aws-helper');

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

exports.ListCosmosDBAccounts = async (subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const cosmosClient = new CosmosDBManagementClient(credential, subscriptionId);

        const accounts = await cosmosClient.databaseAccounts.list();
        return accounts;
    } catch (error) {
        throw new Error(`Error listing Cosmos DB accounts: ${error.message}`);
    }
};

exports.CreateCosmosDBInstance = async (databaseName, accountEndpoint, accountKey) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        var databaseResponse = await client.databases.createIfNotExists({ id: databaseName });

        return databaseResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.ListDatabases = async (accountEndpoint, accountKey) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const { resources: databases } = await client.databases.readAll().fetchAll();

        return databases;
    } catch (error) {
        throw new Error(`Error listing databases: ${error.message}`);
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

exports.GetDatabaseInfo = async (accountEndpoint, accountKey, databaseName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);

        const databaseResponse = await database.read();

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.GetContainerInfo = async (accountEndpoint, accountKey, databaseName, containerName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);
        const container = database.container(containerName);

        const containerResponse = await container.read();

        return containerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.ListContainers = async (accountEndpoint, accountKey, databaseName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const database = client.database(databaseName);

        const { resources: containers } = await database.containers.readAll().fetchAll();

        return containers;
    } catch (error) {
        throw new Error(`Error listing containers: ${error.message}`);
    }
};

exports.UpdateDatabaseThroughput = async (accountEndpoint, accountKey, databaseName, throughput) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);

        const response = await database.replaceThroughput({ throughput });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.UpdateContainerThroughput = async (accountEndpoint, accountKey, databaseName, containerName, throughput) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);
        const container = database.container(containerName);

        const response = await container.replaceThroughput({ throughput });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.UpdateOtherContainerSettings = async (accountEndpoint, accountKey, databaseName, containerName, containerInfo) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);
        const container = database.container(containerName);

        const response = await container.replace(containerInfo);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getDbInstanceKeys = async (resourceGroupName, accountName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const cosmosClient = new CosmosDBManagementClient(credential, subscriptionId);

        const keys = await cosmosClient.databaseAccounts.listKeys(resourceGroupName, accountName);

        return keys;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.GenerateSASToken = async (accountEndpoint, accountKey, databaseName, containerName, permissionInfo) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const permissionDefinition = {
            id: `perm-${containerName}`,
            permissionMode: permissionInfo.permissionMode,
            resource: resourcePath,
            resourcePartitionKey: permissionInfo.resourcePartitionKey || []
        };

        const { resource: permissionResource } = await client.database(databaseName).user(`user-${containerName}`).permissions.createIfNotExists(permissionDefinition);

        const sasToken = permissionResource._token;

        return sasToken;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.CreateCosmosDBUser = async (accountEndpoint, accountKey, databaseName, containerName) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const database = client.database(databaseName);

        const { resource: user } = await database.users.createIfNotExists({ id: `user-${containerName}` });

        return user;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.GetUser = async (accountEndpoint, accountKey, databaseName, userId) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);

        const user = database.user(userId);
        const { resource: userDetails } = await user.read();

        return userDetails;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteCosmosDBUser = async (accountEndpoint, accountKey, databaseName, userId) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });
        const database = client.database(databaseName);

        const user = database.user(userId);
        await user.delete();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.CreatePermission = async (accountEndpoint, accountKey, databaseName, userId, permissionInfo) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const database = client.database(databaseName);

        const user = database.user(userId);

        const { resource: permission } = await user.permissions.create(permissionInfo);

        return permission;
    } catch (error) {
        throw new Error(`Error creating permission: ${error.message}`);
    }
};

exports.DeletePermission = async (accountEndpoint, accountKey, databaseName, userId, permissionId) => {
    try {
        const client = new CosmosClient({ endpoint: accountEndpoint, key: accountKey });

        const database = client.database(databaseName);

        const user = database.user(userId);
        
        await user.permission(permissionId).delete();
    } catch (error) {
        throw new Error(`Error deleting permission: ${error.message}`);
    }
};

exports.BackupData = async (databaseId, containerId, bucketName, outputFileName) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const query = {
            query: 'SELECT * FROM c'
        };

        const { resources: items } = await container.items.query(query).fetchAll();

        const fileContent = JSON.stringify(items, null, 2);

        fs.writeFileSync(outputFileName, fileContent);

        await uploadFile(bucketName, outputFilename, fileContent);
    } catch (error) {
        throw new Error(`Error backing up data: ${error.message}`);
    }
};

exports.RestoreBackup = async (databaseId, containerId, bucketName, objectKey) => {
    try {
        const container = client.database(databaseId).container(containerId);

        var fileContent = await downloadFile(bucketName, objectKey);

        const items = JSON.parse(fileContent);

        for (const item of items) {
            await container.items.create(item);
        }
    } catch (error) {
        throw new Error(`Error backing up data: ${error.message}`);
    }
}

//Database operations

exports.InsertDocument = async (databaseId, containerId, documentInfo) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const { resource: createdItem } = await container.items.create(documentInfo);

        return createdItem;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.QueryDocuments = async (databaseId, containerId, queryValue, params) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const query = {
            query: queryValue,
            parameters: params
        };

        const { resources: results } = await container.items.query(query).fetchAll();

        return results;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.UpdateDocument = async (databaseId, containerId, documentId, partitionKey, attribute, newValue) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const { resource: document } = await container.item(documentId, partitionKey).read();

        document[attribute] = newValue;

        const { resource: updatedDocument } = await container.item(documentId, partitionKey).replace(document);

        return updatedDocument;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteDocument = async (databaseId, containerId, documentId, partitionKey) => {
    try {
        const container = client.database(databaseId).container(containerId);

        await container.item(documentId, partitionKey).delete();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.AggregateQuery = async (databaseId, containerId, aggregateQuery) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const { resources: results } = await container.items.query(aggregateQuery).fetchAll();

        return results;
    } catch (error) {
        throw new Error(`Error executing aggregate query: ${error.message}`);
    }
};

exports.BulkUpsertDocuments = async (databaseId, containerId, documents) => {
    try {
        const container = client.database(databaseId).container(containerId);

        const operations = documents.map(doc => ({ operationType: "Upsert", resourceBody: doc }));

        const { resources: results } = await container.items.bulk(operations);
        
        return results;
    } catch (error) {
        throw new Error(`Error performing bulk upsert: ${error.message}`);
    }
};

exports.BulkDeleteDocuments = async (databaseName, containerName, query) => {
    try {
        const database = cosmosClient.database(databaseName);
        const container = database.container(containerName);

        const { resources: items } = await container.items.query(query).fetchAll();

        if (items.length === 0) {
            return;
        }

        const deletePromises = items.map((item) => {
            return container.item(item.id, item.partitionKey).delete();
        });

        await Promise.all(deletePromises);
    } catch (error) {
        throw new Error(`Error performing bulk delete: ${error.message}`);
    }
};