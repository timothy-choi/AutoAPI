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
}
