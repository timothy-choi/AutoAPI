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

exports.updateMySQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const updatedServer = await mySqlClient.servers.beginUpdateAndWait(serverInfo.resourceGroupName, serverInfo.serverName, serverInfo.updateParams);

        return updatedServer;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createOrUpdateFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const firewallRule = await mySqlClient.firewallRules.beginCreateOrUpdateAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName, firewallInfo.parameters);

        return firewallRule;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.removeFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        await mySqlClient.firewallRules.beginDeleteAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.restoreBackup = async (restoreInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const response = await mySqlClient.servers.beginRestoreAndWait(restoreInfo.resourceGroupName, restoreInfo.serverName, restoreInfo.parameters);

        return response;
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

exports.updateMySQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        var result = await mySqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.updatedParameters).promise();

        return result;
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