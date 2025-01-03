const { DefaultAzureCredential } = require('@azure/identity');
const { WebSiteManagementClient } = require('@azure/arm-appservice');
const { StorageManagementClient } = require('@azure/arm-storage');
const fs = require('fs');
const { BlobServiceClient } = require('@azure/storage-blob');

MAX_RETRIES = 3;

const retryOperation = async (operation) => {
    let attempts = 0;
    while (attempts < MAX_RETRIES) {
        try {
            return await operation();
        } catch (error) {
            attempts++;
            if (attempts >= MAX_RETRIES) throw error;
            await wait(RETRY_DELAY_MS);
        }
    }
};

const pollOperation = async (checkCondition, timeout = POLLING_TIMEOUT_MS) => {
    const start = Date.now();
    while (Date.now() - start < timeout) {
        const result = await checkCondition();
        if (result) return result;
        await wait(POLLING_INTERVAL_MS);
    }
    throw new Error('Polling operation timed out.');
};

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

        var operation = async () => {
            const storageAccountResponse = await webSiteClient.storageAccounts.createOrUpdate(resourceGroupName, storageAccountName, storageConfig);

            return storageAccountResponse;
        };

        return await retryOperation(operation);
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

        var operation = async () => {
            await containerClient.createIfNotExists();
        };

        await retryOperation(operation);

        const blockBlobClient = containerClient.getBlockBlobClient(blobName);
        const zipFileData = fs.readFileSync(zipFilePath);
        
        var operation = async () => {
            var response = await blockBlobClient.uploadData(zipFileData);

            return response;
        };

        return await retryOperation(operation);
    } catch (error) {
        throw new Error(error.message);
    } finally {
        fs.unlinkSync(zipFilePath);
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

        var operation = async () => {
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
        };

        return await retryOperation(operation);
    } catch (error) {
        throw new Error(error.message);
    } finally {
        fs.unlinkSync(zipFilePath);
    }
};

exports.deleteFunctionApp = async (functionAppName, resourceGroupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        var operation = async () => await client.webApps.delete(resourceGroupName, functionAppName);

        await retryOperation(operation);

        return await pollOperation(async () => {
            const apps = await client.webApps.listByResourceGroup(resourceGroupName);
            return !apps.some(app => app.name === functionAppName);
        });
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.scaleFunctionApp = async (subscriptionId, servicePlanName, skuConfig) => {
   try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        var operation = async () => {
            const response = await client.appServicePlans.createOrUpdate(resourceGroupName, servicePlanName, skuConfig);

            return response;
        };

        var res = await retryOperation(operation);

        await pollOperation(async () => {
            const plan = await client.appServicePlans.get(resourceGroupName, servicePlanName);
            return plan.sku && plan.sku.capacity === skuConfig.capacity;
        });

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateFunctionAppSettings = async (subscriptionId, resourceGroupName, functionAppName, appSettings) => {
    try {
        const credential = new DefaultAzureCredential();
        const webSiteClient = new WebSiteManagementClient(credential, subscriptionId);

        var operation = async () => {
            const result = await webSiteClient.webApps.updateApplicationSettings(
                resourceGroupName,
                functionAppName,
                appSettings
            );

            return result;
        };

        var res = await retryOperation(operation);

        await pollOperation(async () => {
            const updatedSettings = await webSiteClient.webApps.listApplicationSettings(resourceGroupName, functionAppName);
            return Object.keys(appSettings.properties).every(key => updatedSettings.properties[key] === appSettings.properties[key]);
        });

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.backupFunctionApp = async (resourceGroupName, functionAppName, storageAccountUrl, backupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        var operation = async () => {
            const backupResponse = await client.webApps.beginBackupAndWait(resourceGroupName, functionAppName, {
                storageAccountUrl,
                backupName,
            });

            return backupResponse;
        };

        var res = await retryOperation(operation);

        await pollOperation(async () => {
            const backups = await client.webApps.listBackups(resourceGroupName, functionAppName);
            return backups.some(backup => backup.name === backupName);
        });

        return res;
    } catch (error) {
        throw new Error(`Error backing up function app: ${error.message}`);
    }
};

exports.restoreFunctionApp = async (resourceGroupName, functionAppName, storageAccountUrl, backupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const client = new WebSiteManagementClient(credential, subscriptionId);

        var operation = async () => {
            const restoreResponse = await client.webApps.beginRestoreAndWait(resourceGroupName, functionAppName, {
                storageAccountUrl,
                backupName,
            });

            return restoreResponse;
        };

        var res = await retryOperation(operation);

        await pollOperation(async () => {
            const backups = await client.webApps.listBackups(resourceGroupName, functionAppName);
            return backups.some(backup => backup.name === backupName);
        });

        return res;
    } catch (error) {
        throw new Error(`Error restoring function app: ${error.message}`);
    }
};

exports.deleteCascadeFunctionApp = async (functionAppName, resourceGroupName, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const appServiceClient = new WebSiteManagementClient(credential, subscriptionId);
        const storageClient = new StorageManagementClient(credential, subscriptionId);

        var operation = async () => { await appServiceClient.webApps.delete(resourceGroupName, functionAppName); };

        await retryOperation(operation);

        await pollOperation(async () => {
            const apps = await appServiceClient.webApps.listByResourceGroup(resourceGroupName);
            return !apps.some(app => app.name === functionAppName);
        });

        const servicePlanName = `${functionAppName}-plan`;
       
        var operation = async () => { await appServiceClient.appServicePlans.delete(resourceGroupName, servicePlanName); };

        await retryOperation(operation);

        await pollOperation(async () => {
            const plans = await appServiceClient.appServicePlans.listByResourceGroup(resourceGroupName);
            return !plans.some(plan => plan.name === servicePlanName);
        });

        const storageAccounts = await storageClient.storageAccounts.listByResourceGroup(resourceGroupName);
        const storageAccount = storageAccounts.find(account =>
            account.name.includes(functionAppName)
        );

        if (storageAccount) {
            var operation = async () => { await storageClient.storageAccounts.deleteMethod(resourceGroupName, storageAccount.name); };

            await retryOperation(operation);

            await pollOperation(async () => {
                const accounts = await storageClient.storageAccounts.listByResourceGroup(resourceGroupName);
                return !accounts.some(account => account.name === storageAccount.name);
            });
        }
    } catch (error) {
        throw new Error(`Error during cascade delete: ${error.message}`);
    }
};