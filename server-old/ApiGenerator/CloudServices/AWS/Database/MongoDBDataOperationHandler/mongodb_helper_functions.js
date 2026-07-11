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

exports.aggregate = async (collection, pipeline) => {
    try {
        const operation = async () => {
            const result = await collection.aggregate(pipeline).toArray();
            return result;
        };

        const result = await retryOperation(operation);

        return result; 
    } catch (error) {
        throw new Error(`Error during aggregation operation: ${error.message}`);
    }
};

exports.countDocuments = async (collection, query) => {
    try {
        const operation = async () => {
            const count = await collection.countDocuments(query);
            return count;
        };

        const count = await retryOperation(operation);
        return count;
    } catch (error) {
        throw new Error(`Error during count operation: ${error.message}`);
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

const confirmUpsert = async (collection, query, updateDoc, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc) {
            const matchesUpdate = Object.keys(updateDoc).every(key => {
                return doc[key] === updateDoc[key];
            });
            if (matchesUpdate) {
                return doc; 
            }
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Operation confirmation timed out');
};

exports.upsert = async (collection, query, updateDoc) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(
                query,
                { $set: updateDoc },
                { upsert: true }
            );
            return result;
        };

        const result = await retryOperation(operation);

        const confirmedDoc = await confirmUpsert(collection, query, updateDoc);

        return confirmedDoc; 
    } catch (error) {
        throw new Error(`Error during upsert operation: ${error.message}`);
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

const confirmDeleteOne = async (collection, query, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);  
        if (!doc) {
            return null;  
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Operation confirmation timed out');
};

exports.deleteOne = async (collection, query) => {
    try {
        const operation = async () => {
            const result = await collection.deleteOne(query);
            return result;  
        };

        const resultVal = await retryOperation(operation);

        await confirmDeleteOne(collection, query);

        return resultVal;  
    } catch (error) {
        throw new Error(error.message);  
    }
};

const confirmDeleteMany = async (collection, query, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const docs = await collection.find(query).toArray();  
        if (docs.length === 0) {
            return [];  
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Operation confirmation timed out');
};

exports.deleteMany = async (collection, query) => {
    try {
        const operation = async () => {
            const result = await collection.deleteMany(query);
            return result;  
        };

        const resultVal = await retryOperation(operation);

        await confirmDeleteMany(collection, query);

        return resultVal;  
    } catch (error) {
        throw new Error(error.message);  
    }
};

const confirmBulkWrite = async (collection, operations, timeout = 5000) => {
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
        let allConfirmed = true;

        for (const op of operations) {
            if (op.insertOne) {
                const doc = await collection.findOne(op.insertOne.document);
                if (!doc) {
                    allConfirmed = false;
                    break;
                }
            } else if (op.updateOne || op.updateMany) {
                const query = op.updateOne ? op.updateOne.filter : op.updateMany.filter;
                const updateDoc = op.updateOne ? op.updateOne.update.$set : op.updateMany.update.$set;
                const docs = await collection.find(query).toArray();
                const allUpdated = docs.every(doc =>
                    Object.keys(updateDoc).every(key => doc[key] === updateDoc[key])
                );
                if (!allUpdated) {
                    allConfirmed = false;
                    break;
                }
            } else if (op.deleteOne || op.deleteMany) {
                const query = op.deleteOne ? op.deleteOne.filter : op.deleteMany.filter;
                const docCount = await collection.countDocuments(query);
                if (docCount > 0) {
                    allConfirmed = false;
                    break;
                }
            }
        }

        if (allConfirmed) {
            return true; 
        }

        await new Promise(resolve => setTimeout(resolve, 500)); 
    }

    throw new Error('Operation confirmation timed out');
};

exports.bulkWrite = async (collection, operations) => {
    try {
        const operation = async () => {
            const result = await collection.bulkWrite(operations, { ordered: true });
            return result;
        };

        const result = await retryOperation(operation);

        await confirmBulkWrite(collection, operations);

        return result; 
    } catch (error) {
        throw new Error(`Error during bulk write operation: ${error.message}`);
    }
};

const confirmIncrement = async (collection, query, field, incrementBy, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc && doc[field] >= incrementBy) {
            return doc;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Increment confirmation timed out');
};

exports.incrementField = async (collection, query, field, incrementBy) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(query, { $inc: { [field]: incrementBy } });
            return result;
        };

        await retryOperation(operation);

        const confirmedDoc = await confirmIncrement(collection, query, field, incrementBy);

        return confirmedDoc;
    } catch (error) {
        throw new Error(`Error during increment operation: ${error.message}`);
    }
};

const confirmSetField = async (collection, query, field, value, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc && doc[field] === value) {
            return doc;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Set field confirmation timed out');
};

exports.setField = async (collection, query, field, value) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(query, { $set: { [field]: value } });
            return result;
        };

        await retryOperation(operation);

        const confirmedDoc = await confirmSetField(collection, query, field, value);
        return confirmedDoc;
    } catch (error) {
        throw new Error(`Error during set field operation: ${error.message}`);
    }
};

const confirmAddToArray = async (collection, query, field, value, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc && doc[field] && doc[field].includes(value)) {
            return doc;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Add to array confirmation timed out');
};

exports.addToArray = async (collection, query, field, value) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(query, { $addToSet: { [field]: value } });
            return result;
        };

        const result = await retryOperation(operation);
        const confirmedDoc = await confirmAddToArray(collection, query, field, value);
        return confirmedDoc;
    } catch (error) {
        throw new Error(`Error during add to array operation: ${error.message}`);
    }
};

const confirmRemoveFromArray = async (collection, query, field, value, timeout = 5000) => {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const doc = await collection.findOne(query);
        if (doc && doc[field] && !doc[field].includes(value)) {
            return doc;
        }
        await new Promise(resolve => setTimeout(resolve, 500)); 
    }
    throw new Error('Remove from array confirmation timed out');
};

exports.removeFromArray = async (collection, query, field, value) => {
    try {
        const operation = async () => {
            const result = await collection.updateOne(query, { $pull: { [field]: value } });
            return result;
        };

        const result = await retryOperation(operation);
        const confirmedDoc = await confirmRemoveFromArray(collection, query, field, value);
        return confirmedDoc;
    } catch (error) {
        throw new Error(`Error during remove from array operation: ${error.message}`);
    }
};

const confirmUpdateArrayElement = async (collection, query, arrayField, expectedValue, timeout = 5000) => {
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
        const document = await collection.findOne(query);

        if (document && document[arrayField].includes(expectedValue)) {
            return true;
        }

        await new Promise(resolve => setTimeout(resolve, 500)); 
    }

    throw new Error('Operation confirmation timed out: Array element not updated');
};

exports.updateArrayElement = async (collection, query, arrayField, oldValue, newValue) => {
    try {
        const operation = async () => {
            return await collection.updateOne(
                { ...query, [arrayField]: oldValue }, 
                { $set: { [`${arrayField}.$`]: newValue } } 
            );
        };

        const result = await retryOperation(operation);

        await confirmUpdateArrayElement(collection, query, arrayField, newValue);

        return result;
    } catch (error) {
        console.error('Error during update array element operation:', error);
        throw error;
    }
};