const { DefaultAzureCredential } = require('@azure/identity');
const { PostgreSQLManagementClient } = require('@azure/arm-postgresql');

exports.createPostgreSQLServer = async (PostgreSQLInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

        var postgreServerResponse = await postgreClient.flexibleServers.beginCreateAndWait(PostgreSQLInfo.resourceGroupName, PostgreSQLInfo.serverName, PostgreSQLInfo.requestInfo);

        return postgreServerResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deletePostgreSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgreClient.flexibleServers.beginDeleteAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getPostgreSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreSqlClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const serverDetails = await postgreSqlClient.flexibleServers.get(resourceGroupName, serverName);

        return serverDetails;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.updatePostgreSQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const updatedServer = await postgresClient.flexibleServers.beginUpdateAndWait(serverInfo.resourceGroupName, serverInfo.serverName, serverInfo.updateParams);

        return updatedServer;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.startPostgresServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const response = await postgresClient.flexibleServers.beginStartAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.stopSQLServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const response = await postgresClient.flexibleServers.beginStopAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.rebootPostgresServer = async (serverInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const response = await postgresClient.flexibleServers.beginRestartAndWait(serverInfo.resourceGroupName, serverInfo.serverName);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.createOrUpdateFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const firewallRule = await postgresClient.firewallRules.beginCreateOrUpdateAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName, firewallInfo.parameters);

        return firewallRule;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.removeFirewallRule = async (firewallInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgresClient.firewallRules.beginDeleteAndWait(firewallInfo.resourceGroupName, firewallInfo.serverName, firewallInfo.ruleName);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.restoreBackup = async (restoreInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const response = await postgresClient.flexibleServers.beginRestoreAndWait(restoreInfo.resourceGroupName, restoreInfo.serverName, restoreInfo.parameters);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.failoverFlexibleServer = async (resourceGroupName, serverName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        await postgresClient.flexibleServers.beginFailoverAndWait(resourceGroupName, serverName);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createPostgreSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgreSqlClient = new PostgreSQLManagementClient(credential, subscriptionId);

        var databaseResponse = await postgreSqlClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.requestInfo).promise();

        return databaseResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getPostgreSQLDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        const databaseDetails = await postgresClient.databases.get(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName);

        return databaseDetails;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.updatePostgresDatabase = async (databaseInfo, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        
        const postgresClient = new PostgreSQLManagementClient(credential, subscriptionId);

        var result = await postgresClient.databases.beginCreateOrUpdateAndWait(databaseInfo.resourceGroupName, databaseInfo.serverName, databaseInfo.databaseName, databaseInfo.updatedParameters).promise();

        return result;
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