const { DefaultAzureCredential } = require('@azure/identity');
const { MySQLManagementClient } = require('@azure/arm-mysql');

exports.createMySQLServer = async (SqlServerInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        var mySqlServerResponse = await mySqlClient.servers.beginCreateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

        return mySqlServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteMySQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        await mySqlClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getMySQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const serverDetails = await mySqlClient.servers.get(resourceGroupName, serverName);

        return serverDetails;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createMySQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        await mySqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.requestInfo).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.deleteMySQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);
        
        await mySqlClient.databases.beginDeleteAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}