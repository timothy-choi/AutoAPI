const { CloudFunctionsServiceClient } = require('@google-cloud/functions');
const { Storage } = require('@google-cloud/storage');
const { exec } = require('child_process');
const axios = require('axios');

const retryOperation = async (operation, retries = 3, delay = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation();
        } catch (error) {
            if (attempt === retries - 1) {
                throw new Error(`Operation failed after ${retries} attempts: ${error.message}`);
            }
            attempt++;
            console.log(`Retrying operation... Attempt ${attempt + 1}`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
};

const pollFunction = async (operationFn, checkStatusFn, maxAttempts = 10, delay = 5000) => {
    let attempt = 0;
    while (attempt < maxAttempts) {
        try {
            const status = await checkStatusFn();
            if (status === 'SUCCESS' || status === 'DONE') {
                return { success: true, message: 'Operation completed successfully' };
            } else if (status === 'FAILURE') {
                throw new Error('Operation failed');
            }

            console.log(`Polling attempt ${attempt + 1}: Status - ${status}`);
            attempt++;
            await new Promise(resolve => setTimeout(resolve, delay));
        } catch (error) {
            if (attempt === maxAttempts - 1) {
                throw new Error(`Polling failed after ${maxAttempts} attempts: ${error.message}`);
            }
            console.log(`Polling attempt ${attempt + 1} failed: ${error.message}`);
            attempt++;
        }
    }

    throw new Error(`Polling timed out after ${maxAttempts} attempts`);
};

exports.DeployServerlessFunction = async (functionName, entryPoint, runtime, sourcePath, region, projectId) => {
    try {
        const deployCommand = `
            gcloud functions deploy ${functionName} \
            --entry-point=${entryPoint} \
            --runtime=${runtime} \
            --trigger-http \
            --allow-unauthenticated \
            --region=${region} \
            --source=${sourcePath} \
            --project=${projectId}
        `;

        await retryOperation(() => new Promise((resolve, reject) => {
            exec(deployCommand, (error, stdout, stderr) => {
                if (error) {
                    throw new Error(`Error deploying function: ${error.message}`);
                } else if (stderr) {
                    throw new Error(`Deployment error: ${stderr}`);
                } 
            });
        }));

        const checkStatusFn = async () => {
            const functionDetails = await exports.GetServerlessFunction(functionName, projectId, region);
            return functionDetails.status; 
        };

        await pollFunction(null, checkStatusFn, 20, 3000);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteServerlessFunction = async (functionName, projectId, region) => {
    try {
        const client = new CloudFunctionsServiceClient();

        var operation = async () => {
            const [response] = await client.deleteFunction({
                name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
            });

            return response;
        };

        var res = await retryOperation(operation);

        const checkStatusFn = async () => {
            try {
                await client.getFunction({
                    name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
                });
                return 'PENDING'; 
            } catch (error) {
                if (error.code === 5) {
                    return 'DONE'; 
                }
                throw new Error(error.message); 
            }
        };

        await pollFunction(null, checkStatusFn, 20, 3000);

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.GetServerlessFunction = async (functionName, projectId, region) => {
    try {
        const client = new CloudFunctionsServiceClient();

        const [response] = await client.getFunction({
            name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
        });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.listServerlessFunctions = async (projectId, region) => {
    try {
        const client = new CloudFunctionsServiceClient();

        const [response] = await client.listFunctions({
            parent: `projects/${projectId}/locations/${region}`,
        });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.UpdateServerlessFunction = async (functionName, projectId, region, updatedConfig) => {
    try {
        const client = new CloudFunctionsServiceClient();

        var operation = async () => {
            const [response] = await client.updateFunction({
                name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
                ...updatedConfig,
            });

            return response;
        };

        var res = await retryOperation(operation);

        const checkStatusFn = async () => {
            const functionDetails = await client.getFunction({
                name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
            });

            return functionDetails.status; 
        };

        await pollFunction(null, checkStatusFn, 20, 3000);

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.CloneServerlessFunction = async (sourceFunctionName, targetFunctionName, projectId, region) => {
    try {
        const client = new CloudFunctionsServiceClient();

        const [sourceFunction] = await client.getFunction({
            name: `projects/${projectId}/locations/${region}/functions/${sourceFunctionName}`,
        });

        var operation = async () => {
            const [response] = await client.createFunction({
                location: `projects/${projectId}/locations/${region}`,
                function: {
                    ...sourceFunction,
                    name: targetFunctionName, 
                },
            });

            return response;
        };

        var res = await retryOperation(operation);

        const checkStatusFn = async () => {
            const functionDetails = await client.getFunction({
                name: `projects/${projectId}/locations/${region}/functions/${targetFunctionName}`,
            });

            return functionDetails.status; 
        };

        await pollFunction(null, checkStatusFn, 20, 3000);

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.RollbackFunction = async (functionName, projectId, region, versionId) => {
    try {
        const client = new CloudFunctionsServiceClient();

        var operations = async () => {
            const [response] = await client.rollbackFunction({
                name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
                versionId,
            });

            return response;
        };

        var res = await retryOperation(operations);

        const checkStatusFn = async () => {
            const functionDetails = await client.getFunction({
                name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
            });

            return functionDetails.status; 
        };

        await pollFunction(null, checkStatusFn, 20, 3000);

        return res;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.BackupFunctionConfiguration = async (functionName, projectId, region, bucketName, fileName) => {
    try {
        const client = new CloudFunctionsServiceClient();
        const storage = new Storage();

        const [functionDetails] = await client.getFunction({
            name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
        });

        const file = storage.bucket(bucketName).file(fileName);

        var operation  = async () => { await file.save(JSON.stringify(functionDetails, null, 2)); };

        await retryOperation(operation);

        const checkStatusFn = async () => {
            const [metadata] = await file.getMetadata();
            return metadata;
        };

        await pollFunction(null, checkStatusFn, 20, 3000);
    } catch (error) {
        throw new Error(`Error backing up function configuration: ${error.message}`);
    }
};

exports.RestoreFunctionConfiguration = async (bucketName, fileName, projectId, region) => {
    try {
        const storage = new Storage();
        const client = new CloudFunctionsServiceClient();

        const file = storage.bucket(bucketName).file(fileName);

        var operation = async () => { 
            const [configData] = await file.download(); 

            return configData;
        };

        const configData = await retryOperation(operation);

        const functionConfig = JSON.parse(configData);
        const { name, ...config } = functionConfig;

        var operation = async () => {
            const [response] = await client.updateFunction({
                name: `projects/${projectId}/locations/${region}/functions/${name}`,
                ...config,
            });

            return response;
        };

        var res = await retryOperation(operation);

        const checkStatusFn = async () => {
            const functionDetails = await client.getFunction({
                name: `projects/${projectId}/locations/${region}/functions/${name}`,
            });

            return functionDetails.status; 
        };

        await pollFunction(null, checkStatusFn, 20, 3000);

        return res;
    } catch (error) {
        throw new Error(`Error restoring function configuration: ${error.message}`);
    }
};

exports.getServerlessFunctionMetrics = async (httpFunctionUri, accessToken, refreshToken, projectName, minutes, metricTypes) => {
    try {
        var metrics = await axios.post(httpFunctionUri, {
            accessToken,
            refreshToken,
            projectName,
            minutes,
            metricTypes
        }, {
            headers: {
                Authorization: `Bearer ${accessToken}`,
                'Content-Type': 'application/json',
            },
        });

        return metrics.metricResults;
    } catch (error) {
        throw new Error(error.message);
    }
};