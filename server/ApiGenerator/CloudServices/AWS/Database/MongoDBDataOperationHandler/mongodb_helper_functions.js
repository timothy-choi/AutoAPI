const { MongoClient } = require('mongodb');
const AWS = require('aws-sdk');

exports.getCredentials = async (secretName, region) => {
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

