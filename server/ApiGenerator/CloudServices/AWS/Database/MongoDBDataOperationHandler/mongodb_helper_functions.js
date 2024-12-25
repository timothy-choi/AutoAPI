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

const confirmInsertOne = async (collection, query, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc) {
            return doc;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Operation confirmation timed out');
};

exports.InsertOne = async (collection, document) => {
    try {
        var operation = async () => {
            var result = await collection.insertOne(document);

            return result;
        };

        const resultVal = await retryOperation(operation);

        await confirmInsertOne(collection, { _id: resultVal.insertedId });
    } catch (error) {
        throw new Error(error.message);
    }
};

const confirmInsertMany = async (collection, query, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const docs = await collection.find(query).toArray();
        if (docs.length > 0) {
            return docs;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Operation confirmation timed out');
};

exports.InsertMany = async (collection, documents) => {
    try {
        const operation = async () => {
            const result = await collection.insertMany(documents);

            return result;  
        };

        const resultVal = await retryOperation(operation);

        const insertedDocs = await confirmInsertMany(collection, { _id: { $in: resultVal.insertedIds } });

        return insertedDocs;
    } catch (error) {
        throw new Error(error.message); 
    }
};

const confirmUpdateOne = async (collection, query, updatedDocument, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc && JSON.stringify(doc) !== JSON.stringify(updatedDocument)) {
            return doc;  
        }
        await new Promise(resolve => setTimeout(resolve, 500));  
    }
    throw new Error('Operation confirmation timed out');
};

exports.updateOne = async (collection, query, updateDoc) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(query, { $set: updateDoc });

            return result;  
        };

        const resultVal = await retryOperation(operation);

        const confirmedDocument = await confirmUpdateOne(collection, query, resultVal);

        return confirmedDocument;
    } catch (error) {
        throw new Error(error.message);  
    }
};

const confirmUpdateMany = async (collection, query, updateDoc, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const docs = await collection.find(query).toArray();  
        const allUpdated = docs.every(doc => {
            return Object.keys(updateDoc).every(key => doc[key] === updateDoc[key]);
        });

        if (allUpdated) {
            return docs; 
        }
        await new Promise(resolve => setTimeout(resolve, 500));  
    }
    throw new Error('Operation confirmation timed out');
};

exports.updateMany = async (collection, query, updateDoc) => {
    try {
        const operation = async () => {
            const result = await collection.updateMany(query, { $set: updateDoc });
            return result; 
        };

        await retryOperation(operation);

        var confirmedUpdatedDocuments = await confirmUpdateMany(collection, query, updateDoc);

        return confirmedUpdatedDocuments;
    } catch (error) {
        throw new Error(error);  
    }
};