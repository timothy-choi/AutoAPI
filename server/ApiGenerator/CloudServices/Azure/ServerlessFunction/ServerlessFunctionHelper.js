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