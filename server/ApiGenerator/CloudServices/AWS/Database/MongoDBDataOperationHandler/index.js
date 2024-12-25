const MongoDBHelper = require('./mongodb_helper_functions');

exports.handler = async (event) => {
    try {
        var requestData = JSON.parse(event.body);

        var secret = await MongoDBHelper.getCredentials(requestData.secretName, requestData.region);

        requestData.connectionInfo.username = secret.username;

        requestData.connectionInfo.password = secret.password;

        const client = await MongoDBHelper.getMongoDBClient(requestData.connectionInfo, requestData.connectionOptions);

        const db = client.db(requestData.databaseName);

        const collection = db.collection(requestData.collectionName);

        let result = null;

        let operation = requestData.operation;

        switch (operation) {
            case 'insertOne':
                await MongoDBHelper.InsertOne(collection, requestData.document);
                break;
            case 'updateOne':
                result = await MongoDBHelper.updateOne(collection, requestData.query, requestData.updateDoc);
                break;
            case 'deleteOne':
                result = await MongoDBHelper.deleteOne(collection, requestData.query);
                break;
            case 'insertMany':
                result = await MongoDBHelper.InsertMany(collection, requestData.documents);
                break;
            case 'updateMany':
                result = await MongoDBHelper.updateMany(collection, requestData.query, request.updateDoc);
                break;
            case 'deleteMany':
                result = await MongoDBHelper.deleteMany(collection, requestData.query);
                break;
            default:
                throw new Error(`Unsupported operation: ${operation}`);
        };

        return {
            statusCode: 200,
            body: JSON.stringify({ success: true, result }),
        };
    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify({ success: false, message: error.message }),
        };
    } finally {
        if (client) {
            await client.close();
        }
    }
};