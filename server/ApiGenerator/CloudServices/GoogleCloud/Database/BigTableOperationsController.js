var BigTableHelper = require('./BigTableOperationsHelper');

exports.createInstance = async (req, res) => {
    try {
        var instanceResponse = await BigTableHelper.createInstance(req.body.options, req.body.clusters, req.body.instanceId);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateInstance = async (req, res) => {
    try {
        var instanceResponse = await BigTableHelper.updateInstance(req.body.instanceId, req.body.options);

        return res.status(200).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteInstance = async (req, res) => {
    try {
        await BigTableHelper.deleteInstance(req.body.instanceId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getInstance = async (req, res) => {
    try {
        var instanceInfo = await BigTableHelper.getInstance(req.body.instanceId);

        return res.status(200).send({"instanceInfo": instanceInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};