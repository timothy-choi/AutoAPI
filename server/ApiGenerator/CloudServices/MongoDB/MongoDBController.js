const MongoDBInstanceHelper = require('./MongoDBInstanceHelper');
const MongoDBServiceHelper = require('./MongoDBServiceAccount');

exports.CreateServiceAccount = async (req, res) => {
    try {
        var serviceAccountResponse = await MongoDBServiceHelper.createServiceAccount(req.body.mongoServiceAccountUri, req.body.name, req.body.apiKey);

        return res.status(201).send({"serviceAccountResponse": serviceAccountResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}