const { CloudFunctionsServiceClient } = require('@google-cloud/functions');
const { Storage } = require('@google-cloud/storage');
const { exec } = require('child_process');

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

        exec(deployCommand, (error, stdout, stderr) => {
            if (error) {
                throw new Error(`Error deploying function: ${error.message}`);
            } if (stderr) {
                throw new Error(`Deployment error: ${stderr}`);
            }
        });
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteServerlessFunction = async (functionName, projectId, region) => {
    try {
        const client = new CloudFunctionsServiceClient();

        const [response] = await client.deleteFunction({
            name: `projects/${projectId}/locations/${region}/functions/${functionName}`,
        });

        return response;
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