const { DefaultAzureCredential } = require('@azure/identity');
const { MySQLManagementClient } = require('@azure/arm-mysql');

exports.createMySQLServer = async (SqlServerInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        var mySqlServerResponse = await mySqlClient.flexibleServers.beginCreateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

        return mySqlServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteMySQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        await mySqlClient.flexibleServers.beginDeleteAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getMySQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const serverDetails = await mySqlClient.flexibleServers.get(resourceGroupName, serverName);

        return serverDetails;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.ListMySQLServers = async (subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const servers = await mySqlClient.servers.list();

        return servers;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.updateMySQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const updatedServer = await mySqlClient.flexibleServers.beginUpdateAndWait(serverInfo.resourceGroupName, serverInfo.serverName, serverInfo.updateParams);

        return updatedServer;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.startMySQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const response = await mySqlClient.flexibleServers.beginStartAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.stopMySQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const response = await mySqlClient.flexibleServers.beginStopAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.rebootMySQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const response = await mySqlClient.flexibleServers.beginRestartAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
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

        const response = await mySqlClient.flexibleServers.beginRestoreAndWait(restoreInfo.resourceGroupName, restoreInfo.serverName, restoreInfo.parameters);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.failoverFlexibleServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const mysqlClient = new MySQLManagementClient(credential, subscriptionId);

        await mysqlClient.flexibleServers.beginFailoverAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createMySQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        var databaseResponse = await mySqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.requestInfo).promise();

        return databaseResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getMySQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

        const databaseDetails = await mySqlClient.databases.get(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName);

        return databaseDetails;
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