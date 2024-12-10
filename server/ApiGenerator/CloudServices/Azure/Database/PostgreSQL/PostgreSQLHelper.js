const { DefaultAzureCredential } = require('@azure/identity');
const { PostgreSQLManagementClient } = require('@azure/arm-postgresql');

exports.createPostgreSQLServer = async (PostgreSQLInfo, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

    var postgreServerResponse = await postgreClient.servers.beginCreateAndWait(PostgreSQLInfo.resourceGroupName, PostgreSQLInfo.serverName, PostgreSQLInfo.requestInfo);

    return postgreServerResponse;
};

exports.deletePostgreSQLServer = async (resourceGroupName, serverName, subscriptionId) => {
    const credential = new DefaultAzureCredential();
    
    const postgreClient = new PostgreSQLManagementClient(credential, subscriptionId);

    await postgreClient.servers.beginDeleteAndWait(resourceGroupName, serverName);
}