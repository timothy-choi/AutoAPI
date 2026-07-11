const MongoDBHelper = require('./mongodb_helper_functions');

exports.ExecuteMongoDBOperations = async (req, res) => {
    try {
        var requestData = req.body;

        var secret = await MongoDBHelper.getCredentials(requestData.secretName, requestData.region);

        requestData.connectionInfo.username = secret.username;

        requestData.connectionInfo.password = secret.password;

        const client = await MongoDBHelper.getMongoDBClient(requestData.connectionInfo, requestData.connectionOptions);

        const db = client.db(requestData.databaseName);

        const collection = db.collection(requestData.collectionName);

        let result = null;

        let operation = requestData.operation;

        switch (operation) {
            case 'queryOne':
                result = await MongoDBHelper.QueryOne(collection, requestData.filter, requestData.projection);
                break;
            case 'queryMany':
                result = await MongoDBHelper.queryMany(collection, requestData.filter, requestData.options);
                break;
            case 'aggregate':
                result = await MongoDBHelper.aggregate(collection, requestData.pipeline);
                break;
            case 'countDocuments':
                result = await MongoDBHelper.countDocuments(collection, requestData.query);
                break;
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
            case 'upsert':
                result = await MongoDBHelper.upsert(collection, requestData.query, requestData.updateDoc);
                break;
            case 'bulkWrite':
                result = await MongoDBHelper.bulkWrite(collection, requestData.operations);
                break;
            case 'incrementField':
                result = await MongoDBHelper.incrementField(collection, requestData.query, requestData.field, requestData.incrementBy);
                break;
            case 'setField':
                result = await MongoDBHelper.setField(collection, requestData.query, requestData.field, requestData.value);
                break;
            case 'addToArray':
                result = await MongoDBHelper.addToArray(collection, requestData.query, requestData.field, requestData.value);
                break;
            case 'removeFromArray':
                result = await MongoDBHelper.removeFromArray(collection, requestData.query, requestData.field, requestData.value);
                break;
            case 'updateArrayElement':
                result = await MongoDBHelper.updateArrayElement(collection, requestData.query, requestData.arrayField, requestData.oldValue, requestData.newValue);
                break;
            default:
                throw new Error(`Unsupported operation: ${operation}`);
        };

        return res.status(200).send({"success": true, "data": result});
    } catch (error) {
        return res.status(500).send({"success": false, "message": error.message});
    } finally {
        if (client) {
            await client.close();
        }
    }
};