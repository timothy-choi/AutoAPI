require('dotenv').config();
const { DefaultAzureCredential } = require('@azure/identity');
const { SqlManagementClient } = require('@azure/arm-sql');

exports.createSQLServer = async (SqlServerInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        var sqlServerResponse = await sqlClient.servers.beginCreateOrUpdateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

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

exports.updateSQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        const updatedServer = await sqlClient.servers.beginUpdateAndWait(serverInfo.resourceGroupName, serverInfo.serverName, serverInfo.updateParams);

        return updatedServer;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.startSQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        const response = await sqlClient.servers.beginStartAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.stopSQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        const response = await sqlClient.servers.beginStopAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.failoverAutoFailoverGroup = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        var response = await sqlClient.failoverGroups.beginFailoverAndWait(
            serverInfo.resourceGroupName,
            serverInfo.serverName,
            serverInfo.failoverGroupName
        );

        return response;
    } catch (error) {
        throw new Error(`Error triggering failover: ${error.message}`);
    }
};
 
exports.createOrUpdateFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        const firewallRule = await sqlClient.firewallRules.beginCreateOrUpdateAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName, firewallInfo.parameters);

        return firewallRule;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.removeFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        await sqlClient.firewallRules.beginDeleteAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.restoreBackup = async (restoreInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        const response = await sqlClient.servers.beginRestoreAndWait(restoreInfo.resourceGroupName, restoreInfo.serverName, restoreInfo.parameters);

        return response;
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

exports.updateSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const sqlClient = new SqlManagementClient(credential, subscriptionId);

        var result = await sqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.updatedParameters).promise();

        return result;
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