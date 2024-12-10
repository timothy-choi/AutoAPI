const { DefaultAzureCredential } = require('@azure/identity');
const { MySQLManagementClient } = require('@azure/arm-mysql');

exports.createMySQLServer = async (SqlServerInfo, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

    var mySqlServerResponse = await mySqlClient.servers.beginCreateAndWait(SqlServerInfo.resourceGroupName, SqlServerInfo.serverName, SqlServerInfo.requestInfo);

    return mySqlServerResponse;
};

exports.deleteMySQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const mySqlClient = new MySQLManagementClient(credential, subscriptionId);

    await mySqlClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
}