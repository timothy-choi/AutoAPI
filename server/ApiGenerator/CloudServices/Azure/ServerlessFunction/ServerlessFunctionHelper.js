const { DefaultAzureCredential } = require('@azure/identity');
const { WebSiteManagementClient } = require('@azure/arm-appservice');
const { StorageManagementClient } = require('@azure/arm-storage');

exports.createStorageAccount = async (storageAccountName, storageConfig, resourceGroupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const webSiteClient = new StorageManagementClient(credential, subscriptionId);

        const storageAccountResponse = await webSiteClient.storageAccounts.createOrUpdate(resourceGroupName, storageAccountName, storageConfig);

        return storageAccountResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createFunctionApp = async (functionAppName, storageAccountName, resourceGroupName, appServicePlanConfig, location, subscriptionId) => {
    try {
        const appServicePlanName = `${functionAppName}-plan`;

        const credential = new DefaultAzureCredential();

        const client = new WebSiteManagementClient(credential, subscriptionId);

        await client.appServicePlans.beginCreateOrUpdateAndWait(resourceGroupName, appServicePlanName, appServicePlanConfig);

        var response = await client.webApps.beginCreateOrUpdateAndWait(resourceGroupName, functionAppName, {
            location: location,
            serverFarmId: `/subscriptions/${subscriptionId}/resourceGroups/${resourceGroupName}/providers/Microsoft.Web/serverfarms/${appServicePlanName}`,
            siteConfig: {
                appSettings: [
                    { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' },
                    { name: 'WEBSITE_RUN_FROM_PACKAGE', value: '1' },
                    { name: 'AzureWebJobsStorage', value: `DefaultEndpointsProtocol=https;AccountName=${storageAccountName}` },
                ],
            },
            httpsOnly: true,
        });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};
