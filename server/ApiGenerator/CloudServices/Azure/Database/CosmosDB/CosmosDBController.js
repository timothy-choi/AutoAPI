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
