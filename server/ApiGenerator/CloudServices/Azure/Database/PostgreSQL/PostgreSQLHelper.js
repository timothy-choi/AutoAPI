const { DefaultAzureCredential } = require('@azure/identity');
const { PostgreSQLManagementClient } = require('@azure/arm-postgresql');

exports.createPostgreSQLServer = async (PostgreSQLInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

        var postgreServerResponse = await postgreClient.servers.beginCreateAndWait(PostgreSQLInfo.resourceGroupName, PostgreSQLInfo.serverName, PostgreSQLInfo.requestInfo);

        return postgreServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deletePostgreSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgreClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getPostgreSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreSqlClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const serverDetails = await postgreSqlClient.servers.get(resourceGroupName, serverName);

        return serverDetails;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createPostgreSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreSqlClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgreSqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.requestInfo).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.deletePostgreSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreSqlClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgreSqlClient.databases.beginDeleteAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}