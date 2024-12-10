require('dotenv').config();
const { DefaultAzureCredential } = require('@azure/identity');
const { SqlManagementClient } = require('@azure/arm-sql');

exports.createSQLServer = async (SqlServerInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        var sqlServerResponse = await sqlClient.servers.beginCreateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

        return sqlServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        await sqlClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        var sqlServerResponse = await sqlClient.servers.get(resourceGroupName, serverName);

        return sqlServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        await sqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.requestInfo).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.deleteSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        await sqlClient.databases.beginDeleteAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}