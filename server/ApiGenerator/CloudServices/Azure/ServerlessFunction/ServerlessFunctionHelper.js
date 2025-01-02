const { DefaultAzureCredential } = require('@azure/identity');
const { WebSiteManagementClient } = require('@azure/arm-appservice');
const { StorageManagementClient } = require('@azure/arm-storage');
const fs = require('fs');
const { BlobServiceClient } = require('@azure/storage-blob');

const parsePublishProfile = (publishProfileXml) => {
    const parseString = require('xml2js').parseString;
    let credentials = {};

    parseString(publishProfileXml, (err, result) => {
        if (err) throw err;

        const profile = result.publishData.publishProfile.find(p => p.$.publishMethod === 'MSDeploy');
        if (!profile) throw new Error('Publish profile for MSDeploy not found.');

        credentials = {
            publishingUsername: profile.$.userName,
            publishingPassword: profile.$.userPWD,
            publishingUrl: profile.$.publishUrl,
        };
    });

    return credentials;
};

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

exports.uploadFunctionCode = async (storageAccountName, containerName, zipFilePath, blobName) => {
    try {
        const blobServiceClient = BlobServiceClient.fromConnectionString(`DefaultEndpointsProtocol=https;AccountName=${storageAccountName}`);
        const containerClient = blobServiceClient.getContainerClient(containerName);

        await containerClient.createIfNotExists();

        const blockBlobClient = containerClient.getBlockBlobClient(blobName);
        const zipFileData = fs.readFileSync(zipFilePath);

        var response = await blockBlobClient.uploadData(zipFileData);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getAzureFunctionInfo = async (functionAppName, resourceGroupName, functionName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        const functionInfo = await client.webApps.getFunction(resourceGroupName, functionAppName, functionName);

        return functionInfo;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getListOfFunctions = async (functionAppName, resourceGroupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        const functionsList = await client.webApps.listFunctions(resourceGroupName, functionAppName);

        return functionsList;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateFunction = async (subscriptionId, zipFilePath, resourceGroupName, functionAppName) => {
    try {
        const credential = new DefaultAzureCredential();
        const webSiteClient = new WebSiteManagementClient(credential, subscriptionId);

        const publishProfiles = await webSiteClient.webApps.listPublishingProfiles(
            resourceGroupName,
            functionAppName,
            { format: 'WebDeploy' }
        );

        if (!publishProfiles || publishProfiles.length === 0) {
            throw new Error('Unable to retrieve publish profiles.');
        }

        const publishProfile = publishProfiles[0];
        const { publishingUsername, publishingPassword, publishingUrl } = parsePublishProfile(publishProfile);

        const zipFile = fs.readFileSync(zipFilePath);

        const kuduDeployUrl = `${publishingUrl}/api/zipdeploy`;
        const response = await axios.post(kuduDeployUrl, zipFile, {
            headers: {
                'Content-Type': 'application/zip',
            },
            auth: {
                username: publishingUsername,
                password: publishingPassword,
            },
        });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteFunctionApp = async (functionAppName, resourceGroupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        await client.webApps.delete(resourceGroupName, functionAppName);

        return;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.scaleFunctionApp = async (servicePlanName, skuConfig) => {
   try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        const response = await client.appServicePlans.createOrUpdate(resourceGroupName, servicePlanName, skuConfig);

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateFunctionAppSettings = async (resourceGroupName, functionAppName, appSettings) => {
    try {
        const credential = new DefaultAzureCredential();
        const webSiteClient = new WebSiteManagementClient(credential, subscriptionId);

        const result = await webSiteClient.webApps.updateApplicationSettings(
            resourceGroupName,
            functionAppName,
            appSettings
        );

        return result;
    } catch (error) {
        throw new Error(error.message);
    }
};