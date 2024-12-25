const { MongoClient } = require('mongodb');
const AWS = require('aws-sdk');

let mainClient = null;

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

exports.getMongoDBClient = async (connectionInfo, connectionOptions) => {
    try {
        if (mainClient && mainClient.isConnected()) {
            return mainClient;
        }

        connectionString = `mongodb+srv://${connectionInfo.username}:${connectionInfo.password}@${connectionInfo.cluster}/${connectioninfo.dbname}?retryWrites=true&w=majority`;
        
        mainClient = new MongoClient(connectionString, connectionOptions);
        await mainClient.connect();

        return mainClient;
    } catch (error) {
        throw new Error(error.message);
    }
};

const retryOperation = async (operation, maxRetries = 3, delay = 1000) => {
    let attempts = 0;
    while (attempts < maxRetries) {
        try {
            return await operation();
        } catch (error) {
            attempts++;

            if (attempts >= maxRetries) {
                throw new Error(error.message);
            }

            await new Promise(resolve => setTimeout(resolve, delay * Math.pow(2, attempts - 1)));
        }
    }
};

//database operations

exports.QueryOne = async (collection, filter, projection = {}) => {
    try {
        const document = await collection.findOne(filter, { projection });

        if (!document) {
            return null;
        }

        return document;
    } catch (error) {
        throw new Error(error.message);
    }
}; 

exports.queryMany = async (collection, filter, options = {}) => {
    const { projection = {}, sort = {}, limit = 0, skip = 0 } = options;

    try {
        const cursor = collection
            .find(filter, { projection })
            .sort(sort)
            .limit(limit)
            .skip(skip);

        const documents = await cursor.toArray();

        if (documents.length === 0) {
            return null;
        }

        return documents;
    } catch (error) {
        throw new Error(error.message);
    }
};