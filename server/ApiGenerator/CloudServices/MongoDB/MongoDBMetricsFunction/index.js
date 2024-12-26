const axios = require('axios');
const aws = require('aws-sdk');

const getCredentials = async (secretName, region) => {
    try {
        const secretsManager = new AWS.SecretsManager({ region });

        const secret = await secretsManager.getSecretValue({ SecretId: secretName }).promise();

        if (secret.SecretString) {
            return JSON.parse(secret.SecretString);
        } else {
            const buff = Buffer.from(secret.SecretBinary, 'base64');
            return JSON.parse(buff.toString('ascii'));
        }
    } catch (error) {
        throw new Error(error.message);
    }
};

const fetchMetrics = async (clusterName, username, password, projectId) => {
    const authHeader = Buffer.from(`${username}:${password}`).toString('base64');
    const metricsUrl = `https://cloud.mongodb.com/api/atlas/v1.0/groups/${projectId}/clusters/${clusterName}/processes`;
  
    const response = await axios.get(metricsUrl, {
      headers: {
        'Authorization': `Basic ${authHeader}`,
        'Content-Type': 'application/json',
      },
    });
  
    return response.data;
  }
  

exports.handler = async (event) => {
    try {
        var credentials = await getCredentials(event.secretName, event.region);

        var metricsData = await fetchMetrics(event.clusterName, credentials.username, credentials.password, event.projectId);

        return {
            status: 200,
            body: JSON.stringify(metricsData), 
        };
    } catch (error) {
        return {
            status: 500,
            message: "Metrics Monitoring failed"
        };
    }
};