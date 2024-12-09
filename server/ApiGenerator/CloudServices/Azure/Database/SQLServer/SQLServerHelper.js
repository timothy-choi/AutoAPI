require('dotenv').config();
const { DefaultAzureCredential } = require('@azure/identity');
const { SqlManagementClient } = require('@azure/arm-sql');

exports.createSQLServer = async (SqlServerInfo, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const sqlClient = new SqlManagementClient(credential, subscriptionId);

    var sqlServerResponse = await sqlClient.servers.beginCreateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

    return sqlServerResponse;
};

exports.deleteSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const sqlClient = new SqlManagementClient(credential, subscriptionId);

    await sqlClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
}