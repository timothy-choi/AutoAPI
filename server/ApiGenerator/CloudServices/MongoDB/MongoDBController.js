const MongoDBInstanceHelper = require('./MongoDBInstanceHelper');
const MongoDBServiceHelper = require('./MongoDBServiceAccount');

exports.CreateServiceAccount = async (req, res) => {
    try {
        var serviceAccountResponse = await MongoDBServiceHelper.createServiceAccount(req.body.mongoServiceAccountUri, req.body.name, req.body.apiKey);

        return res.status(201).send({"serviceAccountResponse": serviceAccountResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMongoDBProject = async (req, res) => {
    try {
        var projectResponse = await MongoDBInstanceHelper.createProject(req.body.apiKey, req.body.projectUri, req.body.name, req.body.organizationId);

        return res.status(201).send({"projectResponse": projectResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteMongoDBProject = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteProject(req.body.projectUri, req.body.apiKey);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.createCluster(req.body.apiKey, req.body.clusterUri, req.body.clusterInfo);

        return res.status(201).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.updateCluster(req.body.apiKey, req.body.clusterUri, req.body.clusterConfig);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteMongoDBCluster = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteCluster(req.body.apiKey, req.body.clusterUri);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};