const CosmosDBHelper = require('./CosmosDBHelper');

exports.CreateOrUpdateCosmosDBAccount = async (req, res) => {
    try {
        var cosmosResponse = await CosmosDBHelper.CreateOrUpdateCosmosDBAccount(req.body.resourceGroupName, req.body.accountName, req.body.cosmosDbParams, req.body.subscriptionId);

        return res.status(201).send({"cosmosResponse": cosmosResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteCosmosDBAccount = async (req, res) => {
    try {
        await CosmosDBHelper.DeleteCosmosDBAccount(req.body.resourceGroupName, req.body.accountName, req.body.subscriptionId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetCosmosDBAccountList = async (req, res) => {
    try {
        var cosmosResponse = await CosmosDBHelper.ListCosmosDBAccounts(req.subscriptionId);

        return res.status(200).send({"cosmosAccounts": cosmosResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateCosmosDBInstance = async (req, res) => {
    try {
        var databaseResponse = await CosmosDBHelper.CreateCosmosDBInstance(req.body.databaseName, req.body.accountEndpoint, req.bodu.accountKey);

        return res.status(201).send({"databaseResponse": databaseResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteCosmosDBInstance = async (req, res) => {
    try {
        await CosmosDBHelper.DeleteCosmosDBInstance(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetCosmosDBInstanceList = async (req, res) => {
    try {
        var cosmosResponse = await CosmosDBHelper.ListDatabases(req.body.accountEndpoint, req.body.accountKey);

        return res.status(200).send({"cosmosDatabases": cosmosResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateDBContainer = async (req, res) => {
    try {
        var containerInfo = await CosmosDBHelper.CreateCosmosDBContainer(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName, req.body.containerInfo);

        return res.status(201).send({"containerInfo": containerInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteDBContainer = async (req, res) => {
    try {
        await CosmosDBHelper.DeleteCosmosDBContainer(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName, req.body.containerName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.ListDBContainers = async (req, res) => {
    try {
        var containers = CosmosDBHelper.ListContainers(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName);

        return res.status(200).send({"containers": containers});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateDatabaseThroughput = async (req, res) => {
    try {
        var databaseThroughputResponse = await CosmosDBHelper.UpdateDatabaseThroughput(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName, req.body.throughput);

        return res.status(200).send({"databaseThroughputResponse": databaseThroughputResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateContainerThroughput = async (req, res) => {
    try {
        var databaseContainerResponse = await CosmosDBHelper.UpdateContainerThroughput(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName, req.body.containerName, req.body.throughput);

        return res.status(200).send({"databaseContainerResponse": databaseContainerResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateOtherContainerSettings = async (req, res) => {
    try {
       var containerSettingsResponse = await CosmosDBHelper.UpdateOtherContainerSettings(req.body.accountEndpoint, req.body.accountKey, req.body.databaseName, req.body.containerName, req.body.containerInfo);
       
       return res.status(200).send({"containerSettingsResponse": containerSettingsResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.InsertDocument = async (req, res) => {
    try {
        var insertResponse = await CosmosDBHelper.InsertDocument(req.body.databaseId, req.body.containerId, req.body.documentInfo);

        return res.status(201).send({"insertResponse": insertResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.QueryDocument = async (req, res) => {
    try {
        var queryResponse = await CosmosDBHelper.QueryDocuments(req.body.databaseId, req.body.containerId, req.body.queryValue, req.body.params);

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateDocument = async (req, res) => {
    try {
        var updateResponse = await CosmosDBHelper.UpdateDocument(req.body.databaseId, req.body.containerId, req.body.documentId, req.body.partitionKey, req.body.attribute, req.body.newValue);

        return res.status(200).send({"updateResponse": updateResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.DeleteDocument = async (req, res) => {
    try {
        await CosmosDBHelper.DeleteDocument(req.body.databaseId, req.body.containerId, req.body.documentId, req.body.partitionKey);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.AggregrateQuery = async (req, res) => {
    try {
        var queryResponse = await CosmosDBHelper.AggregateQuery(req.body.databaseId, req.body.containerId, req.body.aggregateQuery);

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.BulkUpsertDocuments = async (req, res) => {
    try {
        var modifyResponse = await CosmosDBHelper.BulkUpsertDocuments(req.body.databaseId, req.body.containerId, req.body.documents);

        return res.status(200).send({"modifyResponse": modifyResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.BulkDeleteDocuments = async (req, res) => {
    try {
        await CosmosDBHelper.BulkDeleteDocuments(req.body.databaseName, req.body.containerName, req.body.query);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}